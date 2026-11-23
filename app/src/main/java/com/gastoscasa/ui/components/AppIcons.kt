package com.gastoscasa.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Iconos de línea que no están en material-icons-core. */
object AppIcons {
    private fun line(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.stroke(d: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        path(
            stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, pathBuilder = d,
        )

    val Bars: ImageVector by lazy {
        line("Bars") {
            stroke { moveTo(6f, 19f); lineTo(6f, 13f) }
            stroke { moveTo(12f, 19f); lineTo(12f, 6f) }
            stroke { moveTo(18f, 19f); lineTo(18f, 10f) }
        }
    }

    val Groups: ImageVector by lazy {
        line("Groups") {
            stroke { moveTo(9f, 11f); arcTo(3.2f, 3.2f, 0f, true, true, 9.01f, 11f) }
            stroke { moveTo(3f, 20f); curveTo(3f, 16f, 5.5f, 14.5f, 9f, 14.5f); curveTo(12.5f, 14.5f, 15f, 16f, 15f, 20f) }
            stroke { moveTo(16f, 5.2f); curveTo(18f, 5.5f, 19f, 7f, 19f, 8.3f); curveTo(19f, 9.8f, 18f, 11f, 16f, 11.3f) }
            stroke { moveTo(17.5f, 14.6f); curveTo(19.8f, 15.2f, 21f, 16.8f, 21f, 20f) }
        }
    }

    val Home: ImageVector by lazy {
        line("Home") {
            stroke { moveTo(4f, 10.5f); lineTo(12f, 4f); lineTo(20f, 10.5f); lineTo(20f, 20f); lineTo(4f, 20f); close() }
            stroke { moveTo(10f, 20f); lineTo(10f, 15f); lineTo(14f, 15f); lineTo(14f, 20f) }
        }
    }

    val Gear: ImageVector by lazy {
        line("Gear") {
            stroke { moveTo(12f, 9f); arcTo(3f, 3f, 0f, true, true, 12.01f, 9f) }
            stroke {
                moveTo(12f, 3f); lineTo(13.6f, 5.2f); lineTo(16.3f, 4.5f); lineTo(16.9f, 7.1f); lineTo(19.5f, 7.7f)
                lineTo(18.8f, 10.4f); lineTo(21f, 12f); lineTo(18.8f, 13.6f); lineTo(19.5f, 16.3f); lineTo(16.9f, 16.9f)
                lineTo(16.3f, 19.5f); lineTo(13.6f, 18.8f); lineTo(12f, 21f); lineTo(10.4f, 18.8f); lineTo(7.7f, 19.5f)
                lineTo(7.1f, 16.9f); lineTo(4.5f, 16.3f); lineTo(5.2f, 13.6f); lineTo(3f, 12f); lineTo(5.2f, 10.4f)
                lineTo(4.5f, 7.7f); lineTo(7.1f, 7.1f); lineTo(7.7f, 4.5f); lineTo(10.4f, 5.2f); close()
            }
        }
    }

    val Bulb: ImageVector by lazy {
        line("Bulb") {
            stroke { moveTo(9f, 18f); lineTo(15f, 18f) }
            stroke { moveTo(10f, 21f); lineTo(14f, 21f) }
            stroke {
                moveTo(9f, 15f); curveTo(7f, 13.5f, 6f, 11.8f, 6f, 9.8f); curveTo(6f, 6.5f, 8.7f, 4f, 12f, 4f)
                curveTo(15.3f, 4f, 18f, 6.5f, 18f, 9.8f); curveTo(18f, 11.8f, 17f, 13.5f, 15f, 15f); close()
            }
            stroke { moveTo(12f, 1f); lineTo(12f, 1.5f) }
            stroke { moveTo(3f, 9.5f); lineTo(2f, 9.5f) }
            stroke { moveTo(21f, 9.5f); lineTo(22f, 9.5f) }
        }
    }
}
