package com.familyexpensetracker.prototype.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.familyexpensetracker.prototype.data.NotificationCaptureResult
import com.familyexpensetracker.prototype.data.NotificationRepository
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

data class ActiveNotificationScanResult(
    val processed: Int,
    val added: Int,
    val updated: Int,
    val restored: Int,
    val duplicates: Int,
)

class RaiffeisenNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        currentService = WeakReference(this)
        queueActiveNotificationScan()
    }

    override fun onNotificationPosted(statusBarNotification: StatusBarNotification) {
        serviceScope.launch {
            capture(statusBarNotification)
        }
    }

    override fun onListenerDisconnected() {
        currentService = null
        requestRebind(componentName(this))
        super.onListenerDisconnected()
    }

    private fun queueActiveNotificationScan(
        onComplete: ((ActiveNotificationScanResult) -> Unit)? = null,
    ) {
        serviceScope.launch {
            onComplete?.invoke(scanActiveNotifications())
        }
    }

    private suspend fun scanActiveNotifications(): ActiveNotificationScanResult {
        var processed = 0
        var added = 0
        var updated = 0
        var restored = 0
        var duplicates = 0

        activeNotifications.orEmpty().forEach { notification ->
            when (capture(notification)) {
                null -> Unit
                NotificationCaptureResult.ADDED -> {
                    processed++
                    added++
                }
                NotificationCaptureResult.UPDATED -> {
                    processed++
                    updated++
                }
                NotificationCaptureResult.RESTORED -> {
                    processed++
                    restored++
                }
                NotificationCaptureResult.DUPLICATE -> {
                    processed++
                    duplicates++
                }
            }
        }

        return ActiveNotificationScanResult(processed, added, updated, restored, duplicates)
    }

    private suspend fun capture(
        statusBarNotification: StatusBarNotification,
    ): NotificationCaptureResult? {
        if (!BankPackageAllowlist.contains(statusBarNotification.packageName)) return null

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

        if (rawText.isBlank()) return null

        return NotificationRepository.getInstance(applicationContext).capture(
            packageName = statusBarNotification.packageName,
            title = title,
            rawText = rawText,
            postedAt = statusBarNotification.postTime,
        )
    }

    override fun onDestroy() {
        currentService = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var currentService: WeakReference<RaiffeisenNotificationListenerService>? = null

        fun requestActiveNotificationScan(
            onComplete: ((ActiveNotificationScanResult) -> Unit)? = null,
        ): Boolean {
            val service = currentService?.get() ?: return false
            service.queueActiveNotificationScan(onComplete)
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