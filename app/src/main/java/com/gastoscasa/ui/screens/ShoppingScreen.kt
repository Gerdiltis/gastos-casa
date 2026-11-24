package com.gastoscasa.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gastoscasa.data.AuthRepository
import com.gastoscasa.data.GroupRepository
import com.gastoscasa.data.ShoppingRepository
import com.gastoscasa.domain.Group
import com.gastoscasa.domain.ShoppingItem
import com.gastoscasa.ui.Fmt
import com.gastoscasa.ui.components.AppCard
import com.gastoscasa.ui.components.CategoryIcons
import com.gastoscasa.ui.components.EmptyState
import com.gastoscasa.ui.components.ErrorBanner
import com.gastoscasa.ui.components.SelectChip
import com.gastoscasa.ui.components.TopBar
import com.gastoscasa.ui.friendly
import com.gastoscasa.ui.theme.AppColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShoppingState(
    val loading: Boolean = true,
    val groups: List<Group> = emptyList(),
    val groupId: String? = null,
    val items: List<ShoppingItem> = emptyList(),
    val error: String? = null,
) {
    val group: Group? get() = groups.firstOrNull { it.id == groupId }
}

@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val groupsRepo: GroupRepository,
    private val repo: ShoppingRepository,
    private val auth: AuthRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(ShoppingState())
    val state = _state.asStateFlow()
    private var watch: Job? = null

    val me: String? get() = auth.currentUserId

    fun load() {
        viewModelScope.launch {
            runCatching { groupsRepo.myGroups() }
                .onSuccess { groups ->
                    val keep = (_state.value.groupId ?: savedState.get<String>(KEY)).takeIf { id -> groups.any { it.id == id } }
                    _state.update { it.copy(groups = groups, error = null) }
                    val target = keep ?: groups.firstOrNull()?.id
                    if (target != null) select(target, force = true) else _state.update { it.copy(loading = false) }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.friendly()) } }
        }
    }

    fun select(groupId: String, force: Boolean = false) {
        if (!force && groupId == _state.value.groupId) return
        savedState[KEY] = groupId
        _state.update { it.copy(groupId = groupId, items = if (it.groupId == groupId) it.items else emptyList(), loading = true) }
        reload()
        watch?.cancel()
        watch = viewModelScope.launch { runCatching { repo.observe(groupId) { reload() } } }
    }

    private fun reload() {
        val id = _state.value.groupId ?: return
        viewModelScope.launch {
            runCatching { repo.items(id) }
                .onSuccess { list -> if (_state.value.groupId == id) _state.update { it.copy(items = list, loading = false, error = null) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.friendly()) } }
        }
    }

    fun add(name: String) {
        val id = _state.value.groupId ?: return
        val clean = name.trim().take(80)
        if (clean.isEmpty()) return
        viewModelScope.launch {
            runCatching { repo.add(id, clean) }
                .onSuccess { item ->
                    _state.update { s -> if (s.items.any { it.id == item.id }) s else s.copy(items = listOf(item) + s.items, error = null) }
                }
                .onFailure { e -> _state.update { it.copy(error = e.friendly()) } }
        }
    }

    /** Lo quita de la lista al instante; si falla el borrado, vuelve a aparecer. */
    fun bought(item: ShoppingItem) {
        _state.update { s -> s.copy(items = s.items.filterNot { it.id == item.id }) }
        viewModelScope.launch {
            runCatching { repo.remove(item.id) }.onFailure { e ->
                _state.update { s -> s.copy(items = (s.items + item).sortedByDescending { it.createdAt }, error = e.friendly()) }
            }
        }
    }

    override fun onCleared() { watch?.cancel() }

    private companion object { const val KEY = "shoppingGroup" }
}

@Composable
fun ShoppingScreen(onSeeGroups: () -> Unit, vm: ShoppingViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    val submit = { if (text.isNotBlank()) { vm.add(text); text = "" } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { TopBar("Lista de la compra") }
            item { ErrorBanner(s.error) }
            if (s.groups.isEmpty()) {
                item {
                    if (s.loading) Box(Modifier.fillMaxWidth().padding(60.dp), Alignment.Center) { CircularProgressIndicator(color = AppColors.Ink) }
                    else Column {
                        EmptyState("🛒", "Aún no tienes grupos", "La lista de la compra se comparte con los miembros de un grupo.")
                        com.gastoscasa.ui.components.PrimaryButton("Ir a Grupos", onSeeGroups)
                    }
                }
                return@LazyColumn
            }
            if (s.groups.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.groups, key = { it.id }) { g -> SelectChip(g.name, g.id == s.groupId, { vm.select(g.id) }) }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppTextField(text, { text = it.take(80) }, "Añadir producto…", Modifier.weight(1f), onDone = submit)
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.size(56.dp).clip(CircleShape)
                            .background(if (text.isBlank()) AppColors.Muted.copy(alpha = 0.4f) else AppColors.Ink)
                            .clickable(enabled = text.isNotBlank(), onClick = submit),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Add, "Añadir", tint = Color.White) }
                }
            }
            item {
                Text(
                    "${s.items.size} ${if (s.items.size == 1) "producto" else "productos"} · compartida con ${s.group?.members?.size ?: 0} personas de ${s.group?.name.orEmpty()}",
                    style = MaterialTheme.typography.labelMedium, color = AppColors.Muted,
                )
            }
            if (s.items.isEmpty()) {
                item {
                    if (s.loading) Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) { CircularProgressIndicator(color = AppColors.Ink) }
                    else EmptyState("📝", "Lista vacía", "Añade lo que haga falta. Al marcarlo como comprado, desaparece para todos.")
                }
            }
            items(s.items, key = { it.id }) { item ->
                ShoppingRow(item, s.group?.memberName(item.addedBy) ?: "¿?", Modifier.animateItem()) {
                    vm.bought(item)
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        val r = snackbar.showSnackbar("Comprado: ${item.name}", actionLabel = "Deshacer", duration = SnackbarDuration.Short)
                        if (r == SnackbarResult.ActionPerformed) vm.add(item.name)
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp))
    }
}

@Composable
private fun ShoppingRow(item: ShoppingItem, who: String, modifier: Modifier, onBought: () -> Unit) {
    AppCard(modifier.fillMaxWidth().animateContentSize(), onClick = onBought, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).border(2.dp, AppColors.Ink, CircleShape).clickable(onClick = onBought),
                contentAlignment = Alignment.Center,
            ) { Icon(CategoryIcons.Check, "Marcar como comprado", tint = AppColors.Ink.copy(alpha = 0.15f), modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text("añadió $who · ${Fmt.ago(item.createdAt)}", style = MaterialTheme.typography.labelMedium, color = AppColors.Muted)
            }
        }
    }
}
