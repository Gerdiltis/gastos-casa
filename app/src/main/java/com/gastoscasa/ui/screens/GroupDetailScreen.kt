package com.gastoscasa.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gastoscasa.data.AuthRepository
import com.gastoscasa.data.GroupRepository
import com.gastoscasa.data.Overview
import com.gastoscasa.domain.Balances
import com.gastoscasa.domain.Expense
import com.gastoscasa.domain.Split
import com.gastoscasa.domain.Transfer
import com.gastoscasa.ui.Fmt
import com.gastoscasa.ui.components.AppCard
import com.gastoscasa.ui.components.Avatar
import com.gastoscasa.ui.components.CircleButton
import com.gastoscasa.ui.components.EmptyState
import com.gastoscasa.ui.components.ErrorBanner
import com.gastoscasa.ui.components.IconBubble
import com.gastoscasa.ui.components.PillTabs
import com.gastoscasa.ui.components.PrimaryButton
import com.gastoscasa.ui.components.SecondaryButton
import com.gastoscasa.ui.components.SectionHeader
import com.gastoscasa.ui.components.SmartTipCard
import com.gastoscasa.ui.components.TopBar
import com.gastoscasa.ui.friendly
import com.gastoscasa.ui.theme.AppColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupDetailState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    /** Vista del grupo con el mismo modelo que la pantalla de inicio. */
    val data: Overview? = null,
) {
    val group get() = data?.groups?.firstOrNull()
    val balances: Map<String, Long>
        get() = group?.let { g -> Balances.compute(data!!.expenses, g.members.map { it.userId }) } ?: emptyMap()
    val transfers: List<Transfer> get() = Balances.settleUp(balances)
}

@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: GroupRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    val groupId: String = checkNotNull(savedState["groupId"])
    private val _state = MutableStateFlow(GroupDetailState())
    val state = _state.asStateFlow()

    init {
        // Tiempo real: si otro miembro añade o borra un gasto, recargamos.
        viewModelScope.launch { runCatching { repo.observeExpenses(groupId) { refresh() } } }
    }

    fun refresh(pull: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = pull) }
            runCatching {
                val group = async { repo.group(groupId) }
                val expenses = async { repo.expenses(groupId) }
                val categories = async { repo.categories(groupId) }
                val g = group.await() ?: error("Ya no perteneces a este grupo")
                Overview(auth.currentUserId.orEmpty(), "", listOf(g), expenses.await(), categories.await())
            }.onSuccess { d -> _state.update { it.copy(loading = false, refreshing = false, data = d, error = null) } }
                .onFailure { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.friendly()) } }
        }
    }

    private fun action(success: String? = null, after: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            runCatching { block() }
                .onSuccess { _state.update { it.copy(busy = false, message = success) }; refresh(); after() }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.friendly()) } }
        }
    }

    fun deleteExpense(e: Expense) = action("Gasto eliminado") { repo.deleteExpense(e.id) }

    fun recordPayment(t: Transfer) = action("Pago registrado") {
        val g = state.value.group ?: return@action
        repo.addExpense(
            groupId, "Pago de ${g.memberName(t.from)} a ${g.memberName(t.to)}", t.amountCents,
            categoryId = null, paidBy = t.from, splits = listOf(Split(t.to, t.amountCents)), isSettlement = true,
        )
    }

    fun archive() = action("Gastos archivados. ¡Empezáis de cero!") { repo.archiveSettled(groupId) }

    fun addCategory(name: String, icon: String, color: String) {
        if (name.isBlank()) { _state.update { it.copy(error = "Escribe un nombre de categoría") }; return }
        action("Categoría creada") { repo.addCategory(groupId, name, icon.ifBlank { "🏷️" }, color) }
    }

    fun deleteCategory(id: String) = action("Categoría eliminada") { repo.deleteCategory(id) }

    fun rename(name: String) {
        if (name.isBlank()) return
        action("Nombre actualizado") { repo.renameGroup(groupId, name) }
    }

    fun leave(onDone: () -> Unit) = action(after = onDone) { repo.leaveGroup(groupId, auth.currentUserId.orEmpty()) }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

