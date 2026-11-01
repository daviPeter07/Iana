package com.davipeter.iana.wakeword

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Modelo de português do Vosk (Apache 2.0, ~31 MB). É baixado uma vez, na primeira vez que a
 * escuta da palavra "IANA" é ligada, e fica guardado no app. Depois disso tudo roda offline.
 */
object VoskModel {
    private const val URL_ZIP = "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip"
    private const val DIR = "vosk-pt"
    private const val READY_MARKER = ".pronto"

    fun isReady(context: Context): Boolean = File(File(context.filesDir, DIR), READY_MARKER).exists()

    /** Garante o modelo no aparelho e devolve a pasta dele. [onProgress] recebe 0..100. */
    suspend fun ensure(context: Context, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, DIR)
        if (File(dir, READY_MARKER).exists()) return@withContext dir

        dir.deleteRecursively()
        dir.mkdirs()
        val zip = File(context.cacheDir, "vosk-pt.zip")
        try {
            download(zip, onProgress)
            unzip(zip, dir)
            File(dir, READY_MARKER).createNewFile()
        } catch (e: IOException) {
            dir.deleteRecursively()
            throw e
        } finally {
            zip.delete()
        }
        dir
    }

    private fun download(target: File, onProgress: (Int) -> Unit) {
        val conn = URL(URL_ZIP).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            if (conn.responseCode !in 200..299) throw IOException("Download do modelo falhou: HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            var done = 0L
            var lastPercent = -1
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Extrai tirando a pasta raiz do zip ("vosk-model-small-pt-0.3/am/..." vira "am/..."). */
    private fun unzip(zip: File, dir: File) {
        val root = dir.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                val relative = entry.name.substringAfter('/', "")
                if (relative.isEmpty()) continue
                val out = File(dir, relative)
                // Protege contra zips maliciosos que tentam escrever fora da pasta ("../").
                if (!out.canonicalPath.startsWith(root)) throw IOException("Entrada inválida no zip: ${entry.name}")
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zin.copyTo(it) }
                }
            }
        }
    }
}
