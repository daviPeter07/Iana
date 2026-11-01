package com.davipeter.iana.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.davipeter.iana.IanaApp
import com.davipeter.iana.assistant.Permission
import com.davipeter.iana.assistant.Reply
import com.davipeter.iana.voice.SpeechException
import com.davipeter.iana.voice.SpeechListener
import com.davipeter.iana.voice.Speaker
import com.davipeter.iana.wakeword.WakeWordService
import com.davipeter.iana.widget.WidgetUpdateWorker
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
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val assistant = (app as IanaApp).container.assistant
    private val listener = SpeechListener(app)
    private val speaker = Speaker(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Volume da voz enquanto ouve (0 a 1). Separado do estado para não redesenhar a tela toda. */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    /** Qual voz está respondendo (VoiceStudio ou o motor do celular). */
    val voiceName: String get() = speaker.description

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
        val reply = assistant.handle(text)
        if (reply.changedCalendar) WidgetUpdateWorker.refreshNow(getApplication())
        _state.update {
            it.copy(
                phase = Phase.Speaking,
                partial = "",
                history = it.history + Exchange(nextId++, text, reply),
                waitingPermission = reply.needs?.let { p -> p to text },
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
