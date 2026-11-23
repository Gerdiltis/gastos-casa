package com.gastoscasa.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gastoscasa.ui.theme.AppColors

data class DonutSegment(val value: Float, val color: Color)

/** Donut grueso con extremos redondeados, como "Where your money goes". */
@Composable
fun DonutChart(
    segments: List<DonutSegment>,
    centerTop: String,
    centerBottom: String,
    modifier: Modifier = Modifier,
    size: Dp = 170.dp,
    thickness: Dp = 30.dp,
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = thickness.toPx()
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            val total = segments.sumOf { it.value.toDouble() }.toFloat()
            if (total <= 0f) {
                drawArc(AppColors.Soft, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                return@Canvas
            }
            val gap = if (segments.size > 1) 10f else 0f
            var start = -90f
            segments.forEach { s ->
                val sweep = 360f * s.value / total
                drawArc(
                    s.color, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false, topLeft, arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTop, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
            Text(centerBottom, color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Gráfico de área amarillo con punto turquesa en [highlight] (pantalla Analytics). */
@Composable
fun AreaChart(
    values: List<Long>,
    labels: List<String>,
    highlight: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(130.dp).padding(vertical = 8.dp)) {
            if (values.isEmpty()) return@Canvas
            val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
            val stepX = if (values.size > 1) size.width / (values.size - 1) else size.width
            fun pt(i: Int) = Offset(i * stepX, size.height - (values[i] / max) * size.height * 0.85f - 4f)

            // guías horizontales
            repeat(3) { r ->
                val y = size.height * (r + 1) / 4f
                drawLine(AppColors.Line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }

            val line = Path().apply {
                moveTo(pt(0).x, pt(0).y)
                for (i in 1 until values.size) {
                    val p0 = pt(i - 1)
                    val p1 = pt(i)
                    val cx = (p0.x + p1.x) / 2
                    cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(pt(values.lastIndex).x, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(AppColors.Yellow.copy(alpha = 0.75f), AppColors.Yellow.copy(alpha = 0.05f))))
            drawPath(line, AppColors.Yellow, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))

            if (highlight in values.indices) {
                val p = pt(highlight)
                drawLine(AppColors.Teal, Offset(p.x, 0f), p, strokeWidth = 3.dp.toPx())
                drawCircle(AppColors.Teal, 9.dp.toPx(), p)
                drawCircle(Color.White, 4.dp.toPx(), p)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, l ->
                Text(
                    l, style = MaterialTheme.typography.labelMedium,
                    color = if (i == highlight) AppColors.Ink else AppColors.Muted,
                    textAlign = when (i) { 0 -> TextAlign.Start; labels.lastIndex -> TextAlign.End; else -> TextAlign.Center },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Anillo de porcentaje (Category Breakdown). */
@Composable
fun PercentRing(percent: Int, color: Color, size: Dp = 56.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 4.dp.toPx()
            val tl = Offset(stroke / 2, stroke / 2)
            val s = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(color.copy(alpha = 0.15f), 0f, 360f, false, tl, s, style = Stroke(stroke))
            drawArc(color, -90f, 360f * percent / 100f, false, tl, s, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("$percent%", fontSize = 13.sp, color = color)
    }
}
