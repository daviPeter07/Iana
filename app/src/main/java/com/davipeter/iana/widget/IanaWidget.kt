package com.davipeter.iana.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.davipeter.iana.R
import com.davipeter.iana.assistant.PtBr
import com.davipeter.iana.ui.MainActivity

// Paleta da SyncForge (versão escura), igual à do app.
private val Noite = Color(0xFF00072D)
private val Veludo = Color(0xFF0A1440)
private val Lua = Color(0xFFEEF2FF)
private val Nevoa = Color(0xFF7E9DFF)
private val Azul = Color(0xFF2459F5)

/** Widget da tela inicial: próximo compromisso, manchetes e botão para falar com a IANA. */
class IanaWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetStore(context).load()
        val listen = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_LISTEN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        provideContent {
            Content(snapshot, listen)
        }
    }

    @Composable
    private fun Content(snapshot: WidgetSnapshot, listen: Intent) {
        Column(
            GlanceModifier
                .fillMaxSize()
                .background(Noite)
                .cornerRadius(24.dp)
                .padding(16.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            // Compromisso em destaque, com o botão de falar ao lado.
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    val (title, whenText) = when {
                        !snapshot.calendarAllowed -> "Agenda bloqueada" to "Abra a IANA para liberar"
                        snapshot.nextEventTitle != null -> snapshot.nextEventTitle.orEmpty() to snapshot.nextEventWhen.orEmpty()
                        snapshot.updatedAt == null -> "Carregando…" to ""
                        else -> "Agenda livre" to "Nada nos próximos 7 dias"
                    }
                    Text(
                        title,
                        style = TextStyle(color = ColorProvider(Lua), fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                    if (whenText.isNotEmpty()) {
                        Text(whenText, style = TextStyle(color = ColorProvider(Nevoa), fontSize = 13.sp), maxLines = 1)
                    }
                }
                Spacer(GlanceModifier.width(12.dp))
                Box(
                    GlanceModifier.size(48.dp).background(Azul).cornerRadius(24.dp)
                        .clickable(actionStartActivity(listen)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        ImageProvider(R.drawable.ic_mic),
                        contentDescription = "Falar com a IANA",
                        colorFilter = ColorFilter.tint(ColorProvider(Lua)),
                        modifier = GlanceModifier.size(24.dp),
                    )
                }
            }

            Spacer(GlanceModifier.height(14.dp))

            // Manchetes num bloco mais escuro.
            Column(GlanceModifier.fillMaxWidth().defaultWeight().background(Veludo).cornerRadius(16.dp).padding(12.dp)) {
                if (snapshot.headlines.isEmpty()) {
                    Text(
                        if (snapshot.updatedAt == null) "Buscando notícias…" else "Sem conexão para buscar notícias",
                        style = TextStyle(color = ColorProvider(Nevoa), fontSize = 13.sp),
                    )
                } else {
                    snapshot.headlines.forEach { title ->
                        Text(
                            title,
                            style = TextStyle(color = ColorProvider(Lua), fontSize = 13.sp),
                            maxLines = 2,
                            modifier = GlanceModifier.padding(bottom = 8.dp),
                        )
                    }
                }
            }

            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    snapshot.updatedAt?.let { "Atualizado às ${PtBr.time(it.toLocalTime())}" } ?: "",
                    style = TextStyle(color = ColorProvider(Nevoa), fontSize = 11.sp),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Image(
                    ImageProvider(R.drawable.ic_refresh),
                    contentDescription = "Atualizar",
                    colorFilter = ColorFilter.tint(ColorProvider(Nevoa)),
                    modifier = GlanceModifier.size(28.dp).padding(5.dp).clickable(actionRunCallback<RefreshAction>()),
                )
            }
        }
    }
}

/** Botão de atualizar do widget. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetUpdateWorker.refreshNow(context)
    }
}

class IanaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = IanaWidget()

    /** Primeiro widget adicionado: agenda as atualizações e já busca os dados. */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateWorker.schedule(context)
        WidgetUpdateWorker.refreshNow(context)
    }

    /** Último widget removido: para de atualizar. */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdateWorker.cancel(context)
    }
}
