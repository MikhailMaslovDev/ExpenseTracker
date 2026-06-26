package com.familyexpensetracker.prototype.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.familyexpensetracker.prototype.data.NotificationRepository
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class RaiffeisenNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        currentService = WeakReference(this)
        scanActiveNotifications()
    }

    override fun onNotificationPosted(statusBarNotification: StatusBarNotification) {
        capture(statusBarNotification)
    }

    override fun onListenerDisconnected() {
        currentService = null
        requestRebind(componentName(this))
        super.onListenerDisconnected()
    }

    private fun scanActiveNotifications() {
        activeNotifications.orEmpty().forEach(::capture)
    }

    private fun capture(statusBarNotification: StatusBarNotification) {
        if (!BankPackageAllowlist.contains(statusBarNotification.packageName)) return

        val notification = statusBarNotification.notification
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val rawText = sequenceOf(
            extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT),
        )
            .mapNotNull { it?.toString()?.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")

        if (rawText.isBlank()) return

        serviceScope.launch {
            NotificationRepository.getInstance(applicationContext).capture(
                packageName = statusBarNotification.packageName,
                title = title,
                rawText = rawText,
                postedAt = statusBarNotification.postTime,
            )
        }
    }

    override fun onDestroy() {
        currentService = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var currentService: WeakReference<RaiffeisenNotificationListenerService>? = null

        fun requestActiveNotificationScan(): Boolean {
            val service = currentService?.get() ?: return false
            service.scanActiveNotifications()
            return true
        }

        fun ensureConnected(context: Context): Boolean {
            if (currentService?.get() != null) return true
            requestRebind(componentName(context))
            return false
        }

        private fun componentName(context: Context): ComponentName =
            ComponentName(context, RaiffeisenNotificationListenerService::class.java)
    }
}
