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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.gastoscasa.data.RecurringRepository
import com.gastoscasa.domain.Frequency
import com.gastoscasa.domain.Recurrence
import com.gastoscasa.domain.RecurringExpense
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
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
    // Repetir
    val repeat: Boolean = false,
    val frequency: Frequency = Frequency.MONTHLY,
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val recurring: RecurringExpense? = null,
)

/** Para qué se usa el formulario. */
enum class ExpenseFormMode { NEW, EDIT_EXPENSE, EDIT_RECURRING, CONFIRM_OCCURRENCE }

@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: GroupRepository,
    private val recurringRepo: RecurringRepository,
    auth: AuthRepository,
) : ViewModel() {
    private val groupId: String = checkNotNull(savedState["groupId"])
    /** Si viene, estamos editando ese gasto en vez de crear uno nuevo. */
    val expenseId: String? = savedState["expenseId"]
    val recurringId: String? = savedState["recurringId"]
    val occurrenceDate: LocalDate? = savedState.get<String>("date")?.let(LocalDate::parse)
    val mode: ExpenseFormMode = when {
        expenseId != null -> ExpenseFormMode.EDIT_EXPENSE
        recurringId != null && occurrenceDate != null -> ExpenseFormMode.CONFIRM_OCCURRENCE
        recurringId != null -> ExpenseFormMode.EDIT_RECURRING
        else -> ExpenseFormMode.NEW
    }
    private val me = auth.currentUserId.orEmpty()
    private val _state = MutableStateFlow(AddExpenseState(paidBy = me))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                val g = async { repo.group(groupId) }
                val c = async { repo.categories(groupId) }
                val e = async { expenseId?.let { repo.expense(it) } }
                val r = recurringId?.let { recurringRepo.get(it) }
                if (r != null) _state.update { it.copy(recurring = r) }
                Triple(g.await(), c.await(), e.await())
            }.onSuccess { (g, cats, e) ->
                val r = _state.value.recurring
                _state.update {
                    if (r != null) {
                        it.copy(
                            loading = false, group = g, categories = cats,
                            description = r.description, amount = Money.toInput(r.amountCents),
                            categoryId = r.categoryId, paidBy = r.paidBy, participants = r.participants.toSet(),
                            repeat = true, frequency = r.frequency, startDate = r.startDate, endDate = r.endDate,
                        )
                    } else if (e != null) {
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
    fun setRepeat(v: Boolean) = _state.update { it.copy(repeat = v) }
    fun setFrequency(f: Frequency) = _state.update { it.copy(frequency = f) }
    fun setStart(d: LocalDate) = _state.update { it.copy(startDate = d) }
    fun setEnd(d: LocalDate) = _state.update { it.copy(endDate = d) }
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
            ?: if (s.repeat && mode != ExpenseFormMode.CONFIRM_OCCURRENCE) {
                if (mode == ExpenseFormMode.EDIT_RECURRING) {
                    if (s.endDate == null || s.endDate.isBefore(s.startDate)) "La fecha de fin debe ser posterior al inicio" else null
                } else Recurrence.validate(s.startDate, s.endDate, s.frequency, LocalDate.now())
            } else null
        if (error != null) { _state.update { it.copy(error = error) }; return }
        // Orden estable (el de los miembros) para repartir los céntimos sobrantes.
        val ordered = g.members.map { it.userId }.filter { it in s.participants }
        val splits = SplitCalculator.equalSplit(cents!!, ordered)
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            runCatching {
                when (mode) {
                    ExpenseFormMode.EDIT_EXPENSE ->
                        repo.updateExpense(expenseId!!, s.description, cents, s.categoryId, s.paidBy, splits)
                    ExpenseFormMode.EDIT_RECURRING ->
                        recurringRepo.update(recurringId!!, s.description, cents, s.categoryId, s.paidBy, ordered, s.endDate!!)
                    ExpenseFormMode.CONFIRM_OCCURRENCE ->
                        recurringRepo.resolve(recurringId!!, occurrenceDate!!, accept = true, amountCents = cents)
                    ExpenseFormMode.NEW ->
                        if (s.repeat) recurringRepo.create(
                            groupId, s.description, cents, s.categoryId, s.paidBy, ordered, s.frequency, s.startDate, s.endDate!!,
                        )
                        else repo.addExpense(groupId, s.description, cents, s.categoryId, s.paidBy, splits)
                }
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
        val confirming = vm.mode == ExpenseFormMode.CONFIRM_OCCURRENCE
        TopBar(
            when (vm.mode) {
                ExpenseFormMode.NEW -> "Nuevo gasto"
                ExpenseFormMode.EDIT_EXPENSE -> "Editar gasto"
                ExpenseFormMode.EDIT_RECURRING -> "Editar recurrencia"
                ExpenseFormMode.CONFIRM_OCCURRENCE -> "Apuntar repetición"
            },
            onBack = onBack,
        )
        if (s.loading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = AppColors.Ink) }
            return@Column
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (confirming) {
                val r = s.recurring
                AppCard(Modifier.fillMaxWidth()) {
                    Text(s.description, style = MaterialTheme.typography.titleMedium)
                    if (r != null && vm.occurrenceDate != null) {
                        Text(
                            "Repetición ${r.occurrence + 1}/${r.total} · ${Fmt.date(vm.occurrenceDate)}",
                            color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Importe de esta vez (€)", color = AppColors.Muted)
                    AppTextField(s.amount, vm::setAmount, "0,00", keyboardType = KeyboardType.Decimal)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "El resto (categoría, quién paga y reparto) se toma de la recurrencia.",
                        style = MaterialTheme.typography.labelMedium, color = AppColors.Muted,
                    )
                }
            } else AppCard(Modifier.fillMaxWidth()) {
                Text(if (vm.mode == ExpenseFormMode.EDIT_RECURRING) "Importe de cada vez (€)" else "Importe (€)", color = AppColors.Muted)
                AppTextField(s.amount, vm::setAmount, "0,00", keyboardType = KeyboardType.Decimal)
                Spacer(Modifier.height(8.dp))
                Text("Descripción", color = AppColors.Muted)
                AppTextField(s.description, vm::setDescription, "Compra Mercadona, luz…")
                Spacer(Modifier.height(6.dp))
                Text("📅 Fecha y hora: se guardan automáticamente", style = MaterialTheme.typography.labelMedium, color = AppColors.Muted)
            }

            if (vm.mode == ExpenseFormMode.NEW || vm.mode == ExpenseFormMode.EDIT_RECURRING) RepeatSection(s, vm)

            if (!confirming) {

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
            }
            ErrorBanner(s.error)
            Spacer(Modifier.height(8.dp))
        }
        Box(Modifier.padding(20.dp).navigationBarsPadding()) {
            if (s.saving) Box(Modifier.fillMaxWidth(), Alignment.Center) { CircularProgressIndicator(color = AppColors.Ink) }
            else PrimaryButton(
                when {
                    vm.mode == ExpenseFormMode.EDIT_EXPENSE -> "Guardar cambios"
                    vm.mode == ExpenseFormMode.EDIT_RECURRING -> "Guardar recurrencia"
                    confirming -> "Apuntar gasto"
                    s.repeat -> if (s.startDate == LocalDate.now()) "Guardar y apuntar el primero" else "Programar gasto"
                    else -> "Guardar gasto"
                },
                { vm.save(onBack) },
            )
        }
    }
}


/** Interruptor «Repetir» con frecuencia, inicio y fin. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatSection(s: AddExpenseState, vm: AddExpenseViewModel) {
    val editing = vm.mode == ExpenseFormMode.EDIT_RECURRING
    var picking by remember { mutableStateOf<String?>(null) } // "start" | "end"
    AppCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Repetir", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (editing) "Los cambios se aplican a las próximas veces" else "Te avisaremos para confirmar cada vez",
                    style = MaterialTheme.typography.labelMedium, color = AppColors.Muted,
                )
            }
            if (!editing) Switch(
                checked = s.repeat, onCheckedChange = vm::setRepeat,
                colors = SwitchDefaults.colors(checkedTrackColor = AppColors.Ink, checkedThumbColor = Color.White),
            )
        }
        if (!s.repeat) return@AppCard
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Frequency.entries.forEach { f ->
                SelectChip(f.label, s.frequency == f, { if (!editing) vm.setFrequency(f) })
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DateBox("Empieza", Fmt.date(s.startDate), Modifier.weight(1f), enabled = !editing) { picking = "start" }
            DateBox("Termina", s.endDate?.let(Fmt::date) ?: "Elegir", Modifier.weight(1f)) { picking = "end" }
        }
        val end = s.endDate
        if (end != null && !end.isBefore(s.startDate)) {
            val n = Recurrence.total(s.startDate, end, s.frequency)
            Spacer(Modifier.height(10.dp))
            Text(
                "Se repetirá ${s.frequency.every}, del ${Fmt.date(s.startDate)} al ${Fmt.date(end)} ($n ${if (n == 1) "vez" else "veces"}).",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    picking?.let { which ->
        val initial = if (which == "start") s.startDate else s.endDate ?: s.startDate.plusYears(1)
        val today = LocalDate.now()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return if (which == "start") !d.isBefore(today) else !d.isBefore(s.startDate)
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        if (which == "start") vm.setStart(d) else vm.setEnd(d)
                    }
                    picking = null
                }) { Text("Aceptar", color = AppColors.Ink) }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancelar", color = AppColors.Muted) } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun DateBox(label: String, value: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(AppColors.Soft)
            .clickable(enabled = enabled, onClick = onClick).padding(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = AppColors.Muted)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = if (enabled) AppColors.Ink else AppColors.Muted)
    }
}
