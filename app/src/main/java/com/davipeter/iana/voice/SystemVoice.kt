package com.davipeter.iana.voice

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Voz do próprio celular (offline): o TextToSpeech do Android em português do Brasil.
 *
 * A voz da IANA é a "dii" (feminina, pt-BR) do sherpa-onnx, instalada como motor de voz do
 * Android. Ela roda no celular, então é a mesma com ou sem internet. Sem ela, cai no motor padrão.
 */
class SystemVoice(context: Context) {

    private val initStatus = CompletableDeferred<Int>()
    private val enginePackage = preferredEngine(context)
    private val tts = TextToSpeech(context.applicationContext, { status -> initStatus.complete(status) }, enginePackage)

    /** True se a voz da IANA (motor sherpa-onnx) está instalada. */
    val hasIanaVoice: Boolean = enginePackage != null

    /** Nome do motor em uso (ex.: "Speech Services by Google", "sherpa-onnx TTS Engine"), para mostrar na tela. */
    val engineName: String? = runCatching {
        val pm = context.packageManager
        val pkg = enginePackage ?: tts.defaultEngine
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()
    private val pending = ConcurrentHashMap<String, () -> Unit>()
    private var configured = false

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) = Unit
        override fun onDone(utteranceId: String) = finish(utteranceId)
        override fun onStop(utteranceId: String, interrupted: Boolean) = finish(utteranceId)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) = finish(utteranceId)
        override fun onError(utteranceId: String, errorCode: Int) = finish(utteranceId)
    }

    private fun finish(id: String) {
        pending.remove(id)?.invoke()
    }

    /** Fala e só retorna quando terminar (ou for interrompida). Retorna false se não houver voz pt-BR. */
    suspend fun speak(text: String): Boolean {
        if (initStatus.await() != TextToSpeech.SUCCESS) return false
        if (!configured) {
            val lang = tts.setLanguage(Locale.forLanguageTag("pt-BR"))
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) return false
            tts.setOnUtteranceProgressListener(progress)
            pickOfflineVoice()
            configured = true
        }
        val id = UUID.randomUUID().toString()
        suspendCancellableCoroutine { cont ->
            pending[id] = { if (cont.isActive) cont.resume(Unit) }
            cont.invokeOnCancellation {
                pending.remove(id)
                tts.stop()
            }
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR) finish(id)
        }
        return true
    }

    /** No motor do Google, prefere uma voz pt-BR que não dependa de internet. */
    private fun pickOfflineVoice() {
        val voices = runCatching { tts.voices }.getOrNull() ?: return
        voices.filter { it.locale.toLanguageTag().equals("pt-BR", ignoreCase = true) && !it.isNetworkConnectionRequired }
            .minByOrNull { it.latency }
            ?.let { tts.voice = it }
    }

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.shutdown()
    }

    private companion object {
        /** Pacote de um motor sherpa-onnx instalado, se houver. Nulo = motor padrão do celular. */
        fun preferredEngine(context: Context): String? =
            context.packageManager.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
                .map { it.serviceInfo.packageName }
                .firstOrNull { it.contains("sherpa", ignoreCase = true) }
    }
}
