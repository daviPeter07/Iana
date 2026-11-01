package com.davipeter.iana.voice

import android.content.Context
import com.davipeter.iana.IanaApp

/**
 * A voz da IANA. Por padrão, a voz "dii" embutida no app (sherpa-onnx): a mesma voz com ou sem
 * internet. Também dá pra escolher outro motor de voz instalado no celular.
 * Enquanto a voz embutida não foi baixada, usa a voz padrão do celular.
 */
class Speaker(context: Context) {

    private val appContext = context.applicationContext
    private val embedded = (appContext as IanaApp).container.embeddedVoice
    private val prefs = appContext.getSharedPreferences("voz", Context.MODE_PRIVATE)
    private var system: SystemVoice? = null

    /** Vozes disponíveis: a da IANA primeiro, depois os motores instalados no celular. */
    val engines: List<VoiceEngine> get() = listOf(IANA) + VoiceEngine.installed(appContext)

    var current: VoiceEngine = pick()
        private set

    /** True = a voz da IANA já foi baixada. */
    val hasIanaVoice: Boolean get() = embedded.isReady

    /** Baixa a voz da IANA (uma vez só). [onProgress] recebe 0..100. */
    suspend fun downloadIanaVoice(onProgress: (Int) -> Unit) = embedded.download(onProgress)

    /** Troca a voz e lembra da escolha. */
    fun choose(engine: VoiceEngine) {
        prefs.edit().putString(KEY_ENGINE, engine.packageName).apply()
        system?.shutdown()
        system = null
        current = engine
    }

    /** Fala e retorna quando terminar. False = não há voz disponível. */
    suspend fun speak(text: String): Boolean {
        if (current == IANA && embedded.isReady) return embedded.speak(text)
        return systemVoice().speak(text)
    }

    fun stop() {
        embedded.stop()
        system?.stop()
    }

    /** A voz embutida é compartilhada pelo app todo; só o motor do sistema é liberado aqui. */
    fun shutdown() {
        system?.shutdown()
        system = null
    }

    private fun systemVoice(): SystemVoice =
        system ?: SystemVoice(appContext, current.packageName.takeIf { current != IANA }).also { system = it }

    private fun pick(): VoiceEngine {
        val saved = prefs.getString(KEY_ENGINE, null) ?: return IANA
        return engines.firstOrNull { it.packageName == saved } ?: IANA
    }

    companion object {
        private const val KEY_ENGINE = "motor"

        /** A voz embutida no app. */
        val IANA = VoiceEngine(packageName = "iana:dii", label = "IANA (voz dii, embutida)")
    }
}
