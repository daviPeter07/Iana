package com.davipeter.iana.voice

import android.content.Context
import android.os.SystemClock
import com.davipeter.iana.BuildConfig
import java.io.IOException

/**
 * A voz da IANA. Tenta o VoiceStudio no computador (se configurado) e, se ele não responder,
 * fala com a voz offline do celular. Depois de uma falha, fica 5 minutos sem tentar o servidor
 * para não atrasar as respostas.
 */
class Speaker(context: Context) {

    private val system = SystemVoice(context)
    private val studio = BuildConfig.VOICESTUDIO_URL.takeIf { it.isNotBlank() }?.let {
        VoiceStudioVoice(context.applicationContext, it, BuildConfig.VOICESTUDIO_MODEL, BuildConfig.VOICESTUDIO_VOICE)
    }
    private var studioDownUntil = 0L

    /** Fala e retorna quando terminar. False = não há voz disponível. */
    suspend fun speak(text: String): Boolean {
        if (studio != null && SystemClock.elapsedRealtime() >= studioDownUntil) {
            try {
                studio.speak(text)
                return true
            } catch (e: IOException) {
                studioDownUntil = SystemClock.elapsedRealtime() + STUDIO_RETRY_MS
            }
        }
        return system.speak(text)
    }

    /** "VoiceStudio" ou o nome do motor do celular. */
    val description: String
        get() = if (studio != null && SystemClock.elapsedRealtime() >= studioDownUntil) {
            "VoiceStudio"
        } else {
            system.engineName.orEmpty()
        }

    fun stop() {
        studio?.stop()
        system.stop()
    }

    fun shutdown() {
        studio?.stop()
        system.shutdown()
    }

    private companion object {
        const val STUDIO_RETRY_MS = 5 * 60 * 1000L
    }
}
