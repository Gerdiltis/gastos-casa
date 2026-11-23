package com.gastoscasa.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gastoscasa.data.LoadOverview
import com.gastoscasa.data.Overview
import com.gastoscasa.domain.Expense
import com.gastoscasa.domain.Stats
import com.gastoscasa.domain.StatsScope
import com.gastoscasa.ui.Fmt
import com.gastoscasa.ui.components.AppCard
import com.gastoscasa.ui.components.Avatar
import com.gastoscasa.ui.components.DonutChart
import com.gastoscasa.ui.components.DonutSegment
import com.gastoscasa.ui.components.EmptyState
import com.gastoscasa.ui.components.ErrorBanner
import com.gastoscasa.ui.components.IconBubble
import com.gastoscasa.ui.components.SectionHeader
import com.gastoscasa.ui.components.SmartTipCard
import com.gastoscasa.ui.components.StatChip
import com.gastoscasa.ui.friendly
import com.gastoscasa.ui.theme.AppColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class OverviewState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val data: Overview? = null,
)

/** ViewModel base que carga la foto completa (lo reutilizan Inicio, Grupos y Estadísticas). */
abstract class OverviewViewModel(private val load: LoadOverview) : ViewModel() {
    protected val _state = MutableStateFlow(OverviewState())
    val state = _state.asStateFlow()

    fun refresh(pull: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = pull, loading = it.data == null) }
            runCatching { load() }
                .onSuccess { d -> _state.update { OverviewState(loading = false, data = d) } }
                .onFailure { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.friendly()) } }
        }
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(load: LoadOverview) : OverviewViewModel(load)

/** Agrupa el gasto por nombre de categoría (las categorías son por grupo). */
fun categorySlices(o: Overview, list: List<Expense>, stats: Stats): List<Triple<String, String, Long>> =
    stats.byCategory(list)
        .map { (id, v) -> o.categoryOf(id).let { c -> Triple(c?.name ?: "Sin categoría", c?.color ?: "#B0BEC5", v) } }
        .groupBy { it.first }
        .map { (name, l) -> Triple(name, l.first().second, l.sumOf { it.third }) }
        .sortedByDescending { it.third }

