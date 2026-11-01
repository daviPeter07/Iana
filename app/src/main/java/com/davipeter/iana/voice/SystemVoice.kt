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

/** Um motor de voz instalado no celular (Google, sherpa-onnx "dii", Supertonic...). */
data class VoiceEngine(val packageName: String, val label: String) {
    /** A voz embutida ("iana:dii") e os motores do sherpa-onnx são open source e offline. */
    val isOpenSource: Boolean get() = packageName.startsWith("iana:") || packageName.contains("sherpa", ignoreCase = true)

    companion object {
        fun installed(context: Context): List<VoiceEngine> {
            val pm = context.packageManager
            return pm.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
                .map { it.serviceInfo }
                .distinctBy { it.packageName }
                .map { VoiceEngine(it.packageName, it.loadLabel(pm).toString()) }
        }
    }
}

/**
 * Fala usando um motor de voz do Android ([engine]; nulo = o padrão do celular), em pt-BR.
 * Roda no celular, então funciona sem internet quando o motor é offline (caso do sherpa-onnx).
 */
class SystemVoice(context: Context, engine: String?) {

    private val initStatus = CompletableDeferred<Int>()
    private val tts = TextToSpeech(context.applicationContext, { status -> initStatus.complete(status) }, engine)
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
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Alguns motores (ex.: Supertonic) registram só "pt", sem o país.
                val generic = tts.setLanguage(Locale.forLanguageTag("pt"))
                if (generic == TextToSpeech.LANG_MISSING_DATA || generic == TextToSpeech.LANG_NOT_SUPPORTED) return false
            }
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
        voices.filter { it.locale.language == "pt" && !it.isNetworkConnectionRequired }
            .minByOrNull { it.latency }
            ?.let { tts.voice = it }
    }

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.shutdown()
    }
}
