package com.davipeter.iana.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * Voz gerada pelo VoiceStudio (https://voicestudio.sh) rodando no seu computador.
 * Usa a API compatível com OpenAI: POST {baseUrl}/v1/audio/speech, que devolve um MP3.
 *
 * O celular precisa estar na mesma rede Wi-Fi do computador.
 */
class VoiceStudioVoice(
    private val context: Context,
    private val baseUrl: String,
    private val model: String,
    private val voice: String,
) {
    private var player: MediaPlayer? = null

    /** Gera e toca o áudio. Lança [IOException] se o servidor não responder. */
    suspend fun speak(text: String) {
        val file = synthesize(text)
        play(file)
    }

    private suspend fun synthesize(text: String): File = withContext(Dispatchers.IO) {
        val conn = URL(baseUrl.trimEnd('/') + "/v1/audio/speech").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 2_500
            conn.readTimeout = 30_000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer local") // o VoiceStudio não confere a chave
            val body = JSONObject()
                .put("model", model)
                .put("voice", voice)
                .put("input", text)
                .put("response_format", "mp3")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) throw IOException("VoiceStudio respondeu HTTP ${conn.responseCode}")
            val file = File(context.cacheDir, "fala.mp3")
            conn.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
            file
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun play(file: File) = suspendCancellableCoroutine { cont ->
        val mp = MediaPlayer()
        player = mp
        fun done() {
            mp.release()
            if (player === mp) player = null
            if (cont.isActive) cont.resume(Unit)
        }
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        mp.setOnCompletionListener { done() }
        mp.setOnErrorListener { _, _, _ -> done(); true }
        cont.invokeOnCancellation {
            runCatching { mp.stop() }
            mp.release()
            if (player === mp) player = null
        }
        try {
            mp.setDataSource(file.path)
            mp.prepare()
            mp.start()
        } catch (e: IOException) {
            done()
        }
    }

    fun stop() {
        player?.let { runCatching { it.stop() } }
    }
}
