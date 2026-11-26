package com.gastoscasa

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gastoscasa.push.Notifications
import com.gastoscasa.ui.AppRoot
import com.gastoscasa.ui.theme.GastosTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** Grupo a abrir al tocar una notificación (se consume una vez). */
    private val openGroup = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openGroup.value = intent.getStringExtra(Notifications.EXTRA_GROUP)
        setContent { GastosTheme { AppRoot(openGroup) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Notifications.EXTRA_GROUP)?.let { openGroup.value = it }
    }
}
