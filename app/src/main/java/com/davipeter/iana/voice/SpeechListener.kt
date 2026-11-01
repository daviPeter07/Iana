package com.davipeter.iana.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SpeechException(val code: Int) : Exception("Erro de reconhecimento de voz: $code") {
    val friendlyMessage: String
        get() = when (code) {
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "Sem internet para reconhecer a voz. Baixe o pacote offline de português nas configurações de voz."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Preciso de permissão para usar o microfone."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "O reconhecimento de voz está ocupado. Tente de novo."
            SpeechRecognizer.ERROR_AUDIO -> "Não consegui acessar o microfone."
            else -> "Não consegui entender. Tente de novo."
        }
}

/**
 * Ouve uma frase usando o reconhecimento de voz do Android. Precisa ser chamado na main thread.
 */
class SpeechListener(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Ouve até a pessoa parar de falar. Retorna o texto reconhecido, ou "" se não ouviu nada.
     * [onPartial] recebe o texto parcial enquanto a pessoa fala.
     */
    suspend fun listen(onPartial: (String) -> Unit = {}): String = suspendCancellableCoroutine { cont ->
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)

        fun release() = main.post { recognizer.destroy() }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                release()
                if (cont.isActive) cont.resume(text)
            }

            override fun onPartialResults(partial: Bundle) {
                partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let(onPartial)
            }

            override fun onError(error: Int) {
                release()
                if (!cont.isActive) return
                // Silêncio ou nada reconhecido não é erro: só não ouvimos nada.
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    cont.resume("")
                } else {
                    cont.resumeWithException(SpeechException(error))
                }
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        cont.invokeOnCancellation {
            main.post {
                recognizer.cancel()
                recognizer.destroy()
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer.startListening(intent)
    }
}
