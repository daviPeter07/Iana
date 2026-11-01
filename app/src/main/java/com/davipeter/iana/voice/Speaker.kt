package com.davipeter.iana.voice

import android.content.Context

/**
 * A voz da IANA. É sempre a mesma, online ou offline, porque o motor roda no próprio celular.
 *
 * Qual motor usar: o que você escolheu no app; senão o Supertonic, se estiver instalado;
 * senão a "dii" (sherpa-onnx); senão a voz padrão do Android.
 */
class Speaker(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("voz", Context.MODE_PRIVATE)

    /** Motores de voz instalados no celular. */
    val engines: List<VoiceEngine> get() = VoiceEngine.installed(appContext)

    /** Motor em uso (nulo = o padrão do celular). */
    var current: VoiceEngine? = pickEngine()
        private set

    private var voice = SystemVoice(appContext, current?.packageName)

    /** False = nenhuma voz open source instalada (está usando a voz padrão do celular). */
    val hasIanaVoice: Boolean get() = current?.isOpenSource == true

    /** Troca o motor de voz e lembra da escolha. */
    fun choose(engine: VoiceEngine) {
        prefs.edit().putString(KEY_ENGINE, engine.packageName).apply()
        voice.shutdown()
        current = engine
        voice = SystemVoice(appContext, engine.packageName)
    }

    /** Fala e retorna quando terminar. False = não há voz disponível. */
    suspend fun speak(text: String): Boolean = voice.speak(text)

    fun stop() = voice.stop()

    fun shutdown() = voice.shutdown()

    private fun pickEngine(): VoiceEngine? {
        val installed = engines
        val saved = prefs.getString(KEY_ENGINE, null)
        return installed.firstOrNull { it.packageName == saved }
            ?: installed.firstOrNull { it.isOpenSource && it.label.contains("supertonic", ignoreCase = true) }
            ?: installed.firstOrNull { it.isOpenSource } // nulo = a voz padrão do celular
    }

    companion object {
        private const val KEY_ENGINE = "motor"

        /** Trecho do nome do APK certo: celular comum (arm64-v8a) + voz "dii" em pt-BR. */
        const val VOICE_APK_HINT = "arm64-v8a-pt-tts-engine-vits-piper-pt_BR-dii-high"

        /**
         * Página com os APKs do motor de voz. O final "#:~:text=" faz navegadores baseados no Chrome
         * (Chrome, Brave, Edge) rolarem até a voz certa e destacá-la. O "-" precisa ir como %2D.
         */
        val VOICE_DOWNLOAD_URL =
            "https://k2-fsa.github.io/sherpa/onnx/tts/apk-engine.html#:~:text=" + VOICE_APK_HINT.replace("-", "%2D")
    }
}
