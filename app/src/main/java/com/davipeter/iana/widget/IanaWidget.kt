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
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import com.davipeter.iana.assistant.Motivation
import com.davipeter.iana.ui.MainActivity
import java.time.LocalDateTime

// Paleta da SyncForge (versão escura), igual à do app.
private val Fundo = Color(0xFF00072D)
private val Borda = Color(0xFF1F2A5C)
private val Texto = Color(0xFFEEF2FF)
private val Rotulo = Color(0xFF7E9DFF)
private val Azul = Color(0xFF2459F5)

/** Widget da tela inicial: a logo, a frase motivacional do momento e o botão de falar. */
class IanaWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val phrase = Motivation.phraseFor(LocalDateTime.now())
        val listen = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_LISTEN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        provideContent { Content(phrase, listen) }
    }

    @Composable
    private fun Content(phrase: String, listen: Intent) {
        // Borda fina: uma caixa azul-escura por fora e o fundo por dentro.
        Box(
            GlanceModifier.fillMaxSize().background(Borda).cornerRadius(28.dp).padding(1.dp),
        ) {
            Column(
                GlanceModifier
                    .fillMaxSize()
                    .background(Fundo)
                    .cornerRadius(27.dp)
                    .padding(horizontal = 18.dp, vertical = 16.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            ) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        ImageProvider(R.drawable.ic_logo),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(ColorProvider(Azul)),
                        modifier = GlanceModifier.size(22.dp),
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        "IANA",
                        style = TextStyle(color = ColorProvider(Rotulo), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    Box(
                        GlanceModifier.size(44.dp).background(Azul).cornerRadius(22.dp)
                            .clickable(actionStartActivity(listen)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            ImageProvider(R.drawable.ic_mic),
                            contentDescription = "Falar com a IANA",
                            colorFilter = ColorFilter.tint(ColorProvider(Texto)),
                            modifier = GlanceModifier.size(22.dp),
                        )
                    }
                }
                Spacer(GlanceModifier.height(12.dp))
                Text(
                    phrase,
                    style = TextStyle(color = ColorProvider(Texto), fontSize = 19.sp, fontWeight = FontWeight.Medium),
                    maxLines = 3,
                )
            }
        }
    }
}

class IanaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = IanaWidget()

    /** Primeiro widget adicionado: agenda as atualizações da frase. */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateWorker.schedule(context)
    }

    /** Último widget removido: para de atualizar. */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdateWorker.cancel(context)
    }
}
