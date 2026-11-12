package tech.vauldex.dekita.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tech.vauldex.dekita.MainActivity
import tech.vauldex.dekita.R
import tech.vauldex.dekita.presentation.orders.OrderNotice
import tech.vauldex.dekita.presentation.orders.OrderNotifications

/**
 * Posts ORDER-01's order notices as Android system notifications. Local notifications only: the mock
 * has no server push. One notification per order, tagged with its id, so "ready" replaces "on its way".
 */
class OrderNotifier(context: Context) {
    private val context = context.applicationContext
    private val manager = NotificationManagerCompat.from(this.context)

    /** Collects for as long as [scope] lives, so notices arrive while no screen is open. */
    fun start(notifications: OrderNotifications, scope: CoroutineScope) {
        createChannel()
        scope.launch { notifications.notices.collect(::post) }
        // A signed-out phone shouldn't keep showing the last customer's orders.
        scope.launch { notifications.signedOut.collect { manager.cancelAll() } }
    }

    private fun createChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName("Order updates")
            .setDescription("When your order is placed and when it's ready for pickup")
            .build()
        manager.createNotificationChannel(channel)
    }

    private fun post(notice: OrderNotice) {
        // Without the permission (Android 13+) or with notifications off, the ticket and Orders still show the status.
        if (!manager.areNotificationsEnabled() || !hasPermission()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_orders)
            .setContentTitle(notice.title)
            .setContentText(notice.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notice.body))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openTicket(notice.orderId))
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(notice.orderId, NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // The permission was revoked between the check and the post. The ticket still shows the status.
        }
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Explicit and immutable, so no other app can redirect or change it. */
    private fun openTicket(orderId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_ORDER_ID, orderId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            orderId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val CHANNEL_ID = "order-updates"
        const val NOTIFICATION_ID = 1
    }
}
