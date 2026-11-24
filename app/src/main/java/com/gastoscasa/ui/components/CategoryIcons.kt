package com.gastoscasa.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.gastoscasa.domain.CategoryIconKeys as K

/** Iconos de línea para categorías y para la lista de la compra. */
object CategoryIcons {
    private fun line(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.stroke(d: PathBuilder.() -> Unit) =
        path(
            stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = d,
        )

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx, cy - r); arcTo(r, r, 0f, true, true, cx - 0.01f, cy - r); close()
    }

    val Cart by lazy {
        line("Cart") {
            stroke { moveTo(3f, 4f); lineTo(5.5f, 4f); lineTo(8f, 15f); lineTo(18f, 15f); lineTo(20f, 7.5f); lineTo(6.4f, 7.5f) }
            stroke { circle(9f, 19f, 1.4f) }
            stroke { circle(17f, 19f, 1.4f) }
        }
    }

    val Food by lazy {
        line("Food") {
            stroke { moveTo(7f, 11f); lineTo(7f, 21f) }
            stroke { moveTo(4.5f, 3f); lineTo(4.5f, 8f); curveTo(4.5f, 10.5f, 9.5f, 10.5f, 9.5f, 8f); lineTo(9.5f, 3f) }
            stroke { moveTo(7f, 3f); lineTo(7f, 8f) }
            stroke { moveTo(17f, 21f); lineTo(17f, 3f); curveTo(14.5f, 5f, 14.5f, 10f, 14.5f, 13f); lineTo(17f, 13f) }
        }
    }

    val Party by lazy {
        line("Party") {
            stroke { moveTo(4f, 20f); lineTo(8.2f, 8.8f); lineTo(15.2f, 15.8f); close() }
            stroke { moveTo(13f, 3.5f); lineTo(13f, 5.5f) }
            stroke { moveTo(19f, 5f); lineTo(17.3f, 6.7f) }
            stroke { moveTo(20.5f, 11f); lineTo(18.5f, 11f) }
            stroke { moveTo(10.5f, 7f); curveTo(11.5f, 5.5f, 10f, 4.5f, 11f, 3f) }
            stroke { moveTo(17f, 13.5f); curveTo(18.5f, 12.5f, 19.5f, 14f, 21f, 13f) }
        }
    }

    val Car by lazy {
        line("Car") {
            stroke { moveTo(3f, 16f); lineTo(3f, 12f); lineTo(5.2f, 7f); lineTo(18.8f, 7f); lineTo(21f, 12f); lineTo(21f, 16f); close() }
            stroke { moveTo(3f, 12f); lineTo(21f, 12f) }
            stroke { circle(7.5f, 16.5f, 1.8f) }
            stroke { circle(16.5f, 16.5f, 1.8f) }
        }
    }

    val Tag by lazy {
        line("Tag") {
            stroke { moveTo(3f, 12f); lineTo(11f, 4f); lineTo(20f, 4f); lineTo(20f, 13f); lineTo(12f, 21f); close() }
            stroke { circle(15.5f, 8.5f, 1.4f) }
        }
    }

    val Coffee by lazy {
        line("Coffee") {
            stroke { moveTo(5f, 9f); lineTo(16f, 9f); lineTo(16f, 15f); curveTo(16f, 18f, 14f, 20f, 10.5f, 20f); curveTo(7f, 20f, 5f, 18f, 5f, 15f); close() }
            stroke { moveTo(16f, 11f); curveTo(20f, 11f, 20f, 16f, 16f, 16f) }
            stroke { moveTo(9f, 3f); curveTo(8f, 5f, 10f, 5f, 9f, 7f) }
            stroke { moveTo(12.5f, 3f); curveTo(11.5f, 5f, 13.5f, 5f, 12.5f, 7f) }
        }
    }

    val Health by lazy {
        line("Health") {
            stroke {
                moveTo(12f, 20f); curveTo(5f, 15f, 3f, 11.5f, 3f, 8.5f); curveTo(3f, 6f, 5f, 4f, 7.5f, 4f)
                curveTo(9.5f, 4f, 11f, 5f, 12f, 6.5f); curveTo(13f, 5f, 14.5f, 4f, 16.5f, 4f)
                curveTo(19f, 4f, 21f, 6f, 21f, 8.5f); curveTo(21f, 11.5f, 19f, 15f, 12f, 20f); close()
            }
            stroke { moveTo(7f, 11f); lineTo(10f, 11f); lineTo(11.5f, 8.5f); lineTo(13f, 13f); lineTo(14.5f, 11f); lineTo(17f, 11f) }
        }
    }

    val Gift by lazy {
        line("Gift") {
            stroke { moveTo(4f, 9.5f); lineTo(20f, 9.5f); lineTo(20f, 13f); lineTo(4f, 13f); close() }
            stroke { moveTo(5.5f, 13f); lineTo(5.5f, 20f); lineTo(18.5f, 20f); lineTo(18.5f, 13f) }
            stroke { moveTo(12f, 9.5f); lineTo(12f, 20f) }
            stroke { moveTo(12f, 9.5f); curveTo(9f, 9.5f, 6.5f, 7.5f, 8f, 5.5f); curveTo(9.5f, 4f, 12f, 6.5f, 12f, 9.5f) }
            stroke { moveTo(12f, 9.5f); curveTo(15f, 9.5f, 17.5f, 7.5f, 16f, 5.5f); curveTo(14.5f, 4f, 12f, 6.5f, 12f, 9.5f) }
        }
    }

    val Bolt by lazy {
        line("Bolt") {
            stroke { moveTo(13.5f, 3f); lineTo(5f, 14f); lineTo(11.5f, 14f); lineTo(10.5f, 21f); lineTo(19f, 10f); lineTo(12.5f, 10f); close() }
        }
    }

    val Pet by lazy {
        line("Pet") {
            stroke { moveTo(8f, 18f); curveTo(8f, 15f, 10f, 13f, 12f, 13f); curveTo(14f, 13f, 16f, 15f, 16f, 18f); curveTo(16f, 20.5f, 8f, 20.5f, 8f, 18f); close() }
            stroke { circle(5.5f, 11f, 1.6f) }
            stroke { circle(9.5f, 7f, 1.6f) }
            stroke { circle(14.5f, 7f, 1.6f) }
            stroke { circle(18.5f, 11f, 1.6f) }
        }
    }

    val Plane by lazy {
        line("Plane") {
            stroke { moveTo(3f, 11f); lineTo(21f, 4f); lineTo(15f, 20f); lineTo(11f, 13f); close() }
            stroke { moveTo(11f, 13f); lineTo(21f, 4f) }
        }
    }

    val Swap by lazy {
        line("Swap") {
            stroke { moveTo(4f, 8f); lineTo(19f, 8f) }
            stroke { moveTo(15.5f, 4.5f); lineTo(19f, 8f); lineTo(15.5f, 11.5f) }
            stroke { moveTo(20f, 16f); lineTo(5f, 16f) }
            stroke { moveTo(8.5f, 12.5f); lineTo(5f, 16f); lineTo(8.5f, 19.5f) }
        }
    }

    val Check by lazy {
        line("Check") { stroke { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7.5f) } }
    }

    /** Vector del icono guardado, o null si es un emoji antiguo. */
    fun vectorFor(icon: String): ImageVector? = when (icon) {
        K.CART -> Cart
        K.HOME -> AppIcons.Home
        K.FOOD -> Food
        K.PARTY -> Party
        K.CAR -> Car
        K.TAG -> Tag
        K.COFFEE -> Coffee
        K.HEALTH -> Health
        K.GIFT -> Gift
        K.BOLT -> Bolt
        K.PET -> Pet
        K.PLANE -> Plane
        K.SWAP -> Swap
        else -> null
    }
}