@Composable
fun HomeScreen(onOpenGroup: (String) -> Unit, onSeeGroups: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }
    val o = state.data

    PullToRefreshBox(state.refreshing, { vm.refresh(pull = true) }, Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
        ) {
            item { HomeHeader(o?.myName.orEmpty()) }
            item { ErrorBanner(state.error) }
            if (o == null) {
                item { Box(Modifier.fillMaxWidth().padding(60.dp), Alignment.Center) { if (state.loading) CircularProgressIndicator(color = AppColors.Ink) } }
                return@LazyColumn
            }
            val balances = o.groups.associate { it.id to o.myBalance(it.id) }
            val total = balances.values.sum()
            val owedToMe = balances.values.filter { it > 0 }.sum()
            val iOwe = -balances.values.filter { it < 0 }.sum()

            item {
                Column(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Tu saldo total", color = AppColors.Muted, style = MaterialTheme.typography.titleMedium)
                    Text(Fmt.signed(total), style = MaterialTheme.typography.displayLarge)
                    Text(
                        when { total > 0 -> "Te deben dinero"; total < 0 -> "Debes dinero"; else -> "Estás al día" },
                        color = AppColors.Muted, style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatChip("👥", "Grupos", "${o.groups.size}", Modifier.weight(1f))
                    StatChip("↗", "Te deben", Fmt.money(owedToMe), Modifier.weight(1.2f))
                    StatChip("↘", "Debes", Fmt.money(iOwe), Modifier.weight(1.2f))
                }
            }
            item { SectionHeader("Mis grupos", "Ver todos", onSeeGroups) }
            item {
                if (o.groups.isEmpty()) {
                    SmartTipCard("Empieza aquí", "Crea un grupo o únete con un código desde la pestaña Grupos.", onClick = onSeeGroups)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(o.groups, key = { it.id }) { g ->
                            val bal = balances[g.id] ?: 0
                            AppCard(Modifier.width(220.dp), onClick = { onOpenGroup(g.id) }) {
                                Row(verticalAlignment = Alignment.Top) {
                                    IconBubble("🏠", AppColors.Teal)
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        "${g.members.size} personas", color = Color.White, style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.clip(RoundedCornerShape(50)).background(AppColors.Ink).padding(horizontal = 10.dp, vertical = 5.dp),
                                    )
                                }
                                Spacer(Modifier.height(14.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(g.name, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                }
                                Row {
                                    Text(
                                        "${o.expensesOf(g.id).count { !it.isArchived }} gastos activos",
                                        style = MaterialTheme.typography.labelMedium, color = AppColors.Muted, modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        Fmt.signed(bal), fontWeight = FontWeight.SemiBold,
                                        color = when { bal > 0 -> AppColors.Teal; bal < 0 -> AppColors.Red; else -> AppColors.Ink },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                val month = YearMonth.now()
                val stats = Stats(o.expenses, StatsScope.MY_SHARE, o.me)
                val list = stats.inMonth(month)
                val slices = categorySlices(o, list, stats)
                val totalMonth = stats.total(list)
                Spacer(Modifier.height(20.dp))
                AppCard(Modifier.fillMaxWidth(), padding = 20.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("¿A dónde va tu dinero?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            Fmt.monthLong(month), style = MaterialTheme.typography.labelMedium, color = AppColors.Muted,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(AppColors.Soft).padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    if (slices.isEmpty()) {
                        Text("Aún no tienes gastos este mes.", color = AppColors.Muted)
                    } else {
                        val top = slices.take(3)
                        val rest = slices.drop(3).sumOf { it.third }
                        val shown = top.map { it.first to it.third to AppColors.parse(it.second) } +
                            if (rest > 0) listOf("Otros" to rest to AppColors.Line) else emptyList()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DonutChart(
                                shown.map { DonutSegment(it.first.second.toFloat(), it.second) },
                                Fmt.money(totalMonth), "Tu parte", size = 160.dp, thickness = 28.dp,
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                shown.forEach { (pair, color) ->
                                    Row {
                                        Box(Modifier.width(3.dp).height(40.dp).background(color))
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text("${(pair.second * 100 / totalMonth)}%", fontSize = 22.sp)
                                            Text(pair.first, color = AppColors.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val recent = o.expenses.filter { !it.isArchived }.take(5)
            item { SectionHeader("Recientes") }
            if (recent.isEmpty()) item { EmptyState("🧾", "Sin gastos todavía", "Los gastos de tus grupos aparecerán aquí.") }
            items(recent, key = { it.id }) { e ->
                ExpenseRow(e, o.groupOf(e.groupId)?.name ?: "", o, onClick = { onOpenGroup(e.groupId) })
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun HomeHeader(name: String) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        DonutChart(
            listOf(DonutSegment(60f, AppColors.Yellow), DonutSegment(40f, AppColors.Teal)), "", "",
            size = 26.dp, thickness = 5.dp,
        )
        Spacer(Modifier.width(10.dp))
        Text("GASTOS CASA", fontSize = 18.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
        if (name.isNotBlank()) Avatar(name, 48.dp)
    }
}

/** Fila de gasto con el estilo de las tarjetas de "Subscriptions". */
@Composable
fun ExpenseRow(e: Expense, subtitlePrefix: String, o: Overview, onClick: () -> Unit) {
    val cat = o.categoryOf(e.categoryId)
    val group = o.groupOf(e.groupId)
    AppCard(Modifier.fillMaxWidth(), onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(if (e.isSettlement) "🤝" else cat?.icon ?: "🏷️", cat?.let { AppColors.parse(it.color) } ?: AppColors.Teal)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.description, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(subtitlePrefix.ifBlank { null }, "Pagó ${group?.memberName(e.paidBy) ?: "?"}", Fmt.dateTime(e.createdAt)).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Fmt.money(e.amountCents), fontWeight = FontWeight.SemiBold)
                val mine = e.shareOf(o.me)
                if (mine > 0 && !e.isSettlement) Text("tu parte ${Fmt.money(mine)}", style = MaterialTheme.typography.labelSmall, color = AppColors.Muted)
            }
        }
    }
}

@Composable
fun Dot(color: Color) = Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(color))
