package com.davipeter.iana.voice

import android.content.Context

/**
 * A voz da IANA. Sempre a mesma voz, online ou offline: o motor sherpa-onnx com a voz "dii"
 * roda no próprio celular. Se ele não estiver instalado, usa a voz padrão do Android e a tela
 * mostra como instalar.
 */
class Speaker(context: Context) {

    private val system = SystemVoice(context)

    /** False = a voz da IANA ainda não foi instalada (está usando a voz padrão do celular). */
    val hasIanaVoice: Boolean get() = system.hasIanaVoice

    /** Fala e retorna quando terminar. False = não há voz disponível. */
    suspend fun speak(text: String): Boolean = system.speak(text)

    fun stop() = system.stop()

    fun shutdown() = system.shutdown()

    companion object {
        /** Página com os APKs do motor de voz (procure o "pt_BR-dii-high", arm64-v8a). */
        const val VOICE_DOWNLOAD_URL = "https://k2-fsa.github.io/sherpa/onnx/tts/apk-engine.html"
    }
}
