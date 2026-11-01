package com.davipeter.iana.ui

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.davipeter.iana.ui.theme.Iana
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * O "rosto" da IANA: uma forma orgânica em âmbar que muda com o que ela está fazendo.
 * Parada ela respira; ouvindo, ondula com o volume da sua voz; pensando, gira;
 * falando, pulsa. Tocar nela = falar (ou parar).
 */
@Composable
fun VoiceOrb(
    phase: Phase,
    level: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 176.dp,
) {
    val context = LocalContext.current
    // Respeita "remover animações" das configurações de acessibilidade.
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val time by produceState(0f, reduceMotion) {
        if (reduceMotion) return@produceState
        val start = withFrameNanos { it }
        while (true) withFrameNanos { value = (it - start) / 1_000_000_000f }
    }
    val smoothLevel by animateFloatAsState(level, spring(stiffness = 300f), label = "level")
    val energy by animateFloatAsState(
        when (phase) {
            Phase.Idle -> 0.03f
            Phase.Listening -> 0.07f
            Phase.Thinking -> 0.05f
            Phase.Speaking -> 0.09f
        },
        label = "energy",
    )

    Canvas(
        modifier
            .size(size)
            .semantics {
                contentDescription = if (phase == Phase.Idle) "Falar com a IANA" else "Parar"
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        val center = Offset(this.size.width / 2, this.size.height / 2)
        val base = this.size.minDimension * 0.27f

        val breath = 1f + 0.035f * sin(time * 1.1f)
        val amplitude = energy + when (phase) {
            Phase.Listening -> smoothLevel * 0.2f
            Phase.Speaking -> 0.06f * abs(sin(time * 6.3f)) + 0.03f * abs(sin(time * 9.7f))
            else -> 0f
        }
        val spin = if (phase == Phase.Thinking) time * 2.4f else time * 0.4f

        fun blob(radius: Float): Path = Path().apply {
            val steps = 96
            for (i in 0..steps) {
                val a = (i.toFloat() / steps) * 2f * PI.toFloat()
                val wobble = 0.6f * sin(3 * a + spin + time * 0.9f) + 0.4f * sin(5 * a - time * 1.7f)
                val r = radius * breath * (1f + amplitude * wobble)
                val x = center.x + r * cos(a)
                val y = center.y + r * sin(a)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }

        // Halo.
        drawCircle(
            Brush.radialGradient(
                listOf(Iana.Ambar.copy(alpha = 0.28f + smoothLevel * 0.2f), Iana.Ambar.copy(alpha = 0f)),
                center = center,
                radius = base * 1.85f,
            ),
            radius = base * 1.85f,
            center = center,
        )
        // Corpo.
        drawPath(
            blob(base),
            Brush.radialGradient(
                listOf(Iana.AmbarClaro, Iana.Ambar, Iana.Ambar.copy(alpha = 0.85f)),
                center = center + Offset(-base * 0.25f, -base * 0.3f),
                radius = base * 1.3f,
            ),
        )
        // Enquanto ouve, um contorno lilás mostra que a vez é sua.
        if (phase == Phase.Listening) {
            drawPath(blob(base * 1.22f), Iana.Lilas.copy(alpha = 0.55f), style = Stroke(width = 1.5.dp.toPx()))
        }
    }
}
