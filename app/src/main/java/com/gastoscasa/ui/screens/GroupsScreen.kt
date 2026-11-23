package com.gastoscasa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gastoscasa.data.GroupRepository
import com.gastoscasa.data.LoadOverview
import com.gastoscasa.ui.Fmt
import com.gastoscasa.ui.components.AppCard
import com.gastoscasa.ui.components.CircleButton
import com.gastoscasa.ui.components.EmptyState
import com.gastoscasa.ui.components.ErrorBanner
import com.gastoscasa.ui.components.IconBubble
import com.gastoscasa.ui.components.PillTabs
import com.gastoscasa.ui.components.SmartTipCard
import com.gastoscasa.ui.components.TopBar
import com.gastoscasa.ui.friendly
import com.gastoscasa.ui.theme.AppColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupsViewModel @Inject constructor(
    load: LoadOverview,
    private val repo: GroupRepository,
) : OverviewViewModel(load) {
    private val _dialogError = MutableStateFlow<String?>(null)
    val dialogError = _dialogError.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    fun createOrJoin(create: Boolean, text: String, onDone: (String) -> Unit) {
        if (text.isBlank()) { _dialogError.value = if (create) "Escribe un nombre" else "Escribe el código"; return }
        viewModelScope.launch {
            _busy.value = true
            _dialogError.value = null
            runCatching { if (create) repo.createGroup(text) else repo.joinGroup(text) }
                .onSuccess { id -> refresh(); onDone(id) }
                .onFailure { _dialogError.value = it.friendly() }
            _busy.value = false
        }
    }

    fun clearDialogError() { _dialogError.value = null }
}

@Composable
fun GroupsScreen(onOpenGroup: (String) -> Unit, vm: GroupsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val dialogError by vm.dialogError.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(0) }
    var showDialog by remember { mutableStateOf(false) }
    val o = state.data

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopBar("Grupos", action = { CircleButton(Icons.Default.Add, "Nuevo grupo", { vm.clearDialogError(); showDialog = true }) })
        PullToRefreshBox(state.refreshing, { vm.refresh(pull = true) }, Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    AppTextField(query, { query = it }, "Buscar grupos…", leading = { Icon(Icons.Default.Search, null, tint = AppColors.Muted) })
                }
                item { ErrorBanner(state.error) }
                if (o == null) {
                    item { Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) { if (state.loading) CircularProgressIndicator(color = AppColors.Ink) } }
                    return@LazyColumn
                }
                val withBalance = o.groups.map { it to o.myBalance(it.id) }
                val pending = withBalance.count { it.second != 0L }
                item {
                    PillTabs(
                        listOf("Todos ${o.groups.size}", "Con deudas $pending", "Al día ${o.groups.size - pending}"),
                        tab, { tab = it },
                    )
                }
                val shown = withBalance
                    .filter { query.isBlank() || it.first.name.contains(query, ignoreCase = true) }
                    .filter { when (tab) { 1 -> it.second != 0L; 2 -> it.second == 0L; else -> true } }
                if (shown.isEmpty()) {
                    item { EmptyState("👥", "No hay grupos aquí", "Pulsa + para crear un grupo o unirte con un código.") }
                }
                items(shown, key = { it.first.id }) { (g, bal) ->
                    AppCard(Modifier.fillMaxWidth(), onClick = { onOpenGroup(g.id) }, padding = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBubble("🏠", AppColors.Teal)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${g.name} • ${g.members.size}", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    g.members.joinToString(", ") { it.name },
                                    style = MaterialTheme.typography.labelMedium, color = AppColors.Muted, maxLines = 1,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    Fmt.signed(bal), fontWeight = FontWeight.SemiBold,
                                    color = when { bal > 0 -> AppColors.Teal; bal < 0 -> AppColors.Red; else -> AppColors.Ink },
                                )
                                Text(
                                    when { bal > 0 -> "te deben"; bal < 0 -> "debes"; else -> "al día" },
                                    style = MaterialTheme.typography.labelSmall, color = AppColors.Muted,
                                )
                            }
                        }
                    }
                }
                item {
                    val owing = withBalance.filter { it.second < 0 }.minByOrNull { it.second }
                    SmartTipCard(
                        "Consejo",
                        if (owing != null) "En ${owing.first.name} debes ${Fmt.money(-owing.second)}. Abre Saldos para liquidar."
                        else "Cuando un grupo esté al día, archiva sus gastos para empezar de cero.",
                        onClick = owing?.let { { onOpenGroup(it.first.id) } },
                    )
                }
            }
        }
    }

    if (showDialog) {
        CreateJoinDialog(
            busy = busy, error = dialogError,
            onDismiss = { showDialog = false },
            onSubmit = { create, text -> vm.createOrJoin(create, text) { id -> showDialog = false; onOpenGroup(id) } },
        )
    }
}

@Composable
private fun CreateJoinDialog(
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (create: Boolean, text: String) -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Card,
        title = { Text(if (tab == 0) "Nuevo grupo" else "Unirse a un grupo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PillTabs(listOf("Crear", "Con código"), tab, { tab = it; text = "" })
                if (tab == 0) AppTextField(text, { text = it }, "Nombre (p. ej. Piso Centro)")
                else AppTextField(text, { text = it.uppercase().take(6) }, "Código de 6 letras")
                ErrorBanner(error)
                if (busy) CircularProgressIndicator(color = AppColors.Ink, modifier = Modifier.height(24.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(tab == 0, text) }, enabled = !busy) {
                Text(if (tab == 0) "Crear" else "Unirme", color = AppColors.Ink, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = AppColors.Muted) } },
    )
}
