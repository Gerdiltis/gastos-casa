package com.gastoscasa.domain

/**
 * Iconos de categoría dibujados por la app (no dependen de la fuente de emojis del móvil).
 * Se guardan en `categories.icon` como "ic:<nombre>". Cualquier otro texto se trata como emoji antiguo.
 */
object CategoryIconKeys {
    const val PREFIX = "ic:"
    const val CART = "ic:cart"
    const val HOME = "ic:home"
    const val FOOD = "ic:food"
    const val PARTY = "ic:party"
    const val CAR = "ic:car"
    const val TAG = "ic:tag"
    const val COFFEE = "ic:coffee"
    const val HEALTH = "ic:health"
    const val GIFT = "ic:gift"
    const val BOLT = "ic:bolt"
    const val PET = "ic:pet"
    const val PLANE = "ic:plane"
    /** Solo para liquidaciones; no aparece en el selector. */
    const val SWAP = "ic:swap"

    /** Los que se pueden elegir al crear una categoría, en orden. */
    val selectable = listOf(CART, HOME, FOOD, PARTY, CAR, TAG, COFFEE, HEALTH, GIFT, BOLT, PET, PLANE)

    fun isBuiltIn(icon: String): Boolean = icon in selectable || icon == SWAP

    /** Valor a guardar: si está vacío o no es válido, la etiqueta genérica. */
    fun normalize(icon: String): String = if (isBuiltIn(icon)) icon else icon.trim().ifEmpty { TAG }
}
