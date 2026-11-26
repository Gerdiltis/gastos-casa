package com.gastoscasa.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gastoscasa.data.RecurringRepository
import com.gastoscasa.ui.friendly
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import javax.inject.Inject

/** Botones Apuntar / Omitir de la notificación de un gasto recurrente (sin abrir la app). */
@AndroidEntryPoint
class RecurringActionReceiver : BroadcastReceiver() {
    @Inject lateinit var recurring: RecurringRepository

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val date = intent.getStringExtra(EXTRA_DATE)?.let(LocalDate::parse) ?: return
        val accept = intent.getBooleanExtra(EXTRA_ACCEPT, false)
        val notification = intent.getIntExtra(EXTRA_NOTIFICATION, 0)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Gastos Casa"
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val text = runCatching { withTimeout(9_000) { recurring.resolve(id, date, accept) } }
                .fold({ if (accept) "Gasto apuntado ✓" else "Repetición omitida" }, { "No se pudo: ${it.friendly()}" })
            Notifications.showResult(context, notification, title, text)
            result.finish()
        }
    }

    companion object {
        const val EXTRA_ID = "recurringId"
        const val EXTRA_DATE = "date"
        const val EXTRA_ACCEPT = "accept"
        const val EXTRA_NOTIFICATION = "notificationId"
        const val EXTRA_TITLE = "title"
    }
}
