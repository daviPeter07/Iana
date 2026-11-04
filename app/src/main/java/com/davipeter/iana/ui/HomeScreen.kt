package com.davipeter.iana.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davipeter.iana.assistant.Permission
import com.davipeter.iana.assistant.PtBr
import com.davipeter.iana.assistant.Reply
import com.davipeter.iana.ui.theme.Iana
import com.davipeter.iana.voice.EmbeddedVoice
import androidx.compose.material3.LinearProgressIndicator
import com.davipeter.iana.voice.VoiceEngine
import com.davipeter.iana.assistant.Motivation
import com.davipeter.iana.R
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import com.davipeter.iana.wakeword.WakeWordService
import java.time.LocalDateTime

/** Atalhos mostrados antes do primeiro pedido. Tocar = pedir. */
private val SHORTCUTS = listOf("Notícias", "Minha agenda de hoje", "Próximo compromisso", "Que horas são?")

/** Só faz sentido com a IA ligada (no modo básico "cria…" viraria um evento na agenda). */
private val AI_SHORTCUTS = listOf("O que tenho amanhã à tarde?", "Cria um resumo toda manhã às 7h com agenda e notícias")

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val level by viewModel.level.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onMicTapped()
    }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        viewModel.onPermissionResult(result.values.all { it })
    }

    // A assistente pediu permissão de agenda: abre o diálogo do Android e repete o comando depois.
    LaunchedEffect(state.waitingPermission) {
        if (state.waitingPermission?.first == Permission.CALENDAR) {
            calendarPermission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
        }
    }

    val onMic = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.onMicTapped() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Criou um relatório: ele chega como notificação, então pede a permissão (Android 13+).
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(state.askNotifications) {
        if (state.askNotifications) {
            viewModel.consumeAskNotifications()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Aberto pelo botão de microfone do widget.
    LaunchedEffect(state.autoListen) {
        if (state.autoListen) {
            viewModel.consumeAutoListen()
            onMic()
        }
    }

    // Escuta da palavra "IANA" em segundo plano.
    val wakeRunning by WakeWordService.running.collectAsStateWithLifecycle()
    val wakeError by WakeWordService.error.collectAsStateWithLifecycle()
    val wakeStatus by WakeWordService.status.collectAsStateWithLifecycle()
    val wakePermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        // Só o microfone é obrigatório; sem notificação o serviço roda, mas o aviso fica escondido.
        if (result[Manifest.permission.RECORD_AUDIO] == true) WakeWordService.start(context)
    }
    val onWakeToggle: (Boolean) -> Unit = { enable ->
        if (!enable) {
            WakeWordService.stop(context)
        } else {
            val needed = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            wakePermissions.launch(needed.toTypedArray())
        }
    }

    Surface(Modifier.fillMaxSize(), color = Iana.Fundo) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(wakeRunning, onWakeToggle)
            wakeError?.let { Notice(it, Iana.Erro) }
            wakeStatus?.let { Notice(it, Iana.Rotulo) }
            if (!state.voiceReady) {
                InstallVoice(progress = state.voiceDownload, onInstall = viewModel::downloadVoice)
            }

            Greeting(Modifier.fillMaxWidth().padding(top = 28.dp))

            JarvisHud(
                phase = state.phase,
                level = level,
                onClick = onMic,
                modifier = Modifier.fillMaxWidth().widthIn(max = 380.dp).padding(vertical = 20.dp),
            )

            PhaseLabel(state.phase, state.error)
            Spacer(Modifier.height(16.dp))

            // Só o momento atual: o que está ouvindo agora ou a última resposta.
            val shown = when {
                state.phase == Phase.Listening || state.phase == Phase.Thinking -> Shown.Heard(state.partial)
                state.history.isNotEmpty() -> Shown.Answer(state.history.last().reply)
                else -> Shown.Idle
            }
            AnimatedContent(shown, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "shown") { s ->
                when (s) {
                    is Shown.Heard -> Text(
                        s.text.ifEmpty { "…" },
                        style = MaterialTheme.typography.titleMedium,
                        color = Iana.Rotulo,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    is Shown.Answer -> Answer(s.reply)
                    Shown.Idle -> Unit
                }
            }

            // Os atalhos voltam sempre que ela termina de responder.
            if (state.phase == Phase.Idle) {
                Shortcuts(
                    items = if (state.ai.active) SHORTCUTS.take(2) + AI_SHORTCUTS else SHORTCUTS,
                    onPick = viewModel::onTyped,
                    modifier = Modifier.padding(top = if (shown == Shown.Idle) 0.dp else 20.dp),
                )
            }

            var choosingVoice by rememberSaveable { mutableStateOf(false) }
            VoiceChip(viewModel.currentVoice, onClick = { choosingVoice = true })
            if (choosingVoice) {
                VoicePicker(
                    engines = viewModel.voiceEngines,
                    current = viewModel.currentVoice,
                    onPick = {
                        choosingVoice = false
                        viewModel.chooseVoice(it)
                    },
                    onDismiss = { choosingVoice = false },
                )
            }

            var configuringAi by rememberSaveable { mutableStateOf(false) }
            AiChip(state.ai, onClick = {
                viewModel.refreshAi()
                configuringAi = true
            })
            if (configuringAi) {
                AiSettingsDialog(
                    ai = state.ai,
                    onSaveKey = viewModel::saveApiKey,
                    onRemoveKey = viewModel::removeApiKey,
                    onToggleAi = viewModel::setAiEnabled,
                    onTogglePersonalWhenLocked = viewModel::setPersonalWhenLocked,
                    onDismiss = { configuringAi = false },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private sealed interface Shown {
    data class Heard(val text: String) : Shown
    data class Answer(val reply: Reply) : Shown
    data object Idle : Shown
}

@Composable
private fun TopBar(wakeRunning: Boolean, onWakeToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(R.drawable.ic_logo),
            contentDescription = null,
            tint = Iana.AzulVivo,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text("IANA", style = MaterialTheme.typography.titleLarge, color = Iana.Texto, modifier = Modifier.weight(1f))
        // A pílula inteira é tocável, não só a chave.
        Row(
            Modifier
                .border(1.dp, Iana.Borda, CircleShape)
                .clickable(role = Role.Switch) { onWakeToggle(!wakeRunning) }
                .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Ouvir “IANA”",
                style = MaterialTheme.typography.labelLarge,
                color = if (wakeRunning) Iana.Texto else Iana.Corpo,
            )
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = wakeRunning,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Iana.AzulVivo,
                    checkedThumbColor = Iana.Texto,
                    uncheckedTrackColor = Iana.Painel,
                    uncheckedThumbColor = Iana.Rotulo,
                    uncheckedBorderColor = Iana.Borda,
                ),
            )
        }
    }
}

