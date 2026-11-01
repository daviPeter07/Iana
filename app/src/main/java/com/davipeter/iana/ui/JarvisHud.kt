package com.davipeter.iana.ui

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.davipeter.iana.ui.theme.Iana
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Painel estilo Jarvis: anéis de marcação girando, arcos segmentados, barras radiais que
 * reagem à voz e um núcleo que brilha. Cada estado tem um movimento próprio:
 * - em espera: tudo gira devagar e o núcleo respira;
 * - ouvindo: as barras seguem o volume da sua voz;
 * - pensando: um feixe de varredura gira e uma onda percorre as barras;
 * - falando: as barras e o núcleo pulsam como uma onda sonora.
 * Tocar no painel = falar (ou interromper).
 */
@Composable
fun JarvisHud(
    phase: Phase,
    level: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
    val voice by animateFloatAsState(level, spring(stiffness = 400f), label = "voice")
    val glow by animateFloatAsState(
        when (phase) {
            Phase.Idle -> 0.35f
            Phase.Listening -> 0.6f
            Phase.Thinking -> 0.5f
            Phase.Speaking -> 0.8f
        },
        tween(400),
        label = "glow",
    )
    val spinSpeed by animateFloatAsState(if (phase == Phase.Thinking) 4f else 1f, tween(600), label = "spin")

    Canvas(
        modifier
            .aspectRatio(1f)
            .semantics { contentDescription = if (phase == Phase.Idle) "Falar com a IANA" else "Interromper" }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        val c = center
        val r = size.minDimension / 2f

        // Brilho de fundo.
        drawCircle(
            Brush.radialGradient(
                listOf(Iana.AzulVivo.copy(alpha = 0.22f * glow + 0.08f), Color.Transparent),
                center = c,
                radius = r,
            ),
            radius = r,
            center = c,
        )

        tickRing(c, r * 0.97f, rotation = time * 4f)
        segmentedArcs(c, r * 0.86f, rotation = -time * 14f * spinSpeed)
        radialBars(c, inner = r * 0.52f, maxLength = r * 0.24f, phase = phase, voice = voice, time = time)

        // Anel interno.
        drawCircle(Iana.Borda, radius = r * 0.47f, center = c, style = Stroke(1.dp.toPx()))

        if (phase == Phase.Thinking) scanner(c, r * 0.38f, width = r * 0.16f, rotation = time * 220f)

        core(c, r, glow = glow, pulse = if (phase == Phase.Speaking) 0.5f + 0.5f * sin(time * 9f) else 0f, breath = sin(time * 1.4f))
    }
}

/** Anel externo de marcações, como um mostrador. */
private fun DrawScope.tickRing(c: Offset, radius: Float, rotation: Float) {
    rotate(rotation, c) {
        val ticks = 120
        for (i in 0 until ticks) {
            val major = i % 10 == 0
            val a = (i.toFloat() / ticks) * 2f * PI.toFloat()
            val len = if (major) 10.dp.toPx() else 4.dp.toPx()
            val start = Offset(c.x + (radius - len) * cos(a), c.y + (radius - len) * sin(a))
            val end = Offset(c.x + radius * cos(a), c.y + radius * sin(a))
            drawLine(
                if (major) Iana.Rotulo.copy(alpha = 0.8f) else Iana.Borda,
                start,
                end,
                strokeWidth = if (major) 1.5.dp.toPx() else 1.dp.toPx(),
            )
        }
    }
}

/** Três arcos grossos e três finos, girando no sentido contrário ao mostrador. */
private fun DrawScope.segmentedArcs(c: Offset, radius: Float, rotation: Float) {
    val topLeft = Offset(c.x - radius, c.y - radius)
    val arcSize = Size(radius * 2, radius * 2)
    rotate(rotation, c) {
        for (k in 0 until 3) {
            drawArc(
                Iana.Rotulo.copy(alpha = 0.55f),
                startAngle = k * 120f,
                sweepAngle = 62f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Butt),
            )
            drawArc(
                Iana.AzulVivo.copy(alpha = 0.7f),
                startAngle = k * 120f + 72f,
                sweepAngle = 18f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(1.dp.toPx()),
            )
        }
    }
}

/** Barras em volta do núcleo: o "equalizador" da voz. */
private fun DrawScope.radialBars(c: Offset, inner: Float, maxLength: Float, phase: Phase, voice: Float, time: Float) {
    val bars = 72
    for (i in 0 until bars) {
        val a = (i.toFloat() / bars) * 2f * PI.toFloat()
        val amount = when (phase) {
            Phase.Idle -> 0.06f + 0.03f * sin(time * 1.4f + i * 0.5f)
            Phase.Listening -> 0.08f + voice * (0.55f + 0.45f * sin(i * 1.7f + time * 9f))
            Phase.Thinking -> 0.08f + 0.6f * max(0f, cos(a - time * 5f)).let { it * it * it }
            Phase.Speaking -> 0.25f + 0.4f * sin(time * 9f + i * 0.6f) * sin(time * 3.1f + i * 0.21f)
        }.coerceIn(0.04f, 1f)
        val len = maxLength * amount
        val start = Offset(c.x + inner * cos(a), c.y + inner * sin(a))
        val end = Offset(c.x + (inner + len) * cos(a), c.y + (inner + len) * sin(a))
        drawLine(
            Brush.linearGradient(listOf(Iana.AzulVivo, Iana.Rotulo.copy(alpha = 0.4f + amount * 0.6f)), start, end),
            start,
            end,
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** Feixe de varredura, como um radar. */
private fun DrawScope.scanner(c: Offset, radius: Float, width: Float, rotation: Float) {
    rotate(rotation, c) {
        drawArc(
            Brush.sweepGradient(
                0f to Color.Transparent,
                0.16f to Iana.AzulVivo.copy(alpha = 0.55f),
                0.17f to Color.Transparent,
                center = c,
            ),
            startAngle = 0f,
            sweepAngle = 60f,
            useCenter = false,
            topLeft = Offset(c.x - radius, c.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width),
        )
    }
}

/** Núcleo: brilho azul, anel e um ponto claro no centro. */
private fun DrawScope.core(c: Offset, r: Float, glow: Float, pulse: Float, breath: Float) {
    val glowRadius = r * (0.36f + 0.04f * breath + 0.06f * pulse)
    drawCircle(
        Brush.radialGradient(
            listOf(Iana.AzulVivo.copy(alpha = 0.35f + 0.5f * glow), Iana.AzulVivo.copy(alpha = 0f)),
            center = c,
            radius = glowRadius,
        ),
        radius = glowRadius,
        center = c,
    )
    drawCircle(Iana.Rotulo.copy(alpha = 0.8f), radius = r * 0.2f, center = c, style = Stroke(1.5.dp.toPx()))
    val coreRadius = r * (0.11f + 0.015f * breath + 0.03f * pulse)
    drawCircle(
        Brush.radialGradient(listOf(Iana.Texto, Iana.AzulVivo), center = c, radius = coreRadius),
        radius = coreRadius,
        center = c,
    )
}