@Composable
fun GroupDetailScreen(
    onBack: () -> Unit,
    onAddExpense: (String) -> Unit,
    vm: GroupDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(state.message) { if (state.message != null) { kotlinx.coroutines.delay(2500); vm.consumeMessage() } }
    val o = state.data
    val g = state.group

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopBar(
            g?.name ?: "Grupo", onBack = onBack,
            action = { if (g != null) CircleButton(Icons.Default.Add, "Nuevo gasto", { onAddExpense(vm.groupId) }) },
        )
        Box(Modifier.padding(horizontal = 20.dp)) {
            PillTabs(listOf("Gastos", "Saldos", "Categ.", "Info"), tab, { tab = it })
        }
        PullToRefreshBox(state.refreshing, { vm.refresh(pull = true) }, Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { ErrorBanner(state.error) }
                state.message?.let { msg ->
                    item {
                        Text(
                            msg, color = AppColors.Ink,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(AppColors.Teal.copy(alpha = 0.18f)).padding(12.dp),
                        )
                    }
                }
                if (o == null || g == null) {
                    item { Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) { if (state.loading) CircularProgressIndicator(color = AppColors.Ink) } }
                    return@LazyColumn
                }
                when (tab) {
                    0 -> expensesTab(o, state, vm)
                    1 -> balancesTab(o, state, vm)
                    2 -> categoriesTab(o, state, vm)
                    else -> infoTab(o, state, vm, onBack)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Gastos

private fun LazyListScope.expensesTab(o: Overview, state: GroupDetailState, vm: GroupDetailViewModel) {
    item { ExpensesTabContent(o, state, vm) }
}

@Composable
private fun ExpensesTabContent(o: Overview, state: GroupDetailState, vm: GroupDetailViewModel) {
    var archived by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Expense?>(null) }
    val active = o.expenses.filter { !it.isArchived }
    val old = o.expenses.filter { it.isArchived }
    val myBalance = state.balances[o.me] ?: 0

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppTextField(query, { query = it }, "Buscar gastos…", leading = { Icon(Icons.Default.Search, null, tint = AppColors.Muted) })
        PillTabs(listOf("Activos ${active.size}", "Archivados ${old.size}"), archived, { archived = it })
        CategoryFilterRow(o, categoryFilter) { categoryFilter = it }
        val list = (if (archived == 0) active else old)
            .filter { categoryFilter == null || it.categoryId == categoryFilter }
            .filter {
                query.isBlank() || it.description.contains(query, true) ||
                    (o.categoryOf(it.categoryId)?.name?.contains(query, true) ?: false)
            }
        if (list.isEmpty()) {
            EmptyState(
                if (archived == 0) "🧾" else "🗄️",
                if (archived == 0) "No hay gastos" else "Nada archivado",
                if (archived == 0) "Pulsa + arriba para añadir el primero." else "Los gastos saldados que archives aparecerán aquí.",
            )
        }
        list.forEach { e -> ExpenseRow(e, o.categoryOf(e.categoryId)?.name ?: "", o, onClick = { selected = e }) }
        if (archived == 0) {
            SmartTipCard(
                "Tu situación",
                when {
                    myBalance > 0 -> "Te deben ${Fmt.money(myBalance)} en este grupo."
                    myBalance < 0 -> "Debes ${Fmt.money(-myBalance)} en este grupo. Mira la pestaña Saldos."
                    else -> "Estás al día en este grupo. 🎉"
                },
            )
        }
    }

    selected?.let { e ->
        val g = o.groups.first()
        AlertDialog(
            onDismissRequest = { selected = null },
            containerColor = AppColors.Card,
            title = { Text(e.description) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${Fmt.money(e.amountCents)} · ${o.categoryOf(e.categoryId)?.let { "${it.icon} ${it.name}" } ?: if (e.isSettlement) "Liquidación" else "Sin categoría"}")
                    Text("Pagó ${g.memberName(e.paidBy)} · ${Fmt.dateTime(e.createdAt)}", color = AppColors.Muted)
                    Text("Añadido por ${g.memberName(e.createdBy)}", color = AppColors.Muted)
                    Spacer(Modifier.height(6.dp))
                    Text(if (e.isSettlement) "Recibe:" else "Reparto:", fontWeight = FontWeight.SemiBold)
                    e.splits.forEach { s -> Text("• ${g.memberName(s.userId)}: ${Fmt.money(s.shareCents)}") }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Cerrar", color = AppColors.Ink) } },
            dismissButton = {
                if (!e.isArchived) TextButton(onClick = { vm.deleteExpense(e); selected = null }) { Text("Eliminar", color = AppColors.Red) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryFilterRow(o: Overview, selected: String?, onSelect: (String?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        com.gastoscasa.ui.components.SelectChip("Todas", selected == null, { onSelect(null) })
        o.categories.forEach { c ->
            com.gastoscasa.ui.components.SelectChip(c.name, selected == c.id, { onSelect(if (selected == c.id) null else c.id) }, leading = c.icon)
        }
    }
}

// ---------------------------------------------------------------- Saldos

private fun LazyListScope.balancesTab(o: Overview, state: GroupDetailState, vm: GroupDetailViewModel) {
    val g = o.groups.first()
    val balances = state.balances
    item { SectionHeader("Saldos") }
    items(g.members, key = { it.userId }) { m ->
        val b = balances[m.userId] ?: 0
        AppCard(Modifier.fillMaxWidth(), padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(m.name, 40.dp, if (m.userId == o.me) AppColors.Ink else AppColors.Muted)
                Spacer(Modifier.width(12.dp))
                Text(if (m.userId == o.me) "${m.name} (tú)" else m.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        Fmt.signed(b), fontWeight = FontWeight.SemiBold,
                        color = when { b > 0 -> AppColors.Teal; b < 0 -> AppColors.Red; else -> AppColors.Ink },
                    )
                    Text(when { b > 0 -> "le deben"; b < 0 -> "debe"; else -> "al día" }, style = MaterialTheme.typography.labelSmall, color = AppColors.Muted)
                }
            }
        }
    }
    item { SectionHeader("Pagos para saldar") }
    if (state.transfers.isEmpty()) {
        item { EmptyState("🎉", "Todo saldado", "Nadie debe nada en este grupo.") }
    }
    items(state.transfers, key = { "${it.from}-${it.to}" }) { t ->
        AppCard(Modifier.fillMaxWidth(), padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble("🤝", AppColors.Teal, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${g.memberName(t.from)} → ${g.memberName(t.to)}", style = MaterialTheme.typography.titleMedium)
                    Text(Fmt.money(t.amountCents), color = AppColors.Muted)
                }
                Text(
                    "Registrar", color = Color.White, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(AppColors.Ink)
                        .clickable(enabled = !state.busy) { vm.recordPayment(t) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
    item { ArchiveSection(o, state, vm) }
}

@Composable
private fun ArchiveSection(o: Overview, state: GroupDetailState, vm: GroupDetailViewModel) {
    var confirm by remember { mutableStateOf(false) }
    val activeCount = o.expenses.count { !it.isArchived }
    val settled = Balances.isSettled(state.balances)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Archivar")
        Text(
            if (settled) "Las deudas están saldadas. Puedes archivar los $activeCount gastos activos y empezar de cero sin borrar el grupo."
            else "Podrás archivar cuando todos los saldos estén a 0 (registra los pagos de arriba).",
            color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium,
        )
        PrimaryButton("Archivar gastos saldados", { confirm = true }, enabled = settled && activeCount > 0 && !state.busy)
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = AppColors.Card,
            title = { Text("¿Archivar $activeCount gastos?") },
            text = { Text("Dejarán de aparecer en la lista y en los saldos, pero seguirán en Archivados y en Estadísticas.") },
            confirmButton = { TextButton(onClick = { confirm = false; vm.archive() }) { Text("Archivar", color = AppColors.Ink, fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar", color = AppColors.Muted) } },
        )
    }
}

// ---------------------------------------------------------------- Categorías

private fun LazyListScope.categoriesTab(o: Overview, state: GroupDetailState, vm: GroupDetailViewModel) {
    item { NewCategoryCard(state.busy, vm) }
    item { SectionHeader("Categorías del grupo") }
    items(o.categories, key = { it.id }) { c ->
        val uses = o.expenses.count { it.categoryId == c.id }
        AppCard(Modifier.fillMaxWidth(), padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(c.icon, AppColors.parse(c.color), 42.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium)
                    Text("$uses gastos", style = MaterialTheme.typography.labelMedium, color = AppColors.Muted)
                }
                IconButton(onClick = { vm.deleteCategory(c.id) }, enabled = !state.busy) {
                    Icon(Icons.Default.Delete, "Eliminar", tint = AppColors.Muted)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewCategoryCard(busy: Boolean, vm: GroupDetailViewModel) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(AppColors.palette.first()) }
    AppCard(Modifier.fillMaxWidth()) {
        Text("Nueva categoría", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTextField(icon, { icon = it.take(4) }, "😀", Modifier.width(84.dp))
            AppTextField(name, { name = it.take(40) }, "Nombre", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppColors.palette.forEach { hex ->
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(AppColors.parse(hex))
                        .clickable { color = hex }
                        .then(if (hex == color) Modifier.padding(9.dp).clip(CircleShape).background(Color.White) else Modifier),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Añadir categoría", { vm.addCategory(name, icon, color); name = ""; icon = "" }, enabled = !busy)
    }
}

// ---------------------------------------------------------------- Info

private fun LazyListScope.infoTab(o: Overview, state: GroupDetailState, vm: GroupDetailViewModel, onLeft: () -> Unit) {
    item { InfoTabContent(o, state, vm, onLeft) }
}

@Composable
private fun InfoTabContent(o: Overview, state: GroupDetailState, vm: GroupDetailViewModel, onLeft: () -> Unit) {
    val g = o.groups.first()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var newName by remember(g.name) { mutableStateOf(g.name) }
    var confirmLeave by remember { mutableStateOf(false) }
    val shareText = "Únete a mi grupo \"${g.name}\" en Gastos Casa con el código ${g.inviteCode}"

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppCard(Modifier.fillMaxWidth(), padding = 20.dp) {
            Text("Código de invitación", color = AppColors.Muted)
            Text(g.inviteCode, fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Copiar", { clipboard.setText(AnnotatedString(g.inviteCode)) }, Modifier.weight(1f).background(AppColors.Soft, RoundedCornerShape(50)))
                PrimaryButton("Compartir", {
                    context.startActivity(
                        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText), "Invitar")
                    )
                }, Modifier.weight(1f))
            }
        }
        SectionHeader("Miembros (${g.members.size})")
        g.members.forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(m.name, 36.dp)
                Spacer(Modifier.width(10.dp))
                Text(if (m.userId == o.me) "${m.name} (tú)" else m.name)
                if (m.userId == g.createdBy) Text("  · creador", color = AppColors.Muted, style = MaterialTheme.typography.labelMedium)
            }
        }
        SectionHeader("Nombre del grupo")
        AppTextField(newName, { newName = it.take(60) }, "Nombre")
        SecondaryButton("Guardar nombre", { vm.rename(newName) }, enabled = !state.busy && newName != g.name)
        Spacer(Modifier.height(8.dp))
        Text(
            "Salir del grupo", color = AppColors.Red,
            modifier = Modifier.fillMaxWidth().clickable { confirmLeave = true }.padding(12.dp),
        )
    }
    if (confirmLeave) {
        val myBalance = state.balances[o.me] ?: 0
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = AppColors.Card,
            title = { Text("¿Salir de ${g.name}?") },
            text = {
                Text(
                    if (myBalance != 0L) "Tienes un saldo pendiente de ${Fmt.signed(myBalance)}. Sáldalo antes de salir."
                    else "Dejarás de ver el grupo. Podrás volver con el código."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; vm.leave(onLeft) }, enabled = myBalance == 0L) {
                    Text("Salir", color = AppColors.Red)
                }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancelar", color = AppColors.Muted) } },
        )
    }
}