@Composable
private fun Notice(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

/** Aparece enquanto a voz feminina da IANA não foi baixada. [progress] nulo = ainda não começou. */
@Composable
private fun InstallVoice(progress: Int?, onInstall: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .background(Iana.Painel, RoundedCornerShape(16.dp))
            .border(1.dp, Iana.Borda, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Baixe a voz da IANA", style = MaterialTheme.typography.labelLarge, color = Iana.Texto)
            Text(
                if (progress == null) {
                    "Voz feminina que funciona sem internet. São ${EmbeddedVoice.DOWNLOAD_MB} MB, uma vez só."
                } else {
                    "Baixando a voz: $progress%"
                },
                style = MaterialTheme.typography.bodySmall,
                color = Iana.Corpo,
            )
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    color = Iana.AzulVivo,
                    trackColor = Iana.Borda,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
        if (progress == null) {
            Spacer(Modifier.width(12.dp))
            Text(
                "Baixar voz",
                style = MaterialTheme.typography.labelLarge,
                color = Iana.Texto,
                modifier = Modifier
                    .background(Iana.AzulVivo, RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = onInstall)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun Greeting(modifier: Modifier) {
    val now = LocalDateTime.now()
    val hello = when (now.hour) {
        in 5..11 -> "Bom dia"
        in 12..17 -> "Boa tarde"
        else -> "Boa noite"
    }
    Column(modifier) {
        Text(
            PtBr.fullDate(now.toLocalDate(), now.toLocalDate()).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyMedium,
            color = Iana.Rotulo,
        )
        Spacer(Modifier.height(4.dp))
        Text("$hello, ${Motivation.NAME}", style = MaterialTheme.typography.headlineMedium, color = Iana.Texto)
        Spacer(Modifier.height(6.dp))
        Text(Motivation.phraseFor(now), style = MaterialTheme.typography.titleMedium, color = Iana.Corpo)
    }
}

/** O estado atual, logo abaixo do painel. */
@Composable
private fun PhaseLabel(phase: Phase, error: String?) {
    val (text, color) = when {
        error != null -> error to Iana.Erro
        phase == Phase.Listening -> "Ouvindo" to Iana.Texto
        phase == Phase.Thinking -> "Processando" to Iana.Rotulo
        phase == Phase.Speaking -> "Falando. Toque para interromper" to Iana.Rotulo
        else -> "Toque no painel ou diga “IANA”" to Iana.Rotulo
    }
    Text(text, style = MaterialTheme.typography.labelLarge, color = color, textAlign = TextAlign.Center)
}

/** A última resposta, com manchetes ou eventos num painel. */
@Composable
private fun Answer(reply: Reply) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            reply.speech,
            style = MaterialTheme.typography.titleMedium,
            color = Iana.Texto,
            textAlign = TextAlign.Center,
            maxLines = 5,
        )
        if (reply.details.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .background(Iana.Painel, RoundedCornerShape(20.dp))
                    .border(1.dp, Iana.Borda, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                reply.details.forEach { d ->
                    Column {
                        Text(d.text, style = MaterialTheme.typography.bodyMedium, color = Iana.Texto)
                        d.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Iana.Rotulo) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Shortcuts(items: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { text ->
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = Iana.Corpo,
                modifier = Modifier
                    .border(1.dp, Iana.Borda, RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { onPick(text) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/** Mostra qual voz está em uso; tocar abre a escolha de voz. */
@Composable
private fun VoiceChip(current: VoiceEngine?, onClick: () -> Unit) {
    Text(
        "Voz: ${current?.label ?: "padrão do celular"}. Toque para trocar",
        style = MaterialTheme.typography.bodySmall,
        color = Iana.Rotulo,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(top = 28.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(8.dp),
    )
}

@Composable
private fun VoicePicker(
    engines: List<VoiceEngine>,
    current: VoiceEngine?,
    onPick: (VoiceEngine) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Iana.Painel,
        title = { Text("Escolher voz", color = Iana.Texto) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (engines.isEmpty()) {
                    Text("Nenhum motor de voz instalado.", color = Iana.Corpo)
                }
                engines.forEach { engine ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.RadioButton) { onPick(engine) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = engine.packageName == current?.packageName,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = Iana.AzulVivo, unselectedColor = Iana.Rotulo),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(engine.label, style = MaterialTheme.typography.bodyLarge, color = Iana.Texto)
                            Text(
                                if (engine.isOpenSource) "Open source, funciona sem internet" else engine.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = Iana.Rotulo,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar", color = Iana.Rotulo) }
        },
    )
}
