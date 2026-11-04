package com.davipeter.iana.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.davipeter.iana.ai.UsageGuard
import com.davipeter.iana.ui.theme.Iana

/** Mostra se a IA está ligada; tocar abre as configurações. */
@Composable
fun AiChip(ai: AiState, onClick: () -> Unit) {
    val label = when {
        ai.keyMasked == null -> "IA: sem chave. Toque para configurar"
        !ai.enabled -> "IA: desligada (modo básico). Toque para configurar"
        else -> "IA: ligada (Claude). Toque para configurar"
    }
    Text(
        label,
        style = MaterialTheme.typography.bodySmall,
        color = Iana.Rotulo,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(top = 8.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(8.dp),
    )
}

@Composable
fun AiSettingsDialog(
    ai: AiState,
    onSaveKey: (String) -> Unit,
    onRemoveKey: () -> Unit,
    onToggleAi: (Boolean) -> Unit,
    onTogglePersonalWhenLocked: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    // O campo da chave vive só neste diálogo e some ao fechar.
    var draft by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Iana.Painel,
        title = { Text("Inteligência artificial", color = Iana.Texto) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (ai.keyMasked != null) {
                    Text("Chave salva: ${ai.keyMasked}", color = Iana.Corpo, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onRemoveKey) { Text("Remover chave", color = Iana.Erro) }
                } else {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text("Chave da API (sk-ant-…)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        // Teclado de senha e sem correção: o teclado não aprende nem sugere a chave.
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { onSaveKey(draft); draft = "" }),
                        isError = ai.keyError != null,
                        supportingText = if (ai.keyError != null) {
                            { Text(ai.keyError) }
                        } else {
                            null
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Iana.Texto,
                            unfocusedTextColor = Iana.Texto,
                            focusedBorderColor = Iana.AzulVivo,
                            unfocusedBorderColor = Iana.Borda,
                            focusedLabelColor = Iana.Rotulo,
                            unfocusedLabelColor = Iana.Rotulo,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { onSaveKey(draft); draft = "" }, enabled = draft.isNotBlank()) {
                        Text("Salvar chave", color = Iana.AzulVivo)
                    }
                }

                SettingSwitch(
                    title = "Usar a IA",
                    subtitle = "Desligada, a IANA volta aos comandos básicos.",
                    checked = ai.enabled,
                    onChange = onToggleAi,
                )
                SettingSwitch(
                    title = "Agenda com o celular bloqueado",
                    subtitle = "Deixa a IANA falar agenda e relatórios com a tela travada. Alterações sempre pedem desbloqueio.",
                    checked = ai.personalWhenLocked,
                    onChange = onTogglePersonalWhenLocked,
                )

                if (!ai.deviceSecure) {
                    Text(
                        "Este celular não tem bloqueio de tela (PIN, padrão ou digital). Sem ele, a proteção de " +
                            "\"celular bloqueado\" não funciona: qualquer pessoa perto pode pedir alterações.",
                        color = Iana.Erro,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    "Com a IA ligada, o que você fala e, quando preciso, sua agenda e as manchetes vão para a API " +
                        "da Anthropic para montar a resposta. A chave fica cifrada no celular (Android Keystore). " +
                        "Criar compromissos e mexer em relatórios sempre pede o seu \"sim\".",
                    color = Iana.Rotulo,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Pedidos à IA hoje: ${ai.usedToday} de ${UsageGuard.DEFAULT_DAILY_LIMIT}",
                    color = Iana.Rotulo,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar", color = Iana.Rotulo) }
        },
    )
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Iana.Texto, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, color = Iana.Rotulo, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = Iana.AzulVivo, uncheckedTrackColor = Iana.Painel),
        )
    }
}
