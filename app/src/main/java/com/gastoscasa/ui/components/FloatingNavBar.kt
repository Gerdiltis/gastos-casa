package com.gastoscasa.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.gastoscasa.ui.theme.AppColors

data class NavItem(val route: String, val icon: ImageVector, val label: String)

val mainNavItems = listOf(
    NavItem("home", AppIcons.Home, "Inicio"),
    NavItem("groups", AppIcons.Groups, "Grupos"),
    NavItem("stats", AppIcons.Bars, "Estadísticas"),
    NavItem("settings", AppIcons.Gear, "Ajustes"),
)

/** Barra inferior flotante tipo píldora con el elemento activo en negro. */
@Composable
fun FloatingNavBar(current: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(32.dp)
    Row(
        modifier
            .shadow(16.dp, shape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
            .clip(shape)
            .background(Color.White)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        mainNavItems.forEach { item ->
            val active = item.route == current
            Box(
                Modifier
                    .size(width = if (active) 70.dp else 54.dp, height = 58.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (active) AppColors.Ink else Color.Transparent)
                    .clickable { onSelect(item.route) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(item.icon, item.label, tint = if (active) Color.White else AppColors.Ink, modifier = Modifier.size(26.dp))
            }
        }
    }
}
