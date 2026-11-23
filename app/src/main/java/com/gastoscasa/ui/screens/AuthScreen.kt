package com.gastoscasa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gastoscasa.data.AppConfig
import com.gastoscasa.data.AuthRepository
import com.gastoscasa.ui.components.AppCard
import com.gastoscasa.ui.components.DonutChart
import com.gastoscasa.ui.components.DonutSegment
import com.gastoscasa.ui.components.ErrorBanner
import com.gastoscasa.ui.components.PillTabs
import com.gastoscasa.ui.components.PrimaryButton
import com.gastoscasa.ui.friendly
import com.gastoscasa.ui.theme.AppColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthState(val loading: Boolean = false, val error: String? = null)

@HiltViewModel
class AuthViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthState())
    val state = _state.asStateFlow()

    fun submit(register: Boolean, name: String, email: String, password: String) {
        val error = when {
            !AppConfig.isSupabaseConfigured -> "La app no tiene configurado Supabase (ver README)"
            register && name.isBlank() -> "Escribe tu nombre"
            !email.contains("@") -> "Email no válido"
            password.length < 6 -> "La contraseña debe tener al menos 6 caracteres"
            else -> null
        }
        if (error != null) { _state.update { it.copy(error = error) }; return }
        viewModelScope.launch {
            _state.update { AuthState(loading = true) }
            runCatching {
                if (register) auth.signUp(name, email, password) else auth.signIn(email, password)
            }.onFailure { e -> _state.update { AuthState(error = e.friendly()) } }
                .onSuccess { _state.update { AuthState() } }
        }
    }
}

@Composable
fun AuthScreen(vm: AuthViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val register = tab == 1

    Column(
        Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        DonutChart(
            listOf(DonutSegment(56f, AppColors.Yellow), DonutSegment(29f, AppColors.Teal), DonutSegment(15f, AppColors.Line)),
            centerTop = "€", centerBottom = "", size = 120.dp, thickness = 22.dp,
        )
        Text("GASTOS CASA", fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = 2.sp)
        Text("Divide gastos con tu gente", color = AppColors.Muted)
        Spacer(Modifier.height(8.dp))
        AppCard(Modifier.fillMaxWidth(), padding = 8.dp) {
            PillTabs(listOf("Entrar", "Crear cuenta"), tab, { tab = it })
        }
        AppCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (register) AppTextField(name, { name = it }, "Tu nombre")
                AppTextField(email, { email = it }, "Email", keyboardType = KeyboardType.Email)
                AppTextField(
                    password, { password = it }, "Contraseña (mín. 6)",
                    keyboardType = KeyboardType.Password, visual = PasswordVisualTransformation(),
                )
            }
        }
        if (!AppConfig.isSupabaseConfigured) {
            ErrorBanner("Falta SUPABASE_URL / SUPABASE_ANON_KEY en local.properties o en los Secrets de GitHub.")
        }
        ErrorBanner(state.error)
        if (state.loading) CircularProgressIndicator(color = AppColors.Ink)
        else PrimaryButton(if (register) "Crear cuenta" else "Entrar", { vm.submit(register, name, email, password) })
        Text(
            "Tus datos se guardan en tu propia base de datos de Supabase.",
            style = MaterialTheme.typography.labelMedium, color = AppColors.Muted,
        )
    }
}
