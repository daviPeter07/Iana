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

private val Background = Color(0xFF0B0F14)
private val Card = Color(0xFF161D25)
private val Primary = Color(0xFF4FD1FF)
private val OnBackground = Color(0xFFE3E8EE)
private val Muted = Color(0xFF9AA7B4)

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
                .background(Background)
                .cornerRadius(20.dp)
                .padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "IANA",
                    style = TextStyle(color = ColorProvider(Primary), fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Image(
                    ImageProvider(R.drawable.ic_refresh),
                    contentDescription = "Atualizar",
                    colorFilter = ColorFilter.tint(ColorProvider(Muted)),
                    modifier = GlanceModifier.size(28.dp).padding(4.dp).clickable(actionRunCallback<RefreshAction>()),
                )
                Spacer(GlanceModifier.width(6.dp))
                Box(
                    GlanceModifier.size(40.dp).background(Primary).cornerRadius(20.dp)
                        .clickable(actionStartActivity(listen)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        ImageProvider(R.drawable.ic_mic),
                        contentDescription = "Falar com a IANA",
                        colorFilter = ColorFilter.tint(ColorProvider(Background)),
                        modifier = GlanceModifier.size(22.dp),
                    )
                }
            }

            Spacer(GlanceModifier.height(10.dp))

            val eventText = when {
                !snapshot.calendarAllowed -> "Abra o app para liberar a agenda"
                snapshot.nextEvent != null -> snapshot.nextEvent
                snapshot.updatedAt == null -> "Carregando…"
                else -> "Nada na agenda nos próximos 7 dias"
            }
            Column(GlanceModifier.fillMaxWidth().background(Card).cornerRadius(12.dp).padding(10.dp)) {
                Text("PRÓXIMO", style = TextStyle(color = ColorProvider(Muted), fontSize = 10.sp, fontWeight = FontWeight.Medium))
                Text(eventText, style = TextStyle(color = ColorProvider(OnBackground), fontSize = 14.sp), maxLines = 1)
            }

            Spacer(GlanceModifier.height(10.dp))

            if (snapshot.headlines.isEmpty()) {
                Text(
                    if (snapshot.updatedAt == null) "Buscando notícias…" else "Sem notícias agora",
                    style = TextStyle(color = ColorProvider(Muted), fontSize = 13.sp),
                )
            } else {
                snapshot.headlines.forEach { title ->
                    Text(
                        "• $title",
                        style = TextStyle(color = ColorProvider(OnBackground), fontSize = 13.sp),
                        maxLines = 2,
                        modifier = GlanceModifier.padding(bottom = 4.dp),
                    )
                }
            }

            snapshot.updatedAt?.let {
                Text(
                    "Atualizado ${PtBr.time(it.toLocalTime())}",
                    style = TextStyle(color = ColorProvider(Muted), fontSize = 10.sp),
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
