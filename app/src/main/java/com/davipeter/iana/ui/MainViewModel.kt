package com.davipeter.iana.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.davipeter.iana.IanaApp
import com.davipeter.iana.ai.ApiKeyFormat
import com.davipeter.iana.assistant.Permission
import com.davipeter.iana.assistant.Reply
import com.davipeter.iana.voice.SpeechException
import com.davipeter.iana.voice.SpeechListener
import com.davipeter.iana.voice.Speaker
import com.davipeter.iana.voice.VoiceEngine
import com.davipeter.iana.assistant.Motivation
import com.davipeter.iana.wakeword.WakeWordService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Phase { Idle, Listening, Thinking, Speaking }

data class Exchange(val id: Long, val said: String, val reply: Reply)

data class UiState(
    val phase: Phase = Phase.Idle,
    val partial: String = "",
    val history: List<Exchange> = emptyList(),
    val error: String? = null,
    /** Comando que ficou esperando o usuário conceder uma permissão. */
    val waitingPermission: Pair<Permission, String>? = null,
    /** O app foi aberto pelo botão de microfone do widget: começar a ouvir assim que der. */
    val autoListen: Boolean = false,
    /** A voz da IANA já está no celular. */
    val voiceReady: Boolean = false,
    /** Progresso do download da voz (0..100), ou nulo se não está baixando. */
    val voiceDownload: Int? = null,
    val ai: AiState = AiState(),
    /** Um relatório foi criado: a tela pede a permissão de notificação (Android 13+). */
    val askNotifications: Boolean = false,
)

