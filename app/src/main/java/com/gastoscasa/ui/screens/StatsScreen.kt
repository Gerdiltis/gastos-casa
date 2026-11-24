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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gastoscasa.data.LoadOverview
import com.gastoscasa.domain.Stats
import com.gastoscasa.domain.StatsScope
import com.gastoscasa.ui.Fmt
import com.gastoscasa.ui.components.AppCard
import com.gastoscasa.ui.components.AreaChart
import com.gastoscasa.ui.components.CircleButton
import com.gastoscasa.ui.components.EmptyState
import com.gastoscasa.ui.components.ErrorBanner
import com.gastoscasa.ui.components.IconBubble
import com.gastoscasa.ui.components.PercentRing
import com.gastoscasa.ui.components.PillTabs
import com.gastoscasa.ui.components.SectionHeader
import com.gastoscasa.ui.components.SelectChip
import com.gastoscasa.ui.components.TopBar
import com.gastoscasa.ui.theme.AppColors
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(load: LoadOverview) : OverviewViewModel(load)

@Composable
fun StatsScreen(vm: StatsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }
    var groupFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var scopeTab by rememberSaveable { mutableIntStateOf(0) }
    var periodTab by rememberSaveable { mutableIntStateOf(0) }
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    val o = state.data

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopBar("Estadísticas")
        PullToRefreshBox(state.refreshing, { vm.refresh(pull = true) }, Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { ErrorBanner(state.error) }
                if (o == null) {
                    item { Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) { if (state.loading) CircularProgressIndicator(color = AppColors.Ink) } }
                    return@LazyColumn
                }
                // Filtros
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { SelectChip("Todos los grupos", groupFilter == null, { groupFilter = null }) }
                        items(o.groups, key = { it.id }) { g -> SelectChip(g.name, groupFilter == g.id, { groupFilter = g.id }) }
                    }
                }
                item { AppCard(Modifier.fillMaxWidth(), padding = 6.dp) { PillTabs(listOf("Mi parte", "Total grupo"), scopeTab, { scopeTab = it }) } }
                item { AppCard(Modifier.fillMaxWidth(), padding = 6.dp) { PillTabs(listOf("Mes", "Año"), periodTab, { periodTab = it; monthOffset = 0 }) } }

                val expenses = if (groupFilter == null) o.expenses else o.expensesOf(groupFilter!!)
                val stats = Stats(expenses, if (scopeTab == 0) StatsScope.MY_SHARE else StatsScope.GROUP_TOTAL, o.me)
                val byMonth = periodTab == 0
                val now = YearMonth.now()
                val month = if (byMonth) now.plusMonths(monthOffset.toLong()) else now
                val year = now.year + if (byMonth) 0 else monthOffset

                val periodList = if (byMonth) stats.inMonth(month) else stats.inYear(year)
                val prevList = if (byMonth) stats.inMonth(month.minusMonths(1)) else stats.inYear(year - 1)
                val total = stats.total(periodList)
                val prev = stats.total(prevList)

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircleButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Anterior", { monthOffset-- })
                        Text(
                            if (byMonth) Fmt.monthLong(month) else "$year",
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        CircleButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Siguiente", { if (monthOffset < 0) monthOffset++ })
                    }
                }

                item {
                    val series = if (byMonth) stats.lastMonths(month, 6) else stats.monthsOfYear(year)
                    val highlight = if (byMonth) series.lastIndex else if (year == now.year) now.monthValue - 1 else series.indexOfMax()
                    AppCard(Modifier.fillMaxWidth(), padding = 20.dp) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text("Gasto del periodo", style = MaterialTheme.typography.titleMedium, color = AppColors.Muted)
                                Text(Fmt.money(total), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                            }
                            if (prev > 0) {
                                val pct = ((total - prev) * 100 / prev).toInt()
                                Text(
                                    "${if (pct >= 0) "↑" else "↓"} ${kotlin.math.abs(pct)}% vs ${if (byMonth) "mes ant." else "año ant."}",
                                    color = Color.White, style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(AppColors.Teal).padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        AreaChart(
                            series.map { it.second },
                            series.mapIndexed { i, (ym, _) -> if (byMonth || i % 2 == 0) Fmt.monthShort(ym) else "" },
                            highlight,
                        )
                    }
                }

                item { SectionHeader("Desglose por categoría") }
                val slices = categorySlices(o, periodList, stats)
                if (slices.isEmpty()) item { EmptyState("📊", "Sin gastos en este periodo", "Cambia el periodo o el grupo.") }
                items(slices, key = { it.first }) { (name, color, value) ->
                    val cat = o.categories.firstOrNull { it.name == name }
                    AppCard(Modifier.fillMaxWidth(), padding = 16.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBubble(cat?.icon ?: com.gastoscasa.domain.CategoryIconKeys.TAG, AppColors.parse(color), 50.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, color = AppColors.Muted)
                                Text(Fmt.money(value), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            }
                            PercentRing(if (total > 0) (value * 100 / total).toInt() else 0, AppColors.parse(color).darken())
                        }
                    }
                }

                item { SectionHeader("Por año") }
                val years = stats.byYear()
                if (years.isEmpty()) item { Text("Aún no hay datos.", color = AppColors.Muted) }
                items(years, key = { it.first }) { (y, v) ->
                    AppCard(Modifier.fillMaxWidth(), padding = 14.dp) {
                        Row {
                            Text("$y", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Text(Fmt.money(v), fontWeight = FontWeight.SemiBold)
                        }
                        val months = stats.monthsOfYear(y).filter { it.second > 0 }
                        Text(
                            months.joinToString("  ·  ") { "${Fmt.monthShort(it.first)} ${Fmt.money(it.second)}" },
                            style = MaterialTheme.typography.labelMedium, color = AppColors.Muted,
                        )
                    }
                }
            }
        }
    }
}

private fun List<Pair<YearMonth, Long>>.indexOfMax(): Int =
    withIndex().maxByOrNull { it.value.second }?.takeIf { it.value.second > 0 }?.index ?: -1

/** Oscurece un poco los colores claros (amarillo) para que el texto del anillo se lea. */
private fun Color.darken(): Color = Color(red * 0.85f, green * 0.8f, blue * 0.75f, alpha)
