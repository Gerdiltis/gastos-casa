package com.gastoscasa.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gastoscasa.ui.theme.AppColors

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .shadow(6.dp, shape, ambientColor = Color(0x14000000), spotColor = Color(0x14000000))
            .clip(shape)
            .background(AppColors.Card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun CircleButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.8f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = AppColors.Ink, modifier = Modifier.size(22.dp)) }
}

/** Barra superior centrada como en el diseño: ‹  Título  + */
@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp)) {
            if (onBack != null) CircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", onBack)
        }
        Text(
            title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(44.dp)) { action?.invoke() }
    }
}

/** Pestañas tipo píldora: [ All 8 ]  Active 6   Paused 2 */
@Composable
fun PillTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (active) AppColors.Ink else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (active) Color.White else AppColors.Muted, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun IconBubble(icon: String, color: Color = AppColors.Yellow, size: Dp = 46.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) { CategoryGlyph(icon, size * 0.5f) }
}

/** Icono de categoría: vector propio si es "ic:…", si no el emoji tal cual. */
@Composable
fun CategoryGlyph(icon: String, size: Dp = 18.dp, tint: Color = AppColors.Ink) {
    val vector = CategoryIcons.vectorFor(icon)
    if (vector != null) Icon(vector, null, tint = tint, modifier = Modifier.size(size))
    else Text(icon, fontSize = (size.value * 0.9f).sp)
}

@Composable
fun Avatar(name: String, size: Dp = 44.dp, color: Color = AppColors.Ink) {
    Box(
        Modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase().ifEmpty { "?" },
            color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.4f).sp,
        )
    }
}

/** Chip pequeño de la cabecera: icono + etiqueta + valor. */
@Composable
fun StatChip(emoji: String, label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.7f)).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(AppColors.Soft), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 13.sp)
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.padding(end = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = AppColors.Muted, maxLines = 1)
            Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Tarjeta negra "Smart Tip" del diseño. */
@Composable
fun SmartTipCard(title: String, text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    AppCard(modifier.fillMaxWidth(), onClick = onClick, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(AppColors.Ink),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Bulb, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted)
            }
            if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AppColors.Ink)
        }
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, color = AppColors.Muted, modifier = Modifier.clickable(onClick = onAction))
        }
    }
}

@Composable
fun EmptyState(emoji: String, title: String, text: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 44.sp)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(text, color = AppColors.Muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ErrorBanner(message: String?, modifier: Modifier = Modifier) {
    if (message == null) return
    Text(
        message,
        color = AppColors.Red,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.Red.copy(alpha = 0.1f))
            .padding(12.dp),
    )
}

/** Botón negro principal, ancho completo. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) AppColors.Ink else AppColors.Muted.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) AppColors.Ink else AppColors.Muted, style = MaterialTheme.typography.titleMedium)
    }
}

/** Chip seleccionable para personas / categorías. */
@Composable
fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit, leading: String? = null) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) AppColors.Ink else Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            CategoryGlyph(leading, 18.dp, if (selected) Color.White else AppColors.Ink)
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = if (selected) Color.White else AppColors.Ink, style = MaterialTheme.typography.bodyMedium)
    }
}
