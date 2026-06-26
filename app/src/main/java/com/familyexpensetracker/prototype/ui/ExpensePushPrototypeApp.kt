package com.familyexpensetracker.prototype.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyexpensetracker.prototype.MainViewModel
import com.familyexpensetracker.prototype.categorization.ExpenseCategories
import com.familyexpensetracker.prototype.categorization.RuleBasedCategoryClassifier
import com.familyexpensetracker.prototype.data.AccountEntity
import com.familyexpensetracker.prototype.data.CategoryEntity
import com.familyexpensetracker.prototype.data.NotificationRecordEntity
import com.familyexpensetracker.prototype.export.TransactionCsvExporter
import com.familyexpensetracker.prototype.reporting.LatestCardBalance
import com.familyexpensetracker.prototype.reporting.MonthlyExpenseReport
import com.familyexpensetracker.prototype.reporting.MonthlyExpenseReportCalculator
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@Composable
fun ExpensePushPrototypeApp(
    viewModel: MainViewModel,
    hasNotificationAccess: Boolean,
    openNotificationAccessSettings: () -> Unit,
    scanActiveNotifications: () -> Unit,
    exportTransactionsCsv: (String) -> Unit,
    createBackup: () -> Unit,
    selectBackupToRestore: () -> Unit,
    testBackendConnection: (String) -> Unit,
) {
    val records by viewModel.records.collectAsStateWithLifecycle(emptyList())
    val monthlyReport by viewModel.monthlyReport.collectAsStateWithLifecycle(
        MonthlyExpenseReportCalculator().calculate(emptyList()),
    )
    val latestCardBalances by viewModel.latestCardBalances.collectAsStateWithLifecycle(emptyList())
    val transactionNameSuggestions by viewModel.transactionNameSuggestions.collectAsStateWithLifecycle(emptyList())
    val transactionNameCategories by viewModel.transactionNameCategories.collectAsStateWithLifecycle(emptyMap())
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    val transactionFilter by viewModel.transactionFilter.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle(ExpenseCategories.all)
    val categoryItems by viewModel.categoryItems.collectAsStateWithLifecycle(emptyList())
    val accounts by viewModel.accounts.collectAsStateWithLifecycle(emptyList())
    val accountNames by viewModel.accountNames.collectAsStateWithLifecycle(emptyMap())
    val csvExporter = remember { TransactionCsvExporter() }
    var isAddingTransaction by remember { mutableStateOf(false) }
    var isSelectingPeriod by remember { mutableStateOf(false) }
    var isFilteringTransactions by remember { mutableStateOf(false) }
    var isManagingCategories by remember { mutableStateOf(false) }
    var isManagingAccounts by remember { mutableStateOf(false) }
    var isAppMenuExpanded by remember { mutableStateOf(false) }
    var isConfirmingClear by remember { mutableStateOf(false) }
    var isConfirmingRestore by remember { mutableStateOf(false) }
    var isTestingBackend by remember { mutableStateOf(false) }
    var renamingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var deletingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var renamingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var editingRecord by remember { mutableStateOf<NotificationRecordEntity?>(null) }
    var deletingRecord by remember { mutableStateOf<NotificationRecordEntity?>(null) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box {
                        Button(
                            modifier = Modifier.size(width = 72.dp, height = 48.dp),
                            onClick = { isAppMenuExpanded = true },
                        ) {
                            Text("☰", fontSize = 24.sp)
                        }
                        DropdownMenu(
                            expanded = isAppMenuExpanded,
                            onDismissRequest = { isAppMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (hasNotificationAccess) {
                                            "Notification access: enabled"
                                        } else {
                                            "Notification access: disabled"
                                        },
                                    )
                                },
                                enabled = false,
                                onClick = {},
                            )
                            DropdownMenuItem(
                                text = { Text("Open notification access settings") },
                                onClick = {
                                    isAppMenuExpanded = false
                                    openNotificationAccessSettings()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Scan active pushes") },
                                enabled = hasNotificationAccess,
                                onClick = {
                                    isAppMenuExpanded = false
                                    scanActiveNotifications()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Filters") },
                                onClick = {
                                    isAppMenuExpanded = false
                                    isFilteringTransactions = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Manage categories") },
                                onClick = {
                                    isAppMenuExpanded = false
                                    isManagingCategories = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Manage accounts") },
                                onClick = {
                                    isAppMenuExpanded = false
                                    isManagingAccounts = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Test backend connection") },
                                onClick = {
                                    isAppMenuExpanded = false
                                    isTestingBackend = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Export visible transactions") },
                                enabled = records.isNotEmpty(),
                                onClick = {
                                    isAppMenuExpanded = false
                                    exportTransactionsCsv(csvExporter.export(records, accountNames))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Create backup") },
                                onClick = {
                                    isAppMenuExpanded = false
                                    createBackup()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Restore backup") },
                                onClick = {
                                    isAppMenuExpanded = false
                                    isConfirmingRestore = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Clear transactions") },
                                onClick = {
                                    isAppMenuExpanded = false
                                    isConfirmingClear = true
                                },
                            )
                        }
                    }
                    Text("Expense tracker", style = MaterialTheme.typography.headlineSmall)
                    Button(
                        modifier = Modifier.size(width = 72.dp, height = 48.dp),
                        onClick = { isAddingTransaction = true },
                    ) {
                        Text("+", fontSize = 26.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Spacer(Modifier.height(16.dp))
                if (latestCardBalances.isNotEmpty()) {
                    CardBalancesCard(latestCardBalances)
                    Spacer(Modifier.height(16.dp))
                }
                MonthlyReportCard(
                    report = monthlyReport,
                    selectedPeriod = selectedPeriod,
                    onPreviousMonth = viewModel::showPreviousMonth,
                    onNextMonth = viewModel::showNextMonth,
                    onCustomPeriod = { isSelectingPeriod = true },
                    onCurrentMonth = viewModel::showCurrentMonth,
                )
                Spacer(Modifier.height(16.dp))
                Text("Transactions: ${records.size}", style = MaterialTheme.typography.titleMedium)
                if (transactionFilter.isActive) {
                    Text(
                        transactionFilterSummary(
                            query = transactionFilter.query,
                            category = transactionFilter.category,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (records.isEmpty()) {
                    Text("No transactions captured yet.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        records.groupBy(::displayRecordDay).forEach { (day, dayRecords) ->
                            item(key = "day:$day") {
                                Text(day, style = MaterialTheme.typography.titleSmall)
                            }
                            items(dayRecords, key = { it.id }) { record ->
                                NotificationRecordCard(
                                    record = record,
                                    accountName = record.accountHint?.let(accountNames::get),
                                    onEdit = { editingRecord = record },
                                    onDelete = { deletingRecord = record },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isAddingTransaction) {
            TransactionFormDialog(
                title = "Add transaction",
                initialAmount = "",
                initialCurrency = "RSD",
                initialTransactionName = "",
                initialMerchant = null,
                initialTransactionDate = LocalDate.now().format(DISPLAY_DAY_FORMATTER),
                initialCategory = ExpenseCategories.OTHER,
                transactionNameSuggestions = transactionNameSuggestions,
                transactionNameCategories = transactionNameCategories,
                categories = categories,
                onCreateCategory = viewModel::createCategory,
                onDismiss = { isAddingTransaction = false },
                onSave = { amount, currency, transactionName, transactionDate, category ->
                    viewModel.createManualTransaction(amount, currency, transactionName, transactionDate, category)
                    isAddingTransaction = false
                },
            )
        }

        if (isSelectingPeriod) {
            CustomPeriodDialog(
                initialFrom = selectedPeriod.from,
                initialTo = selectedPeriod.to,
                onDismiss = { isSelectingPeriod = false },
                onSave = { from, to ->
                    viewModel.showCustomPeriod(from, to)
                    isSelectingPeriod = false
                },
            )
        }

        if (isFilteringTransactions) {
            TransactionFilterDialog(
                initialQuery = transactionFilter.query,
                initialCategory = transactionFilter.category,
                categories = categories,
                onDismiss = { isFilteringTransactions = false },
                onApply = { query, category ->
                    viewModel.applyTransactionFilter(query, category)
                    isFilteringTransactions = false
                },
                onReset = {
                    viewModel.clearTransactionFilter()
                    isFilteringTransactions = false
                },
            )
        }

        if (isTestingBackend) {
            BackendConnectionDialog(
                onDismiss = { isTestingBackend = false },
                onTest = { url ->
                    testBackendConnection(url)
                    isTestingBackend = false
                },
            )
        }

        if (isManagingCategories) {
            ManageCategoriesDialog(
                categories = categoryItems,
                onDismiss = { isManagingCategories = false },
                onRename = {
                    isManagingCategories = false
                    renamingCategory = it
                },
                onDelete = {
                    isManagingCategories = false
                    deletingCategory = it
                },
            )
        }

        if (isManagingAccounts) {
            ManageAccountsDialog(
                accounts = accounts,
                onDismiss = { isManagingAccounts = false },
                onRename = {
                    isManagingAccounts = false
                    renamingAccount = it
                },
            )
        }

        renamingAccount?.let { account ->
            RenameAccountDialog(
                account = account,
                onDismiss = { renamingAccount = null },
                onSave = { name ->
                    viewModel.renameAccount(account.accountHint, name)
                    renamingAccount = null
                },
            )
        }

        renamingCategory?.let { category ->
            RenameCategoryDialog(
                category = category,
                categories = categoryItems,
                onDismiss = { renamingCategory = null },
                onSave = { newName ->
                    viewModel.renameCategory(category.name, newName)
                    renamingCategory = null
                },
            )
        }

        deletingCategory?.let { category ->
            AlertDialog(
                onDismissRequest = { deletingCategory = null },
                title = { Text("Delete category?") },
                text = {
                    Text(
                        "Transactions and merchant rules using ${category.name} will be changed to " +
                            "${ExpenseCategories.OTHER}.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteCategory(category.name)
                            deletingCategory = null
                        },
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deletingCategory = null }) {
                        Text("Cancel")
                    }
                },
            )
        }

        editingRecord?.let { record ->
            TransactionFormDialog(
                title = "Edit transaction",
                initialAmount = record.amount.orEmpty(),
                initialCurrency = record.currency.orEmpty(),
                initialTransactionName = record.transactionName.orEmpty(),
                initialMerchant = record.merchant.orEmpty(),
                initialTransactionDate = displayRecordDay(record),
                initialCategory = record.category ?: ExpenseCategories.OTHER,
                transactionNameSuggestions = transactionNameSuggestions,
                transactionNameCategories = transactionNameCategories,
                categories = categories,
                onCreateCategory = viewModel::createCategory,
                onDismiss = { editingRecord = null },
                onSave = { amount, currency, transactionName, transactionDate, category ->
                    viewModel.updateTransaction(
                        id = record.id,
                        amount = amount,
                        currency = currency,
                        transactionName = transactionName,
                        transactionDate = transactionDate,
                        category = category,
                    )
                    editingRecord = null
                },
            )
        }

        deletingRecord?.let { record ->
            AlertDialog(
                onDismissRequest = { deletingRecord = null },
                title = { Text("Delete transaction?") },
                text = { Text("This removes only the selected transaction from the local list.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteTransaction(record.id)
                            deletingRecord = null
                        },
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deletingRecord = null }) {
                        Text("Cancel")
                    }
                },
            )
        }

        if (isConfirmingClear) {
            AlertDialog(
                onDismissRequest = { isConfirmingClear = false },
                title = { Text("Clear all transactions?") },
                text = {
                    Text("This removes all local transactions. Saved categories and merchant rules stay available.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearRecords()
                            isConfirmingClear = false
                        },
                    ) {
                        Text("Clear")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isConfirmingClear = false }) {
                        Text("Cancel")
                    }
                },
            )
        }

        if (isConfirmingRestore) {
            AlertDialog(
                onDismissRequest = { isConfirmingRestore = false },
                title = { Text("Restore backup?") },
                text = {
                    Text(
                        "The selected backup will replace local transactions, user categories, " +
                            "merchant rules, and account names.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            isConfirmingRestore = false
                            selectBackupToRestore()
                        },
                    ) {
                        Text("Select backup")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isConfirmingRestore = false }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@Composable
private fun ManageAccountsDialog(
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onRename: (AccountEntity) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Accounts") },
        text = {
            if (accounts.isEmpty()) {
                Text("No cards recognized yet.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(accounts, key = AccountEntity::accountHint) { account ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(account.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${account.bankName} | ****${account.accountHint}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                TextButton(onClick = { onRename(account) }) {
                                    Text("Rename")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun RenameAccountDialog(
    account: AccountEntity,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(account.accountHint) { mutableStateOf(account.name) }
    val normalizedName = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${account.bankName} | ****${account.accountHint}")
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account name") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = normalizedName.isNotBlank() && normalizedName != account.name,
                onClick = { onSave(normalizedName) },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ManageCategoriesDialog(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onRename: (CategoryEntity) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categories") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(categories, key = CategoryEntity::name) { category ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(category.name, style = MaterialTheme.typography.bodyMedium)
                            if (category.isSystem) {
                                Text("System", style = MaterialTheme.typography.bodySmall)
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { onRename(category) }) {
                                        Text("Rename")
                                    }
                                    TextButton(onClick = { onDelete(category) }) {
                                        Text("Delete")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun RenameCategoryDialog(
    category: CategoryEntity,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var newName by remember(category.name) { mutableStateOf(category.name) }
    val normalizedName = newName.trim()
    val isDuplicate = categories.any {
        it.name != category.name && it.name.equals(normalizedName, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Category name") },
                    singleLine = true,
                )
                if (isDuplicate) {
                    Text("A category with this name already exists.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = normalizedName.isNotBlank() &&
                    normalizedName != category.name &&
                    !isDuplicate,
                onClick = { onSave(normalizedName) },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun TransactionFilterDialog(
    initialQuery: String,
    initialCategory: String?,
    categories: List<String>,
    onDismiss: () -> Unit,
    onApply: (String, String?) -> Unit,
    onReset: () -> Unit,
) {
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    var category by remember(initialCategory) { mutableStateOf(initialCategory) }
    var isCategoryMenuExpanded by remember { mutableStateOf(false) }
    var categoryButtonWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transaction filters") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { categoryButtonWidth = it.size.width },
                        onClick = { isCategoryMenuExpanded = !isCategoryMenuExpanded },
                    ) {
                        Text("Category: ${category ?: "All"}")
                    }
                    DropdownMenu(
                        modifier = Modifier
                            .width(with(density) { categoryButtonWidth.toDp() })
                            .heightIn(max = 240.dp),
                        expanded = isCategoryMenuExpanded,
                        onDismissRequest = { isCategoryMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("All") },
                            onClick = {
                                category = null
                                isCategoryMenuExpanded = false
                            },
                        )
                        categories.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    category = option
                                    isCategoryMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(query, category) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) {
                    Text("Reset")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable
private fun CardBalancesCard(balances: List<LatestCardBalance>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Card balances", style = MaterialTheme.typography.titleMedium)
            balances.forEach { balance ->
                Text(
                    "${balance.accountName} (****${balance.accountHint}): ${balance.amount} ${balance.currency}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun NotificationRecordCard(
    record: NotificationRecordEntity,
    accountName: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(record.parseStatus, style = MaterialTheme.typography.labelLarge)
            ParsedField("Amount", listOfNotNull(record.amount, record.currency).joinToString(" "))
            ParsedField("Name", record.transactionName)
            ParsedField("Merchant", record.merchant)
            ParsedField("Source", record.source)
            ParsedField("Operation", record.operationType)
            ParsedField("Category", record.category ?: ExpenseCategories.OTHER)
            ParsedField("Account", accountName)
            ParsedField("Card", record.accountHint?.let { "****$it" })
            ParsedField("Transaction date", record.transactionDate)
            ParsedField(
                "Available balance",
                listOfNotNull(record.availableBalance, record.availableBalanceCurrency).joinToString(" "),
            )
            ParsedField("Received", DateFormat.getDateTimeInstance().format(Date(record.receivedAt)))
            Spacer(Modifier.height(6.dp))
            if (record.rawText.isNotBlank()) {
                Text(record.rawText, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) {
                    Text("Edit")
                }
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun TransactionFormDialog(
    title: String,
    initialAmount: String,
    initialCurrency: String,
    initialTransactionName: String,
    initialMerchant: String?,
    initialTransactionDate: String,
    initialCategory: String,
    transactionNameSuggestions: List<String>,
    transactionNameCategories: Map<String, String>,
    categories: List<String>,
    onCreateCategory: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit,
) {
    var amount by remember(initialAmount) { mutableStateOf(initialAmount) }
    var currency by remember(initialCurrency) { mutableStateOf(initialCurrency) }
    var transactionName by remember(initialTransactionName) { mutableStateOf(initialTransactionName) }
    var transactionDate by remember(initialTransactionDate) { mutableStateOf(initialTransactionDate) }
    var category by remember(initialCategory) { mutableStateOf(initialCategory) }
    var isCurrencyMenuExpanded by remember { mutableStateOf(false) }
    var isNameMenuExpanded by remember { mutableStateOf(false) }
    var isCategoryMenuExpanded by remember { mutableStateOf(false) }
    var isCreatingCategory by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var currencyButtonWidth by remember { mutableStateOf(0) }
    var categoryButtonWidth by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val categoryClassifier = remember { RuleBasedCategoryClassifier() }
    val selectedDate = runCatching {
        LocalDate.parse(transactionDate, DISPLAY_DAY_FORMATTER)
    }.getOrDefault(LocalDate.now())
    val matchingNames = transactionNameSuggestions.filter {
        transactionName.isBlank() || it.contains(transactionName, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { currencyButtonWidth = it.size.width },
                        onClick = { isCurrencyMenuExpanded = !isCurrencyMenuExpanded },
                    ) {
                        Text("Currency: $currency")
                    }
                    DropdownMenu(
                        modifier = Modifier.width(with(density) { currencyButtonWidth.toDp() }),
                        expanded = isCurrencyMenuExpanded,
                        onDismissRequest = { isCurrencyMenuExpanded = false },
                        properties = PopupProperties(focusable = true),
                        containerColor = MaterialTheme.colorScheme.primary,
                    ) {
                        AVAILABLE_CURRENCIES.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(option, color = MaterialTheme.colorScheme.onPrimary)
                                },
                                onClick = {
                                    currency = option
                                    isCurrencyMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = transactionName,
                    onValueChange = {
                        transactionName = it
                        isNameMenuExpanded = it.isNotBlank()
                    },
                    label = { Text("Name") },
                    singleLine = true,
                )
                if (isNameMenuExpanded && matchingNames.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(matchingNames.take(5)) { option ->
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    transactionName = option
                                    category = transactionNameCategories[option]
                                        ?: categoryClassifier.suggestExpenseCategory(option)
                                    isNameMenuExpanded = false
                                },
                            ) {
                                Text(option)
                            }
                        }
                    }
                }
                if (!initialMerchant.isNullOrBlank()) {
                    Text("Recognized merchant: $initialMerchant", style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        openDatePicker(context, selectedDate) { selected ->
                            transactionDate = selected.format(DISPLAY_DAY_FORMATTER)
                        }
                    },
                ) {
                    Text("Date: $transactionDate")
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { categoryButtonWidth = it.size.width },
                        onClick = { isCategoryMenuExpanded = !isCategoryMenuExpanded },
                    ) {
                        Text("Category: $category")
                    }
                    DropdownMenu(
                        modifier = Modifier
                            .width(with(density) { categoryButtonWidth.toDp() })
                            .heightIn(max = 240.dp),
                        expanded = isCategoryMenuExpanded,
                        onDismissRequest = { isCategoryMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("New category") },
                            onClick = {
                                isCategoryMenuExpanded = false
                                isCreatingCategory = true
                            },
                        )
                        categories.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    category = option
                                    isCategoryMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount.isNotBlank() && transactionName.isNotBlank(),
                onClick = { onSave(amount, currency, transactionName, transactionDate, category) },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )

    if (isCreatingCategory) {
        AlertDialog(
            onDismissRequest = { isCreatingCategory = false },
            title = { Text("New category") },
            text = {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("Category name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newCategoryName.isNotBlank(),
                    onClick = {
                        val createdCategory = newCategoryName.trim()
                        onCreateCategory(createdCategory)
                        category = createdCategory
                        newCategoryName = ""
                        isCreatingCategory = false
                    },
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { isCreatingCategory = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun MonthlyReportCard(
    report: MonthlyExpenseReport,
    selectedPeriod: MainViewModel.SelectedPeriod,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCustomPeriod: () -> Unit,
    onCurrentMonth: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Monthly report", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    enabled = selectedPeriod.mode == MainViewModel.PeriodMode.MONTH,
                    onClick = onPreviousMonth,
                ) {
                    Text("<")
                }
                Text(periodTitle(selectedPeriod), style = MaterialTheme.typography.titleSmall)
                TextButton(
                    enabled = selectedPeriod.mode == MainViewModel.PeriodMode.MONTH &&
                        selectedPeriod.month < YearMonth.now(),
                    onClick = onNextMonth,
                ) {
                    Text(">")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCustomPeriod) {
                    Text("Custom period")
                }
                if (selectedPeriod.mode == MainViewModel.PeriodMode.CUSTOM) {
                    TextButton(onClick = onCurrentMonth) {
                        Text("Current month")
                    }
                }
            }
            if (report.currencyTotals.isEmpty()) {
                Text("No expenses in this month.", style = MaterialTheme.typography.bodyMedium)
            } else {
                report.currencyTotals.forEach { total ->
                    Text(
                        "Total: ${total.amount.stripTrailingZeros().toPlainString()} ${total.currency}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(4.dp))
                report.categoryTotals.forEach { total ->
                    Text(
                        "${total.category}: ${total.amount.stripTrailingZeros().toPlainString()} ${total.currency}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (report.merchantTotals.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Top merchants", style = MaterialTheme.typography.titleSmall)
                    report.merchantTotals.forEach { total ->
                        Text(
                            "${total.name}: ${total.amount.stripTrailingZeros().toPlainString()} ${total.currency}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomPeriodDialog(
    initialFrom: LocalDate,
    initialTo: LocalDate,
    onDismiss: () -> Unit,
    onSave: (LocalDate, LocalDate) -> Unit,
) {
    var from by remember(initialFrom) { mutableStateOf(initialFrom) }
    var to by remember(initialTo) { mutableStateOf(initialTo) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom period") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        openDatePicker(context, from) { selected ->
                            from = selected
                            if (to < selected) to = selected
                        }
                    },
                ) {
                    Text("From: ${from.format(DISPLAY_DAY_FORMATTER)}")
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        openDatePicker(context, to) { selected ->
                            to = selected
                            if (from > selected) from = selected
                        }
                    },
                ) {
                    Text("To: ${to.format(DISPLAY_DAY_FORMATTER)}")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(from, to) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ParsedField(label: String, value: String?) {
    if (!value.isNullOrBlank()) {
        Text("$label: $value", style = MaterialTheme.typography.bodyMedium)
    }
}

private fun displayRecordDay(record: NotificationRecordEntity): String =
    record.transactionDay
        ?.let(LocalDate::parse)
        ?.format(DISPLAY_DAY_FORMATTER)
        ?: Instant.ofEpochMilli(record.transactionTimestamp ?: record.postedAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DISPLAY_DAY_FORMATTER)

private val DISPLAY_DAY_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val MONTH_FORMATTER = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())
private val AVAILABLE_CURRENCIES = listOf("RSD", "EUR")

private fun transactionFilterSummary(query: String, category: String?): String =
    listOfNotNull(
        query.takeIf(String::isNotBlank)?.let { "Search: $it" },
        category?.let { "Category: $it" },
    ).joinToString(" | ")

@Composable
private fun BackendConnectionDialog(
    onDismiss: () -> Unit,
    onTest: (String) -> Unit,
) {
    var backendUrl by remember { mutableStateOf("http://10.0.2.2:5062") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Test backend") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Use 10.0.2.2 for Android emulator. For a real phone, use your computer IP " +
                        "on the same Wi-Fi network.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = backendUrl,
                    onValueChange = { backendUrl = it },
                    label = { Text("Backend URL") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = backendUrl.isNotBlank(),
                onClick = { onTest(backendUrl) },
            ) {
                Text("Test")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun periodTitle(period: MainViewModel.SelectedPeriod): String =
    when (period.mode) {
        MainViewModel.PeriodMode.MONTH -> period.month.format(MONTH_FORMATTER)
        MainViewModel.PeriodMode.CUSTOM ->
            "${period.from.format(DISPLAY_DAY_FORMATTER)} - ${period.to.format(DISPLAY_DAY_FORMATTER)}"
    }

private fun openDatePicker(
    context: android.content.Context,
    initialDate: LocalDate,
    onSelected: (LocalDate) -> Unit,
) {
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth -> onSelected(LocalDate.of(year, month + 1, dayOfMonth)) },
        initialDate.year,
        initialDate.monthValue - 1,
        initialDate.dayOfMonth,
    ).apply {
        datePicker.maxDate = System.currentTimeMillis()
    }.show()
}
