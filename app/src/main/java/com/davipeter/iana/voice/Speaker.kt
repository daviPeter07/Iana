package com.davipeter.iana.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/** Voz da IANA: TextToSpeech do Android em português do Brasil. */
class Speaker(context: Context) {

    private val initStatus = CompletableDeferred<Int>()
    private val tts = TextToSpeech(context.applicationContext) { status -> initStatus.complete(status) }
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

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.shutdown()
    }
}
