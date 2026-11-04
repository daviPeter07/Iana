package com.davipeter.iana.wakeword

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.davipeter.iana.IanaApp
import com.davipeter.iana.ai.Answer
import com.davipeter.iana.ai.Confirmation
import com.davipeter.iana.assistant.Reply
import kotlinx.coroutines.CancellationException
import com.davipeter.iana.R
import com.davipeter.iana.ui.MainActivity
import com.davipeter.iana.voice.SpeechListener
import com.davipeter.iana.voice.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.IOException

/**
 * Fica ouvindo a palavra "IANA" em segundo plano com o Vosk (open source, offline, sem conta).
 * Quando ouve: apita, escuta o pedido, executa e responde falando, sem abrir o app.
 *
 * O Vosk só procura algumas palavras (uma "gramática" pequena), o que deixa a escuta leve.
 * Roda como serviço em primeiro plano do tipo microfone, por isso aparece uma notificação fixa.
 */
class WakeWordService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var listener: SpeechListener
    private lateinit var speaker: Speaker
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var detector: SpeechService? = null
    private var conversation: Job? = null
    private var pausedByApp = false

    private val voskListener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) = inspect(hypothesis, "partial")
        override fun onResult(hypothesis: String?) = inspect(hypothesis, "text")
        override fun onFinalResult(hypothesis: String?) = Unit
        override fun onError(exception: Exception?) {
            _error.value = "Erro no microfone: ${exception?.message}"
        }
        override fun onTimeout() = Unit

        private fun inspect(hypothesis: String?, field: String) {
            val text = hypothesis?.let { runCatching { JSONObject(it).optString(field) }.getOrNull() }.orEmpty()
            if (WAKE.containsMatchIn(text)) onWakeWord()
        }
    }

    override fun onCreate() {
        super.onCreate()
        listener = SpeechListener(this)
        speaker = Speaker(this)
        instance = this
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification("Preparando…"),
                // O tipo "microfone" só existe a partir do Android 11; no 10 vale o que está no manifesto.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
            )
        } catch (e: Exception) {
            // Android 14+: serviço de microfone só pode começar com o app aberto.
            _error.value = "Abra o app para ativar a escuta da palavra “IANA”."
            stopSelf()
            return START_NOT_STICKY
        }
        if (detector == null) scope.launch { prepareAndListen() }
        // Se o Android matar o serviço, não tentamos voltar sozinhos (não dá pra abrir o microfone em segundo plano).
        return START_NOT_STICKY
    }

    private suspend fun prepareAndListen() {
        _error.value = null
        try {
            if (!VoskModel.isReady(this)) {
                _status.value = "Baixando o reconhecimento de voz (31 MB, só na primeira vez)…"
            }
            val dir = VoskModel.ensure(this) { percent ->
                scope.launch {
                    _status.value = "Baixando o reconhecimento de voz: $percent%"
                    updateNotification("Baixando reconhecimento de voz: $percent%")
                }
            }
            _status.value = "Carregando…"
            model = withContext(Dispatchers.IO) { Model(dir.path) }
            startDetector()
            _status.value = null
            _running.value = true
            updateNotification("Diga “IANA” e faça seu pedido.")
        } catch (e: IOException) {
            _status.value = null
            _error.value = "Não consegui baixar o reconhecimento de voz. Confira a internet e tente de novo."
            stopSelf()
        }
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        detector?.shutdown()
        recognizer?.close()
        model?.close()
        speaker.shutdown()
        _running.value = false
        _status.value = null
        super.onDestroy()
    }

    private fun startDetector() {
        val m = model ?: return
        val rec = recognizer ?: Recognizer(m, SAMPLE_RATE, GRAMMAR).also { recognizer = it }
        rec.reset()
        val service = detector ?: SpeechService(rec, SAMPLE_RATE).also { detector = it }
        service.startListening(voskListener)
    }

    /** Solta o microfone (o reconhecimento do pedido e o Vosk não podem gravar ao mesmo tempo). */
    private fun stopDetector() {
        detector?.stop()
    }

    private fun onWakeWord() {
        if (conversation?.isActive == true || pausedByApp) return
        stopDetector()
        conversation = scope.launch {
            try {
                beep()
                delay(250) // não deixa o bip entrar na gravação
                val container = (application as IanaApp).container
                // Uma rodada extra só para responder a um "Confirma?" sem precisar dizer "IANA" de novo.
                repeat(2) { round ->
                    if (round == 1) {
                        if (!container.aiAgent.hasPending) return@repeat
                        beep()
                        delay(250)
                    }
                    val heard = runCatching { listener.listen() }.getOrDefault("")
                    if (heard.isBlank()) return@launch
                    // Na rodada sem a palavra "IANA" só vale sim/não: qualquer outra coisa é ignorada,
                    // para uma frase solta da TV não virar um comando completo.
                    if (round == 1 && Confirmation.parse(heard) == Answer.OTHER) return@launch
                    val reply = try {
                        container.assistant.handle(heard)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Reply("Tive um problema agora. Tente de novo.")
                    }
                    val speech = if (reply.needs != null) {
                        "Abra o aplicativo para me dar permissão de agenda."
                    } else {
                        reply.speech
                    }
                    speaker.speak(speech)
                }
            } finally {
                if (!pausedByApp) runCatching { startDetector() }
            }
        }
    }

    private fun beep() {
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        scope.launch {
            delay(300)
            tone.release()
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Palavra de ativação", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Aparece enquanto a IANA está ouvindo a palavra “IANA”."
            },
        )
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, WakeWordService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("IANA está ouvindo")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .addAction(R.drawable.ic_stop, "Desligar", stop)
            .build()
    }

    companion object {
        private const val SAMPLE_RATE = 16_000f

        /**
         * Palavras que o Vosk procura. "[unk]" absorve todo o resto. Incluímos grafias parecidas
         * porque o modelo pode não conhecer "iana"; palavras que ele não conhece são ignoradas.
         */
        private const val GRAMMAR = """["iana", "yana", "ana", "[unk]"]"""
        private val WAKE = Regex("""\b(iana|yana|ana)\b""")

        private const val ACTION_STOP = "com.davipeter.iana.STOP_WAKE_WORD"
        private const val CHANNEL_ID = "wake_word"
        private const val NOTIFICATION_ID = 1

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        /** Mensagem de preparo (download do modelo etc.), ou nulo. */
        private val _status = MutableStateFlow<String?>(null)
        val status: StateFlow<String?> = _status.asStateFlow()

        private var instance: WakeWordService? = null

        fun start(context: Context) {
            _error.value = null
            ContextCompat.startForegroundService(context, Intent(context, WakeWordService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWordService::class.java))
        }

        /** O app vai usar o microfone (botão de falar): solta o microfone do Vosk. Chamar na main thread. */
        fun pauseForApp() {
            instance?.apply {
                pausedByApp = true
                conversation?.cancel()
                stopDetector()
            }
        }

        /** O app terminou de usar o microfone. Chamar na main thread. */
        fun resumeAfterApp() {
            instance?.apply {
                pausedByApp = false
                runCatching { startDetector() }
            }
        }
    }
}
