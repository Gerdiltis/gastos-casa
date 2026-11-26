package com.gastoscasa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gastoscasa.data.AuthRepository
import com.gastoscasa.data.GroupRepository
import com.gastoscasa.domain.Category
import com.gastoscasa.domain.Group
import com.gastoscasa.domain.Money
import com.gastoscasa.domain.SplitCalculator
import com.gastoscasa.ui.Fmt
import com.gastoscasa.ui.components.AppCard
import com.gastoscasa.ui.components.ErrorBanner
import com.gastoscasa.ui.components.PrimaryButton
import com.gastoscasa.ui.components.SelectChip
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

data class AddExpenseState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val group: Group? = null,
    val categories: List<Category> = emptyList(),
    val description: String = "",
    val amount: String = "",
    val categoryId: String? = null,
    val paidBy: String = "",
    val participants: Set<String> = emptySet(),
)

@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: GroupRepository,
    auth: AuthRepository,
) : ViewModel() {
    private val groupId: String = checkNotNull(savedState["groupId"])
    /** Si viene, estamos editando ese gasto en vez de crear uno nuevo. */
    val expenseId: String? = savedState["expenseId"]
    private val me = auth.currentUserId.orEmpty()
    private val _state = MutableStateFlow(AddExpenseState(paidBy = me))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                val g = async { repo.group(groupId) }
                val c = async { repo.categories(groupId) }
                val e = async { expenseId?.let { repo.expense(it) } }
                Triple(g.await(), c.await(), e.await())
            }.onSuccess { (g, cats, e) ->
                _state.update {
                    if (e != null) {
                        it.copy(
                            loading = false, group = g, categories = cats,
                            description = e.description, amount = Money.toInput(e.amountCents),
                            categoryId = e.categoryId, paidBy = e.paidBy,
                            participants = e.splits.filter { s -> s.shareCents > 0 }.map { s -> s.userId }.toSet(),
                        )
                    } else {
                        it.copy(
                            loading = false, group = g, categories = cats,
                            // Por defecto: pagas tú y se reparte entre todos.
                            participants = g?.members?.map { m -> m.userId }?.toSet().orEmpty(),
                        )
                    }
                }
            }.onFailure { e -> _state.update { it.copy(loading = false, error = e.friendly()) } }
        }
    }

    fun setDescription(v: String) = _state.update { it.copy(description = v.take(120)) }
    fun setAmount(v: String) = _state.update { it.copy(amount = v.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(12)) }
    fun setCategory(id: String) = _state.update { it.copy(categoryId = if (it.categoryId == id) null else id) }
    fun setPaidBy(id: String) = _state.update { it.copy(paidBy = id) }
    fun toggleParticipant(id: String) = _state.update {
        it.copy(participants = if (id in it.participants) it.participants - id else it.participants + id)
    }
    fun selectAll() = _state.update { s ->
        val all = s.group?.members?.map { it.userId }?.toSet().orEmpty()
        s.copy(participants = if (s.participants == all) setOf(me) else all)
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        val g = s.group ?: return
        val cents = Money.parse(s.amount)
        val error = when {
            s.description.isBlank() -> "Escribe una descripción"
            cents == null -> "Importe no válido"
            s.participants.isEmpty() -> "Elige al menos una persona"
            else -> null
        }
        if (error != null) { _state.update { it.copy(error = error) }; return }
        // Orden estable (el de los miembros) para repartir los céntimos sobrantes.
        val ordered = g.members.map { it.userId }.filter { it in s.participants }
        val splits = SplitCalculator.equalSplit(cents!!, ordered)
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            runCatching {
                if (expenseId != null) repo.updateExpense(expenseId, s.description, cents, s.categoryId, s.paidBy, splits)
                else repo.addExpense(groupId, s.description, cents, s.categoryId, s.paidBy, splits)
            }
                .onSuccess { onDone() }
                .onFailure { e -> _state.update { it.copy(saving = false, error = e.friendly()) } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddExpenseScreen(onBack: () -> Unit, vm: AddExpenseViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val g = s.group

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        TopBar(if (vm.expenseId != null) "Editar gasto" else "Nuevo gasto", onBack = onBack)
        if (s.loading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = AppColors.Ink) }
            return@Column
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppCard(Modifier.fillMaxWidth()) {
                Text("Importe (€)", color = AppColors.Muted)
                AppTextField(s.amount, vm::setAmount, "0,00", keyboardType = KeyboardType.Decimal)
                Spacer(Modifier.height(8.dp))
                Text("Descripción", color = AppColors.Muted)
                AppTextField(s.description, vm::setDescription, "Compra Mercadona, luz…")
                Spacer(Modifier.height(6.dp))
                Text("📅 Fecha y hora: se guardan automáticamente", style = MaterialTheme.typography.labelMedium, color = AppColors.Muted)
            }

            Text("Categoría", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                s.categories.forEach { c -> SelectChip(c.name, s.categoryId == c.id, { vm.setCategory(c.id) }, leading = c.icon) }
            }
            if (s.categories.isEmpty()) Text("Este grupo no tiene categorías (créalas en la pestaña Categ.)", color = AppColors.Muted)

            if (g != null) {
                Text("Pagado por", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    g.members.forEach { m -> SelectChip(m.name, s.paidBy == m.userId, { vm.setPaidBy(m.userId) }) }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dividir entre", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    SelectChip(if (s.participants.size == g.members.size) "Solo yo" else "Todos", false, vm::selectAll)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    g.members.forEach { m -> SelectChip(m.name, m.userId in s.participants, { vm.toggleParticipant(m.userId) }, leading = if (m.userId in s.participants) "✓" else null) }
                }
                val cents = Money.parse(s.amount)
                if (cents != null && s.participants.isNotEmpty()) {
                    val ordered = g.members.map { it.userId }.filter { it in s.participants }
                    val split = SplitCalculator.equalSplit(cents, ordered)
                    AppCard(Modifier.fillMaxWidth(), padding = 14.dp) {
                        Text("Reparto", style = MaterialTheme.typography.titleMedium)
                        split.forEach { sp ->
                            Row {
                                Text(g.memberName(sp.userId), Modifier.weight(1f))
                                Text(Fmt.money(sp.shareCents))
                            }
                        }
                    }
                }
            }
            ErrorBanner(s.error)
            Spacer(Modifier.height(8.dp))
        }
        Box(Modifier.padding(20.dp).navigationBarsPadding()) {
            if (s.saving) Box(Modifier.fillMaxWidth(), Alignment.Center) { CircularProgressIndicator(color = AppColors.Ink) }
            else PrimaryButton(if (vm.expenseId != null) "Guardar cambios" else "Guardar gasto", { vm.save(onBack) })
        }
    }
}
