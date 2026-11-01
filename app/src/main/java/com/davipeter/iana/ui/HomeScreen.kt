package com.davipeter.iana.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davipeter.iana.R
import com.davipeter.iana.assistant.Detail
import com.davipeter.iana.assistant.Permission
import com.davipeter.iana.assistant.PtBr
import com.davipeter.iana.ui.theme.Iana
import com.davipeter.iana.wakeword.WakeWordService
import java.time.LocalDateTime

/** Pedidos de exemplo mostrados quando a conversa está vazia. Tocar = pedir. */
private val SUGGESTIONS = listOf("Notícias", "Minha agenda de hoje", "Qual meu próximo compromisso?", "Que horas são?")

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

    Surface(Modifier.fillMaxSize(), color = Iana.Noite) {
        Column(Modifier.safeDrawingPadding().imePadding()) {
            TopBar(wakeRunning, onWakeToggle)
            (setupProblem ?: wakeError)?.let { problem ->
                Text(
                    problem,
                    color = Iana.Coral,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            Conversation(state.history, onSuggestion = viewModel::onTyped, modifier = Modifier.weight(1f))
            VoiceOrb(
                phase = state.phase,
                level = level,
                onClick = onMic,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Status(state, viewModel.voiceName)
            TypeBox(onSend = viewModel::onTyped)
        }
    }
}

@Composable
private fun TopBar(wakeRunning: Boolean, onWakeToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("IANA", style = MaterialTheme.typography.titleLarge, color = Iana.Lua, modifier = Modifier.weight(1f))
        // A pílula inteira é tocável, não só a chave.
        Row(
            Modifier
                .border(1.dp, Iana.Linha, CircleShape)
                .clickable(role = Role.Switch) { onWakeToggle(!wakeRunning) }
                .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Ouvir “IANA”",
                style = MaterialTheme.typography.labelLarge,
                color = if (wakeRunning) Iana.Lua else Iana.Nevoa,
            )
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = wakeRunning,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Iana.Ambar,
                    checkedThumbColor = Iana.Noite,
                    uncheckedTrackColor = Iana.Veludo,
                    uncheckedThumbColor = Iana.Nevoa,
                    uncheckedBorderColor = Iana.Linha,
                ),
            )
        }
    }
}

@Composable
private fun Conversation(history: List<Exchange>, onSuggestion: (String) -> Unit, modifier: Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) listState.animateScrollToItem(history.size) // o item 0 é a saudação
    }
    LazyColumn(
        modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 16.dp),
    ) {
        item(key = "greeting") { Greeting() }
        if (history.isEmpty()) {
            item(key = "suggestions") { Suggestions(onSuggestion) }
        }
        items(history, key = { it.id }) { exchange ->
            Column(Modifier.padding(top = 28.dp)) {
                Text(exchange.said, style = MaterialTheme.typography.labelLarge, color = Iana.Lilas)
                Spacer(Modifier.height(6.dp))
                Text(exchange.reply.speech, style = MaterialTheme.typography.headlineSmall, color = Iana.Lua)
                if (exchange.reply.details.isNotEmpty()) {
                    Details(exchange.reply.details, Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun Greeting() {
    val now = LocalDateTime.now()
    val hello = when (now.hour) {
        in 5..11 -> "Bom dia."
        in 12..17 -> "Boa tarde."
        else -> "Boa noite."
    }
    Column {
        Text(hello, style = MaterialTheme.typography.displayMedium, color = Iana.Lua)
        Spacer(Modifier.height(8.dp))
        Text(
            PtBr.fullDate(now.toLocalDate(), now.toLocalDate()).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyMedium,
            color = Iana.Nevoa,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(onSuggestion: (String) -> Unit) {
    Column(Modifier.padding(top = 36.dp)) {
        Text("Toque na bola ou diga algo como:", style = MaterialTheme.typography.bodyMedium, color = Iana.Nevoa)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SUGGESTIONS.forEach { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.labelLarge,
                    color = Iana.Lua,
                    modifier = Modifier
                        .border(1.dp, Iana.Linha, RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) { onSuggestion(text) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** Manchetes, eventos etc.: uma linha âmbar à esquerda amarra a lista à resposta. */
@Composable
private fun Details(details: List<Detail>, modifier: Modifier = Modifier) {
    Column(
        modifier.drawBehind {
            drawLine(Iana.Ambar, Offset(0f, 4.dp.toPx()), Offset(0f, size.height - 4.dp.toPx()), strokeWidth = 2.dp.toPx())
        }.padding(start = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        details.forEach { d ->
            Column {
                Text(d.text, style = MaterialTheme.typography.bodyMedium, color = Iana.Lua)
                d.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Iana.Nevoa) }
            }
        }
    }
}

@Composable
private fun Status(state: UiState, voiceName: String) {
    val (text, color) = when {
        state.error != null -> state.error.orEmpty() to Iana.Coral
        state.phase == Phase.Listening -> state.partial.ifEmpty { "Pode falar." } to Iana.Lua
        state.phase == Phase.Thinking -> "Um instante…" to Iana.Nevoa
        state.phase == Phase.Speaking -> "Toque na bola para interromper" to Iana.Nevoa
        else -> (if (voiceName.isNotBlank()) "Voz: $voiceName" else "") to Iana.Nevoa
    }
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        maxLines = 2,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 6.dp),
    )
}

@Composable
private fun TypeBox(onSend: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val send = {
        onSend(text)
        text = ""
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text("Ou escreva um pedido", color = Iana.Nevoa) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Iana.Ambar,
            unfocusedBorderColor = Iana.Linha,
            cursorColor = Iana.Ambar,
            focusedTextColor = Iana.Lua,
            unfocusedTextColor = Iana.Lua,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { send() }),
        trailingIcon = {
            IconButton(onClick = send, enabled = text.isNotBlank()) {
                Icon(
                    painterResource(R.drawable.ic_send),
                    contentDescription = "Enviar",
                    tint = if (text.isNotBlank()) Iana.Ambar else Iana.Nevoa,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
    )
}
