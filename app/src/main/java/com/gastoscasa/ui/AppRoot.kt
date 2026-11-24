package com.gastoscasa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gastoscasa.data.AuthRepository
import com.gastoscasa.ui.components.FloatingNavBar
import com.gastoscasa.ui.components.mainNavItems
import com.gastoscasa.ui.screens.AddExpenseScreen
import com.gastoscasa.ui.screens.AuthScreen
import com.gastoscasa.ui.screens.GroupDetailScreen
import com.gastoscasa.ui.screens.GroupsScreen
import com.gastoscasa.ui.screens.HomeScreen
import com.gastoscasa.ui.screens.SettingsScreen
import com.gastoscasa.ui.screens.ShoppingScreen
import com.gastoscasa.ui.screens.StatsScreen
import com.gastoscasa.ui.screens.UpdateDialog
import com.gastoscasa.ui.screens.UpdateViewModel
import com.gastoscasa.ui.theme.AppColors
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class Session { Loading, LoggedOut, LoggedIn }

@HiltViewModel
class SessionViewModel @Inject constructor(auth: AuthRepository) : ViewModel() {
    val session = auth.sessionStatus.map {
        when (it) {
            is SessionStatus.Authenticated -> Session.LoggedIn
            is SessionStatus.NotAuthenticated -> Session.LoggedOut
            is SessionStatus.RefreshFailure -> Session.LoggedIn // sin red: mantenemos la sesión
            else -> Session.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Session.Loading)
}

@Composable
fun AppRoot(sessionVm: SessionViewModel = hiltViewModel(), updateVm: UpdateViewModel = hiltViewModel()) {
    val session by sessionVm.session.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { updateVm.autoCheck() }

    Box(Modifier.fillMaxSize().background(AppColors.background)) {
        when (session) {
            Session.Loading -> CircularProgressIndicator(color = AppColors.Ink, modifier = Modifier.align(Alignment.Center))
            Session.LoggedOut -> AuthScreen()
            Session.LoggedIn -> MainNav(updateVm)
        }
        UpdateDialog(updateVm)
    }
}

@Composable
private fun MainNav(updateVm: UpdateViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = mainNavItems.any { it.route == route }

    Box(Modifier.fillMaxSize()) {
        NavHost(nav, startDestination = "home", modifier = Modifier.fillMaxSize()) {
            composable("home") {
                HomeScreen(
                    onOpenGroup = { nav.navigate("group/$it") },
                    onSeeGroups = { nav.navigateTab("groups") },
                )
            }
            composable("groups") { GroupsScreen(onOpenGroup = { nav.navigate("group/$it") }) }
            composable("shopping") { ShoppingScreen(onSeeGroups = { nav.navigateTab("groups") }) }
            composable("stats") { StatsScreen() }
            composable("settings") { SettingsScreen(updateVm) }
            composable("group/{groupId}") {
                GroupDetailScreen(
                    onBack = { nav.popBackStack() },
                    onAddExpense = { id -> nav.navigate("group/$id/new") },
                )
            }
            composable("group/{groupId}/new") { AddExpenseScreen(onBack = { nav.popBackStack() }) }
        }
        if (showBar) {
            FloatingNavBar(
                current = route,
                onSelect = { nav.navigateTab(it) },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
            )
        }
    }
}

private fun androidx.navigation.NavController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
