package com.familyexpensetracker.prototype

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.familyexpensetracker.prototype.backup.ExpenseBackupSerializer
import com.familyexpensetracker.prototype.backend.BackendHealthClient
import com.familyexpensetracker.prototype.data.NotificationRepository
import com.familyexpensetracker.prototype.categorization.RuleBasedCategoryClassifier
import com.familyexpensetracker.prototype.reporting.LatestCardBalanceCalculator
import com.familyexpensetracker.prototype.reporting.MonthlyExpenseReportCalculator
import com.familyexpensetracker.prototype.reporting.TransactionFilterCriteria
import com.familyexpensetracker.prototype.reporting.TransactionListFilter
import com.familyexpensetracker.prototype.reporting.TransactionPeriodFilter
import java.time.YearMonth
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    enum class PeriodMode {
        MONTH,
        CUSTOM,
    }

    data class SelectedPeriod(
        val mode: PeriodMode,
        val month: YearMonth,
        val from: LocalDate,
        val to: LocalDate,
    )

    private val repository = NotificationRepository.getInstance(application)
    private val periodFilter = TransactionPeriodFilter()
    private val reportCalculator = MonthlyExpenseReportCalculator()
    private val cardBalanceCalculator = LatestCardBalanceCalculator()
    private val transactionListFilter = TransactionListFilter()
    private val backupSerializer = ExpenseBackupSerializer()
    private val backendHealthClient = BackendHealthClient()
    private val allRecords = repository.records
    private val currentMonth = YearMonth.now()
    private val _selectedPeriod = MutableStateFlow(monthPeriod(currentMonth))
    private val _transactionFilter = MutableStateFlow(TransactionFilterCriteria())

    val selectedPeriod = _selectedPeriod.asStateFlow()
    val transactionFilter = _transactionFilter.asStateFlow()
    val categoryItems = repository.categories
    val categories = repository.categories.map { categories -> categories.map { it.name } }
    val accounts = repository.accounts
    val accountNames = repository.accounts.map { accounts ->
        accounts.associate { it.accountHint to it.name }
    }
    val records = combine(allRecords, selectedPeriod, transactionFilter) { currentRecords, period, filter ->
        transactionListFilter.apply(
            periodFilter.filterByRange(currentRecords, period.from, period.to),
            filter,
        )
    }
    val monthlyReport = combine(allRecords, selectedPeriod) { currentRecords, period ->
        reportCalculator.calculate(currentRecords, period.from, period.to)
    }
    val latestCardBalances = combine(allRecords, accountNames) { currentRecords, currentAccountNames ->
        cardBalanceCalculator.calculate(currentRecords, currentAccountNames)
    }
    val transactionNameSuggestions = allRecords.map { currentRecords ->
        (RuleBasedCategoryClassifier.suggestedTransactionNames + currentRecords.mapNotNull { it.transactionName })
            .distinct()
            .sorted()
    }
    val transactionNameCategories = allRecords.map { currentRecords ->
        buildMap {
            currentRecords.forEach { record ->
                val name = record.transactionName
                val category = record.category
                if (!name.isNullOrBlank() && !category.isNullOrBlank()) {
                    putIfAbsent(name, category)
                }
            }
        }
    }

    fun showPreviousMonth() {
        _selectedPeriod.value = monthPeriod(_selectedPeriod.value.month.minusMonths(1))
    }

    fun showNextMonth() {
        val nextMonth = _selectedPeriod.value.month.plusMonths(1)
        if (nextMonth <= YearMonth.now()) {
            _selectedPeriod.value = monthPeriod(nextMonth)
        }
    }

    fun showCurrentMonth() {
        _selectedPeriod.value = monthPeriod(YearMonth.now())
    }

    fun showCustomPeriod(from: LocalDate, to: LocalDate) {
        if (from > to || to > LocalDate.now()) return
        _selectedPeriod.value = SelectedPeriod(
            mode = PeriodMode.CUSTOM,
            month = YearMonth.from(to),
            from = from,
            to = to,
        )
    }

    fun applyTransactionFilter(query: String, category: String?) {
        _transactionFilter.value = TransactionFilterCriteria(
            query = query.trim(),
            category = category,
        )
    }

    fun clearTransactionFilter() {
        _transactionFilter.value = TransactionFilterCriteria()
    }

    private fun monthPeriod(month: YearMonth): SelectedPeriod =
        SelectedPeriod(
            mode = PeriodMode.MONTH,
            month = month,
            from = month.atDay(1),
            to = month.atEndOfMonth(),
        )

    fun clearRecords() {
        viewModelScope.launch {
            repository.clear()
        }
    }

    fun updateTransaction(
        id: Long,
        amount: String,
        currency: String,
        transactionName: String,
        transactionDate: String,
        category: String,
    ) {
        viewModelScope.launch {
            repository.updateTransaction(id, amount, currency, transactionName, transactionDate, category)
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
        }
    }

    fun createManualTransaction(
        amount: String,
        currency: String,
        transactionName: String,
        transactionDate: String,
        category: String,
    ) {
        viewModelScope.launch {
            repository.createManualTransaction(amount, currency, transactionName, transactionDate, category)
        }
    }

    fun createCategory(name: String) {
        viewModelScope.launch {
            repository.createCategory(name)
        }
    }

    fun renameCategory(oldName: String, newName: String) {
        viewModelScope.launch {
            val renamed = repository.renameCategory(oldName, newName)
            if (renamed && _transactionFilter.value.category == oldName) {
                _transactionFilter.value = _transactionFilter.value.copy(category = newName.trim())
            }
        }
    }

    fun deleteCategory(name: String) {
        viewModelScope.launch {
            val deleted = repository.deleteCategory(name)
            if (deleted && _transactionFilter.value.category == name) {
                _transactionFilter.value = _transactionFilter.value.copy(category = null)
            }
        }
    }

    fun renameAccount(accountHint: String, name: String) {
        viewModelScope.launch {
            repository.renameAccount(accountHint, name)
        }
    }

    fun createBackup(onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            onResult(
                runCatching {
                    backupSerializer.encode(repository.createBackup())
                },
            )
        }
    }

    fun restoreBackup(content: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                repository.restoreBackup(backupSerializer.decode(content))
                _transactionFilter.value = TransactionFilterCriteria()
                _selectedPeriod.value = monthPeriod(YearMonth.now())
            }
            onResult(result)
        }
    }

    fun testBackendConnection(baseUrl: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = backendHealthClient.check(baseUrl)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onResult(result)
            }
        }
    }
}
