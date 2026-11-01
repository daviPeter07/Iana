package com.davipeter.iana.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * A voz da IANA embutida no app: a voz feminina "dii" (pt-BR) rodando no motor sherpa-onnx.
 * Não depende de outro app nem de internet; o modelo (~67 MB) é baixado uma vez.
 *
 * Licença do modelo: CC BY-NC-SA 4.0 (livre para uso pessoal, não comercial).
 *
 * Uma instância só para o app inteiro (fica no AppContainer): o modelo ocupa memória e não
 * pode gerar duas falas ao mesmo tempo.
 */
class EmbeddedVoice(context: Context) {

    private val dir = File(context.applicationContext.filesDir, DIR)
    private val cacheDir = context.applicationContext.cacheDir
    private val mutex = Mutex()
    private var tts: OfflineTts? = null

    @Volatile private var track: AudioTrack? = null
    @Volatile private var stopped = false

    val isReady: Boolean get() = File(dir, READY_MARKER).exists()

    /** Baixa e instala o modelo. [onProgress] recebe 0..100 durante o download. */
    suspend fun download(onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        if (isReady) return@withContext
        dir.deleteRecursively()
        dir.mkdirs()
        val archive = File(cacheDir, "voz-dii.tar.bz2")
        try {
            fetch(archive, onProgress)
            extract(archive)
            File(dir, READY_MARKER).createNewFile()
        } catch (e: IOException) {
            dir.deleteRecursively()
            throw e
        } finally {
            archive.delete()
        }
    }

    /** Fala e só retorna quando o áudio terminar (ou for interrompido). False = modelo não instalado. */
    suspend fun speak(text: String): Boolean {
        if (!isReady) return false
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val engine = tts ?: load().also { tts = it }
                val rate = engine.sampleRate()
                val out = newTrack(rate)
                track = out
                stopped = false
                val job = coroutineContext
                var written = 0
                try {
                    out.play()
                    // Toca enquanto gera: a fala começa antes da frase inteira estar pronta.
                    engine.generateWithCallback(text, 0, SPEED) { samples ->
                        if (stopped || !job.isActive) {
                            0
                        } else {
                            out.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                            written += samples.size
                            1
                        }
                    }
                    while (!stopped && out.playbackHeadPosition < written) delay(20)
                } finally {
                    track = null
                    runCatching { out.stop() }
                    out.release()
                }
                true
            }
        }
    }

    fun stop() {
        stopped = true
        track?.let { runCatching { it.pause(); it.flush() } }
    }

    private fun load(): OfflineTts {
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = File(dir, "pt_BR-dii-high.onnx").path,
                    tokens = File(dir, "tokens.txt").path,
                    dataDir = File(dir, "espeak-ng-data").path,
                ),
                numThreads = 2,
            ),
        )
        return OfflineTts(null, config) // null = ler os arquivos do disco, não dos assets
    }

    private fun newTrack(sampleRate: Int): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBuffer, sampleRate * 4 / 2)) // ~0,5 s de áudio
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun fetch(target: File, onProgress: (Int) -> Unit) {
        val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            if (conn.responseCode !in 200..299) throw IOException("Download da voz falhou: HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            var done = 0L
            var last = -1
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
                            if (percent != last) {
                                last = percent
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

    /** Extrai tirando a pasta raiz ("vits-piper-pt_BR-dii-high/tokens.txt" vira "tokens.txt"). */
    private fun extract(archive: File) {
        val root = dir.canonicalPath + File.separator
        TarArchiveInputStream(BZip2CompressorInputStream(archive.inputStream().buffered())).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                val relative = entry.name.substringAfter('/', "")
                if (relative.isEmpty()) continue
                val out = File(dir, relative)
                // Protege contra arquivos maliciosos que tentam escrever fora da pasta ("../").
                if (!out.canonicalPath.startsWith(root)) throw IOException("Entrada inválida: ${entry.name}")
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { tar.copyTo(it) }
                }
            }
        }
    }

    companion object {
        private const val DIR = "voz-dii"
        private const val READY_MARKER = ".pronto"
        private const val SPEED = 1.0f
        const val DOWNLOAD_MB = 67
        private const val MODEL_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-pt_BR-dii-high.tar.bz2"
    }
}
