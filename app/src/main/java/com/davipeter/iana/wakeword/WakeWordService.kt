package com.davipeter.iana.wakeword

import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
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
import com.davipeter.iana.BuildConfig
import com.davipeter.iana.IanaApp
import com.davipeter.iana.R
import com.davipeter.iana.ui.MainActivity
import com.davipeter.iana.voice.SpeechListener
import com.davipeter.iana.voice.Speaker
import com.davipeter.iana.widget.WidgetUpdateWorker
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

/**
 * Fica ouvindo a palavra "IANA" em segundo plano (Porcupine, 100% no aparelho).
 * Quando ouve: apita, escuta o comando, executa e responde falando, sem abrir o app.
 *
 * Roda como serviço em primeiro plano do tipo microfone, por isso aparece uma notificação fixa.
 */
class WakeWordService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var listener: SpeechListener
    private lateinit var speaker: Speaker
    private var detector: PorcupineManager? = null
    private var conversation: Job? = null
    private var pausedByApp = false

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
                buildNotification(),
                // O tipo "microfone" só existe a partir do Android 11; no 10 vale o que está no manifesto.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
            )
        } catch (e: Exception) {
            // Android 14+: serviço de microfone só pode começar com o app aberto.
            _error.value = "Abra o app para ativar a escuta da palavra “IANA”."
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startDetector()
            _running.value = true
            _error.value = null
        } catch (e: PorcupineException) {
            _error.value = "Erro na palavra de ativação: ${e.message}"
            stopSelf()
        }
        // Se o Android matar o serviço, não tentamos voltar sozinhos (não dá pra abrir o microfone em segundo plano).
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        releaseDetector()
        speaker.shutdown()
        _running.value = false
        super.onDestroy()
    }

    private fun startDetector() {
        val current = detector ?: PorcupineManager.Builder()
            .setAccessKey(BuildConfig.PICOVOICE_ACCESS_KEY)
            .setKeywordPath(KEYWORD_ASSET)
            .setModelPath(MODEL_ASSET)
            .setSensitivity(SENSITIVITY)
            .setErrorCallback { e -> scope.launch { _error.value = "Erro no microfone: ${e.message}" } }
            .build(applicationContext) { scope.launch { onWakeWord() } }
            .also { detector = it }
        current.start()
    }

    private fun stopDetector() {
        try {
            detector?.stop()
        } catch (e: PorcupineException) {
            // Já estava parado.
        }
    }

    private fun releaseDetector() {
        stopDetector()
        detector?.delete()
        detector = null
    }

    private fun onWakeWord() {
        if (conversation?.isActive == true || pausedByApp) return
        conversation = scope.launch {
            // O Porcupine e o reconhecimento de voz não podem usar o microfone ao mesmo tempo.
            stopDetector()
            try {
                beep()
                delay(250) // não deixa o bip entrar na gravação
                val heard = runCatching { listener.listen() }.getOrDefault("")
                if (heard.isNotBlank()) {
                    val reply = (application as IanaApp).container.assistant.handle(heard)
                    val speech = if (reply.needs != null) {
                        "Abra o aplicativo para me dar permissão de agenda."
                    } else {
                        reply.speech
                    }
                    speaker.speak(speech)
                    if (reply.changedCalendar) WidgetUpdateWorker.refreshNow(applicationContext)
                }
            } finally {
                if (!pausedByApp) runCatching { detector?.start() }
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

    private fun buildNotification(): Notification {
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
            .setContentText("Diga “IANA” e faça seu pedido.")
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(R.drawable.ic_stop, "Desligar", stop)
            .build()
    }

    companion object {
        const val KEYWORD_ASSET = "iana_pt_android.ppn"
        const val MODEL_ASSET = "porcupine_params_pt.pv"
        private const val SENSITIVITY = 0.6f
        private const val ACTION_STOP = "com.davipeter.iana.STOP_WAKE_WORD"
        private const val CHANNEL_ID = "wake_word"
        private const val NOTIFICATION_ID = 1

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        private var instance: WakeWordService? = null

        /** O que falta configurar, ou nulo se está tudo pronto. */
        fun missingSetup(context: Context): String? {
            if (BuildConfig.PICOVOICE_ACCESS_KEY.isBlank()) {
                return "Falta a AccessKey do Picovoice em local.properties (veja o README)."
            }
            val assets = context.assets.list("").orEmpty()
            if (KEYWORD_ASSET !in assets) return "Falta o arquivo $KEYWORD_ASSET em app/src/main/assets (veja o README)."
            return null
        }

        fun start(context: Context) {
            _error.value = null
            ContextCompat.startForegroundService(context, Intent(context, WakeWordService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWordService::class.java))
        }

        /** O app vai usar o microfone (botão de falar): solta o microfone do Porcupine. Chamar na main thread. */
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
                runCatching { detector?.start() }
            }
        }
    }
}
