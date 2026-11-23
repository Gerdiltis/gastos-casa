package com.gastoscasa.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gastoscasa.BuildConfig
import com.gastoscasa.data.AppUpdate
import com.gastoscasa.data.AuthRepository
import com.gastoscasa.data.UpdateRepository
import com.gastoscasa.ui.components.AppCard
import com.gastoscasa.ui.components.Avatar
import com.gastoscasa.ui.components.ErrorBanner
import com.gastoscasa.ui.components.PrimaryButton
import com.gastoscasa.ui.components.SecondaryButton
import com.gastoscasa.ui.components.SectionHeader
import com.gastoscasa.ui.components.TopBar
import com.gastoscasa.ui.friendly
import com.gastoscasa.ui.theme.AppColors
import com.gastoscasa.update.ApkInstaller
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ------------------------------------------------------------ Actualizaciones

data class UpdateState(
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val update: AppUpdate? = null,
    val upToDate: Boolean = false,
    val showDialog: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val repo: UpdateRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(UpdateState())
    val state = _state.asStateFlow()
    private var autoChecked = false

    val isConfigured get() = repo.isConfigured

    /** Comprobación silenciosa al abrir la app: solo muestra algo si hay versión nueva. */
    fun autoCheck() {
        if (autoChecked) return
        autoChecked = true
        check(silent = true)
    }

    fun check(silent: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(checking = true, error = null, upToDate = false) }
            runCatching { repo.checkForUpdate() }
                .onSuccess { u -> _state.update { it.copy(checking = false, update = u, upToDate = u == null, showDialog = u != null) } }
                .onFailure { e -> _state.update { it.copy(checking = false, error = if (silent) null else e.friendly()) } }
        }
    }

    fun dismiss() = _state.update { it.copy(showDialog = false) }

    fun downloadAndInstall() {
        val u = _state.value.update ?: return
        if (!ApkInstaller.canInstall(context)) {
            _state.update { it.copy(error = "Permite \"Instalar apps desconocidas\" para Gastos Casa y vuelve a pulsar Actualizar.") }
            ApkInstaller.openInstallPermissionSettings(context)
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(downloading = true, error = null) }
            runCatching { repo.download(u, ApkInstaller.updateFile(context)) }
                .onSuccess { f -> _state.update { it.copy(downloading = false, showDialog = false) }; ApkInstaller.install(context, f) }
                .onFailure { e -> _state.update { it.copy(downloading = false, error = e.friendly()) } }
        }
    }
}

@Composable
fun UpdateDialog(vm: UpdateViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val u = s.update
    if (!s.showDialog || u == null) return
    AlertDialog(
        onDismissRequest = vm::dismiss,
        containerColor = AppColors.Card,
        title = { Text("Nueva versión ${u.version}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (u.notes.isNotBlank()) Text(u.notes.take(600))
                ErrorBanner(s.error)
                if (s.downloading) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = AppColors.Ink, modifier = Modifier.width(20.dp).height(20.dp))
                    Spacer(Modifier.width(10.dp)); Text("Descargando…")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = vm::downloadAndInstall, enabled = !s.downloading) {
                Text("Actualizar", color = AppColors.Ink, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = vm::dismiss) { Text("Más tarde", color = AppColors.Muted) } },
    )
}

// ------------------------------------------------------------ Ajustes

data class ProfileState(val name: String = "", val email: String = "", val saving: Boolean = false, val error: String? = null, val saved: Boolean = false)

@HiltViewModel
class SettingsViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(ProfileState(email = auth.currentEmail.orEmpty()))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch { runCatching { auth.myName() }.onSuccess { n -> _state.update { it.copy(name = n.orEmpty()) } } }
    }

    fun setName(v: String) = _state.update { it.copy(name = v.take(40), saved = false) }

    fun saveName() {
        val n = _state.value.name
        if (n.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            runCatching { auth.updateName(n) }
                .onSuccess { _state.update { it.copy(saving = false, saved = true) } }
                .onFailure { e -> _state.update { it.copy(saving = false, error = e.friendly()) } }
        }
    }

    fun signOut() = viewModelScope.launch { runCatching { auth.signOut() } }
}

@Composable
fun SettingsScreen(updateVm: UpdateViewModel, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val u by updateVm.state.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }
    LocalContext.current

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopBar("Ajustes")
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppCard(Modifier.fillMaxWidth(), padding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(s.name.ifBlank { s.email }, 56.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(s.name.ifBlank { "Sin nombre" }, style = MaterialTheme.typography.titleMedium)
                        Text(s.email, color = AppColors.Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(14.dp))
                AppTextField(s.name, vm::setName, "Tu nombre visible")
                Spacer(Modifier.height(10.dp))
                SecondaryButton(if (s.saved) "Guardado ✓" else "Guardar nombre", vm::saveName, Modifier, enabled = !s.saving)
                ErrorBanner(s.error)
            }

            SectionHeader("Actualizaciones")
            AppCard(Modifier.fillMaxWidth(), padding = 20.dp) {
                Text("Versión instalada: ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        !updateVm.isConfigured -> "Actualizador no configurado (falta GITHUB_REPO)."
                        u.checking -> "Buscando…"
                        u.update != null -> "Disponible la versión ${u.update!!.version}"
                        u.upToDate -> "Tienes la última versión ✓"
                        else -> "Las versiones nuevas se publican en GitHub Releases."
                    },
                    color = AppColors.Muted,
                )
                ErrorBanner(u.error, Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(12.dp))
                if (u.update != null) PrimaryButton(if (u.downloading) "Descargando…" else "Actualizar ahora", updateVm::downloadAndInstall, enabled = !u.downloading)
                else PrimaryButton("Buscar actualizaciones", { updateVm.check() }, enabled = updateVm.isConfigured && !u.checking)
            }

            SectionHeader("Cuenta")
            SecondaryButton("Cerrar sesión", { confirmLogout = true })
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            containerColor = AppColors.Card,
            title = { Text("¿Cerrar sesión?") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; vm.signOut() }) { Text("Cerrar sesión", color = AppColors.Red) } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancelar", color = AppColors.Muted) } },
        )
    }
}
