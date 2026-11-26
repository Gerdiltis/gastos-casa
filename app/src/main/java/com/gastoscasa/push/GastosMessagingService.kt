package com.gastoscasa.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gastoscasa.MainActivity
import com.gastoscasa.R
import com.gastoscasa.data.PushRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class GastosMessagingService : FirebaseMessagingService() {
    @Inject lateinit var push: PushRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { runCatching { push.register(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val d = message.data
        val title = d["title"] ?: message.notification?.title ?: return
        val body = d["body"] ?: message.notification?.body.orEmpty()
        val group = d["group_id"]
        val recurring = d["recurring_id"]
        val date = d["date"]
        if (d["type"] == "recurring" && group != null && recurring != null && date != null) {
            Notifications.showRecurring(this, title, body, group, recurring, date)
        } else {
            Notifications.show(this, title, body, group?.let { "group/$it" }, d["expense_id"]?.hashCode() ?: title.hashCode())
        }
    }
}

object Notifications {
    const val CHANNEL = "gastos"
    /** Ruta de navegación a abrir al tocar la notificación (p. ej. "group/<id>"). */
    const val EXTRA_ROUTE = "route"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, "Gastos del grupo", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Cuando alguien añade, modifica o elimina un gasto" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun allowed(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context, route: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (route != null) putExtra(EXTRA_ROUTE, route)
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun base(context: Context, title: String, body: String) = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_gastos)
        .setColor(0xFF4FD1B5.toInt())
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)

    /** Pregunta al creador si apunta la repetición: botones Apuntar y Omitir, o tocar para revisar. */
    fun showRecurring(context: Context, title: String, body: String, groupId: String, recurringId: String, date: String) {
        if (!allowed(context)) return
        val id = "$recurringId/$date".hashCode()
        fun action(accept: Boolean) = PendingIntent.getBroadcast(
            context, id * 2 + if (accept) 1 else 0,
            Intent(context, RecurringActionReceiver::class.java).apply {
                putExtra(RecurringActionReceiver.EXTRA_ID, recurringId)
                putExtra(RecurringActionReceiver.EXTRA_DATE, date)
                putExtra(RecurringActionReceiver.EXTRA_ACCEPT, accept)
                putExtra(RecurringActionReceiver.EXTRA_NOTIFICATION, id)
                putExtra(RecurringActionReceiver.EXTRA_TITLE, title)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = base(context, title, body)
            .setContentIntent(openApp(context, "group/$groupId/recurring/$recurringId/confirm/$date", id))
            .addAction(0, "Apuntar", action(true))
            .addAction(0, "Omitir", action(false))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }

    /** Sustituye la pregunta por el resultado ("Apuntado ✓", error…). */
    fun showResult(context: Context, id: Int, title: String, body: String) {
        if (!allowed(context)) return
        NotificationManagerCompat.from(context).notify(id, base(context, title, body).setAutoCancel(true).setTimeoutAfter(8000).build())
    }

    fun show(context: Context, title: String, body: String, route: String?, id: Int) {
        if (!allowed(context)) return
        val pending = openApp(context, route, id)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_gastos)
            .setColor(0xFF4FD1B5.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }
}
