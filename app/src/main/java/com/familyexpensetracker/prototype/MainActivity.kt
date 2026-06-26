package com.familyexpensetracker.prototype

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationManagerCompat
import com.familyexpensetracker.prototype.notifications.RaiffeisenNotificationListenerService
import com.familyexpensetracker.prototype.ui.ExpensePushPrototypeApp
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val hasNotificationAccess = mutableStateOf(false)
    private var pendingCsvContent: String? = null
    private var pendingBackupContent: String? = null
    private val createCsvDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val content = pendingCsvContent
        pendingCsvContent = null
        if (uri == null || content == null) return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                writer.write(content)
            } ?: error("Unable to open the selected file")
        }.onSuccess {
            Toast.makeText(this, "CSV exported", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "CSV export failed", Toast.LENGTH_LONG).show()
        }
    }
    private val createBackupDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val content = pendingBackupContent
        pendingBackupContent = null
        if (uri == null || content == null) return@registerForActivityResult
        writeDocument(uri, content, "Backup created", "Backup failed")
    }
    private val openBackupDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val content = runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("Unable to open the selected file")
        }.getOrElse {
            Toast.makeText(this, "Unable to read backup", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        viewModel.restoreBackup(content) { result ->
            Toast.makeText(
                this,
                if (result.isSuccess) "Backup restored" else "Invalid or unsupported backup",
                if (result.isSuccess) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExpensePushPrototypeApp(
                viewModel = viewModel,
                hasNotificationAccess = hasNotificationAccess.value,
                openNotificationAccessSettings = {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                scanActiveNotifications = {
                    RaiffeisenNotificationListenerService.requestActiveNotificationScan()
                },
                exportTransactionsCsv = ::exportTransactionsCsv,
                createBackup = ::createBackup,
                selectBackupToRestore = {
                    openBackupDocument.launch(arrayOf("application/json", "text/json", "text/plain"))
                },
                testBackendConnection = ::testBackendConnection,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        hasNotificationAccess.value = hasNotificationAccess()
        if (hasNotificationAccess.value) {
            if (RaiffeisenNotificationListenerService.ensureConnected(this)) {
                RaiffeisenNotificationListenerService.requestActiveNotificationScan()
            }
        }
    }

    private fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun exportTransactionsCsv(content: String) {
        pendingCsvContent = content
        createCsvDocument.launch("family-expenses-${LocalDate.now()}.csv")
    }

    private fun createBackup() {
        viewModel.createBackup { result ->
            result.onSuccess { content ->
                pendingBackupContent = content
                createBackupDocument.launch("family-expenses-backup-${LocalDate.now()}.json")
            }.onFailure {
                Toast.makeText(this, "Backup failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun testBackendConnection(baseUrl: String) {
        viewModel.testBackendConnection(baseUrl) { result ->
            Toast.makeText(
                this,
                result.fold(
                    onSuccess = { "Backend is reachable" },
                    onFailure = { "Backend check failed: ${it.message}" },
                ),
                if (result.isSuccess) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun writeDocument(
        uri: android.net.Uri,
        content: String,
        successMessage: String,
        failureMessage: String,
    ) {
        runCatching {
            contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                writer.write(content)
            } ?: error("Unable to open the selected file")
        }.onSuccess {
            Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, failureMessage, Toast.LENGTH_LONG).show()
        }
    }
}
