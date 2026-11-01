package com.davipeter.iana.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davipeter.iana.R
import com.davipeter.iana.assistant.Permission
import com.davipeter.iana.wakeword.WakeWordService

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
    var setupProblem by rememberSaveable { mutableStateOf<String?>(null) }
    val wakePermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        // Só o microfone é obrigatório; sem notificação o serviço roda, mas o aviso fica escondido.
        if (result[Manifest.permission.RECORD_AUDIO] == true) WakeWordService.start(context)
    }
    val onWakeToggle: (Boolean) -> Unit = { enable ->
        if (!enable) {
            WakeWordService.stop(context)
        } else {
            setupProblem = WakeWordService.missingSetup(context)
            if (setupProblem == null) {
                val needed = buildList {
                    add(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
                }
                wakePermissions.launch(needed.toTypedArray())
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "IANA",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Ouvir “IANA”",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Switch(checked = wakeRunning, onCheckedChange = onWakeToggle)
            }
            (setupProblem ?: wakeError)?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            History(state.history, Modifier.weight(1f))
            StatusLine(state)
            MicButton(state.phase, onMic, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(12.dp))
            TypeBox(onSend = viewModel::onTyped)
        }
    }
}

@Composable
private fun History(history: List<Exchange>, modifier: Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) listState.animateScrollToItem(history.lastIndex)
    }
    if (history.isEmpty()) {
        Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                "Toque no microfone e diga, por exemplo:\n“notícias”, “minha agenda de amanhã” ou\n“marca dentista sexta às 10h”.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }
    LazyColumn(
        modifier.fillMaxWidth(),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(history, key = { it.id }) { exchange ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Bubble(exchange.said, fromUser = true)
                Bubble(exchange.reply.speech, fromUser = false, details = exchange.reply.details)
            }
        }
    }
}

@Composable
private fun Bubble(text: String, fromUser: Boolean, details: List<String> = emptyList()) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .background(
                    if (fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    RoundedCornerShape(16.dp),
                )
                .padding(12.dp),
        ) {
            Text(
                text,
                color = if (fromUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            )
            details.forEach {
                Text(
                    "• $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusLine(state: UiState) {
    val text = state.error ?: when (state.phase) {
        Phase.Idle -> ""
        Phase.Listening -> state.partial.ifEmpty { "Ouvindo…" }
        Phase.Thinking -> "Pensando…"
        Phase.Speaking -> "Falando… (toque para parar)"
    }
    Text(
        text,
        color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun MicButton(phase: Phase, onClick: () -> Unit, modifier: Modifier) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "scale",
    )
    val busy = phase != Phase.Idle
    FilledIconButton(
        onClick = onClick,
        shape = CircleShape,
        modifier = modifier.size(88.dp).scale(if (phase == Phase.Listening) pulse else 1f),
    ) {
        Icon(
            painterResource(if (busy) R.drawable.ic_stop else R.drawable.ic_mic),
            contentDescription = if (busy) "Parar" else "Falar com a IANA",
            modifier = Modifier.size(40.dp),
        )
    }
}

@Composable
private fun TypeBox(onSend: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val send = {
        onSend(text)
        text = ""
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Ou digite um comando") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = send) {
            Icon(painterResource(R.drawable.ic_send), contentDescription = "Enviar")
        }
    }
}