/** O que a tela mostra sobre a IA. A chave em si nunca chega aqui, só a versão mascarada. */
data class AiState(
    val keyMasked: String? = null,
    val enabled: Boolean = true,
    val personalWhenLocked: Boolean = false,
    val usedToday: Int = 0,
    val keyError: String? = null,
    /** O celular tem PIN/padrão/biometria; sem isso a proteção de tela bloqueada não vale. */
    val deviceSecure: Boolean = true,
) {
    val active: Boolean get() = enabled && keyMasked != null
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as IanaApp).container
    private val assistant = container.assistant
    private val listener = SpeechListener(app)
    private val speaker = Speaker(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Volume da voz enquanto ouve (0 a 1). Separado do estado para não redesenhar a tela toda. */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    init {
        _state.update { it.copy(voiceReady = speaker.hasIanaVoice) }
        refreshAi()
    }

    // ---------- IA ----------

    fun refreshAi(keyError: String? = null) {
        val masked = container.apiKeys.get()?.let(ApiKeyFormat::masked)
        _state.update {
            it.copy(
                ai = AiState(
                    keyMasked = masked,
                    enabled = container.settings.aiEnabled,
                    personalWhenLocked = container.settings.personalDataWhenLocked,
                    usedToday = container.usage.usedToday(),
                    keyError = keyError,
                    deviceSecure = container.deviceLock.isSecure(),
                ),
            )
        }
    }

    /** Salva a chave cifrada. Recusa o que claramente não é uma chave da Anthropic. */
    fun saveApiKey(raw: String) {
        val key = ApiKeyFormat.normalize(raw)
        if (!ApiKeyFormat.isPlausible(key)) {
            refreshAi(keyError = "Isso não parece uma chave da API (começa com sk-ant-).")
            return
        }
        container.apiKeys.set(key)
        refreshAi()
    }

    fun removeApiKey() {
        container.apiKeys.clear()
        viewModelScope.launch { container.aiAgent.forget() }
        refreshAi()
    }

    fun setAiEnabled(enabled: Boolean) {
        container.settings.aiEnabled = enabled
        if (!enabled) viewModelScope.launch { container.aiAgent.forget() }
        refreshAi()
    }

    fun setPersonalWhenLocked(allowed: Boolean) {
        container.settings.personalDataWhenLocked = allowed
        refreshAi()
    }

    fun consumeAskNotifications() {
        _state.update { it.copy(askNotifications = false) }
    }

    /** Baixa a voz da IANA dentro do app, mostrando o progresso. */
    fun downloadVoice() {
        if (_state.value.voiceDownload != null) return
        viewModelScope.launch {
            _state.update { it.copy(voiceDownload = 0, error = null) }
            try {
                speaker.downloadIanaVoice { p -> _state.update { it.copy(voiceDownload = p) } }
                _state.update { it.copy(voiceDownload = null, voiceReady = true) }
                speaker.choose(Speaker.IANA)
                speaker.speak("Pronto, ${Motivation.NAME}. Agora esta é a minha voz.")
            } catch (e: java.io.IOException) {
                _state.update {
                    it.copy(voiceDownload = null, error = "Não consegui baixar a voz. Confira a internet e tente de novo.")
                }
            }
        }
    }

    /** Motores de voz instalados e o que está em uso, para a escolha de voz. */
    val voiceEngines: List<VoiceEngine> get() = speaker.engines
    val currentVoice: VoiceEngine? get() = speaker.current

    /** Troca a voz e faz ela se apresentar, pra você ouvir na hora. */
    fun chooseVoice(engine: VoiceEngine) {
        job?.cancel()
        speaker.choose(engine)
        job = viewModelScope.launch {
            _state.update { it.copy(phase = Phase.Speaking) }
            speaker.speak("Oi, ${Motivation.NAME}. Esta é a minha voz agora.")
            _state.update { it.copy(phase = Phase.Idle) }
        }
    }

    private var job: Job? = null
    private var nextId = 0L

    /** Toque no microfone: começa a ouvir ou, se já estiver ocupado, para tudo. */
    fun onMicTapped() {
        if (job?.isActive == true) {
            stop()
            return
        }
        if (!listener.isAvailable()) {
            _state.update { it.copy(error = "Este celular não tem reconhecimento de voz. Instale o app Google.") }
            return
        }
        job = viewModelScope.launch {
            // Se a escuta da palavra "IANA" estiver ligada, ela solta o microfone enquanto o app usa.
            WakeWordService.pauseForApp()
            try {
                _state.update { it.copy(phase = Phase.Listening, partial = "", error = null) }
                val heard = try {
                    listener.listen(
                        onPartial = { partial -> _state.update { it.copy(partial = partial) } },
                        onLevel = { _level.value = it },
                    )
                } catch (e: SpeechException) {
                    _state.update { it.copy(phase = Phase.Idle, partial = "", error = e.friendlyMessage) }
                    return@launch
                }
                if (heard.isBlank()) {
                    _state.update { it.copy(phase = Phase.Idle, partial = "") }
                    return@launch
                }
                respond(heard)
            } finally {
                _level.value = 0f
                WakeWordService.resumeAfterApp()
            }
        }
    }

    /** O widget pediu para ouvir. A tela confere a permissão de microfone e chama [onMicTapped]. */
    fun requestAutoListen() {
        _state.update { it.copy(autoListen = true) }
    }

    fun consumeAutoListen() {
        _state.update { it.copy(autoListen = false) }
    }

    /** Comando digitado (útil no emulador e em lugares onde não dá pra falar). */
    fun onTyped(text: String) {
        if (text.isBlank()) return
        job?.cancel()
        job = viewModelScope.launch { respond(text.trim()) }
    }

    /** Chamado pela tela depois que o usuário respondeu ao pedido de permissão. */
    fun onPermissionResult(granted: Boolean) {
        val (_, pending) = _state.value.waitingPermission ?: return
        _state.update { it.copy(waitingPermission = null) }
        if (granted) onTyped(pending)
    }

    fun stop() {
        job?.cancel()
        speaker.stop()
        _state.update { it.copy(phase = Phase.Idle, partial = "") }
    }

    private suspend fun respond(text: String) {
        _state.update { it.copy(phase = Phase.Thinking, partial = text, error = null) }
        val reply = try {
            assistant.handle(text)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Reply("Tive um problema agora. Tente de novo.")
        }
        _state.update {
            it.copy(
                phase = Phase.Speaking,
                partial = "",
                history = it.history + Exchange(nextId++, text, reply),
                waitingPermission = reply.needs?.let { p -> p to text },
                askNotifications = it.askNotifications || reply.changedReports,
                ai = it.ai.copy(usedToday = container.usage.usedToday()),
            )
        }
        val spoke = speaker.speak(reply.speech)
        _state.update {
            it.copy(
                phase = Phase.Idle,
                error = if (spoke) it.error else "Sem voz em português instalada. Veja Configurações > Saída de texto para voz.",
            )
        }
    }

    override fun onCleared() {
        speaker.shutdown()
    }
}
