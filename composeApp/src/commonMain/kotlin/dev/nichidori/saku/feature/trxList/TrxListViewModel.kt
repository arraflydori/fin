package dev.nichidori.saku.feature.trxList

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.nichidori.saku.core.event.AppEvent
import dev.nichidori.saku.core.event.AppEventBus
import dev.nichidori.saku.core.model.Status
import dev.nichidori.saku.core.model.Status.*
import dev.nichidori.saku.core.util.affectedMonths
import dev.nichidori.saku.core.util.log
import dev.nichidori.saku.domain.model.*
import dev.nichidori.saku.domain.repo.AccountRepository
import dev.nichidori.saku.domain.repo.CategoryRepository
import dev.nichidori.saku.domain.repo.InstallmentRepository
import dev.nichidori.saku.domain.repo.TrxRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

data class DailyTrxRecord(
    val trxs: List<Trx>,
    val totalIncome: Long,
    val totalExpense: Long,
)

internal fun groupTrxsByDate(trxs: List<Trx>): Map<LocalDate, DailyTrxRecord> {
    return trxs.groupBy { trx ->
        trx.transactionAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
    }.mapValues { (_, dailyTrxs) ->
        DailyTrxRecord(
            trxs = dailyTrxs,
            totalIncome = dailyTrxs.filterIsInstance<Trx.Income>().sumOf { it.amount },
            totalExpense = dailyTrxs.filterIsInstance<Trx.Expense>()
                .filter { it.installment !is InstallmentInfo.Charge }
                .sumOf { it.amount },
        )
    }
}

data class TrxListUiState(
    val stateByMonth: Map<YearMonth, MonthlyState> = emptyMap(),
    val accounts: List<TrxAccount> = emptyList(),
    val incomeCategories: List<Category> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
    val filterAccountIds: Set<String> = emptySet(),
    val filterAccountTypes: Set<AccountType> = emptySet(),
    val filterCategoryIds: Set<String> = emptySet(),
    val filterTrxTypes: Set<TrxType> = emptySet(),
) {
    val accountTypes: Set<AccountType> = AccountType.entries.toSet()
    val trxTypes: Set<TrxType> = TrxType.entries.toSet()
    val hasFilter: Boolean = filterAccountIds.isNotEmpty()
            || filterAccountTypes.isNotEmpty()
            || filterCategoryIds.isNotEmpty()
            || filterTrxTypes.isNotEmpty()

    data class MonthlyState(
        val loadStatus: Status<Unit, Exception> = Initial,
        val rawTrxs: List<Trx> = emptyList(),
        val trxRecordsByDate: Map<LocalDate, DailyTrxRecord> = emptyMap(),
    )
}

@OptIn(FlowPreview::class)
class TrxListViewModel(
    private val appEventBus: AppEventBus,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val trxRepository: TrxRepository,
    private val installmentRepository: InstallmentRepository,
) : ViewModel() {
    init {
        loadAccounts()
        loadCategories()
        viewModelScope.launch {
            appEventBus.events
                .filterIsInstance<AppEvent.TrxChanged>()
                .debounce(150.milliseconds)
                .collect { event ->
                    event.affectedMonths()
                        .filter { it in _uiState.value.stateByMonth.keys }
                        .forEach { month -> loadTrxs(month) }
                }
        }
    }

    private val _uiState = MutableStateFlow(TrxListUiState())
    val uiState: StateFlow<TrxListUiState> = _uiState.asStateFlow()

    fun loadTrxs(month: YearMonth) {
        viewModelScope.launch {
            try {
                updateMonthlyState(month) {
                    it.copy(loadStatus = Loading)
                }

                val trxs = trxRepository.getFilteredTrxs(TrxFilter(month = month))

                _uiState.update { currentState ->
                    val filteredRecords = filterAndGroupTransactions(
                        trxs = trxs,
                        accountIds = currentState.filterAccountIds,
                        accountTypes = currentState.filterAccountTypes,
                        categoryIds = currentState.filterCategoryIds,
                        trxTypes = currentState.filterTrxTypes,
                    )

                    val currentMonthlyState = currentState.stateByMonth[month] ?: TrxListUiState.MonthlyState()
                    currentState.copy(
                        stateByMonth = currentState.stateByMonth + (month to currentMonthlyState.copy(
                            loadStatus = Success(Unit),
                            rawTrxs = trxs,
                            trxRecordsByDate = filteredRecords
                        ))
                    )
                }
            } catch (e: Exception) {
                log(e)
                updateMonthlyState(month) {
                    it.copy(loadStatus = Failure(e))
                }
            }
        }
    }

    fun loadAccounts() {
        viewModelScope.launch {
            try {
                val accounts = accountRepository.getAllTrxAccountsIncludingDeleted()

                _uiState.update {
                    it.copy(accounts = accounts)
                }
            } catch (e: Exception) {
                this@TrxListViewModel.log(e)
            }
        }
    }

    fun loadCategories() {
        viewModelScope.launch {
            try {
                val categories = categoryRepository.getAllCategories()
                    .sortedWith(compareBy({ it.parent?.name ?: it.name }, { it.parent != null }, { it.name }))

                val incomeCategories = categories.filter { it.type == TrxType.Income }
                    .sortedWith(compareBy({ it.parent?.name ?: it.name }, { it.parent != null }, { it.name }))
                val expenseCategories = categories.filter { it.type == TrxType.Expense }
                    .sortedWith(compareBy({ it.parent?.name ?: it.name }, { it.parent != null }, { it.name }))

                _uiState.update {
                    it.copy(
                        incomeCategories = incomeCategories,
                        expenseCategories = expenseCategories,
                    )
                }
            } catch (e: Exception) {
                this@TrxListViewModel.log(e)
            }
        }
    }

    fun applyFilters(
        accountIds: Set<String>,
        categoryIds: Set<String>,
        accountTypes: Set<AccountType>,
        trxTypes: Set<TrxType>,
    ) {
        _uiState.update {
            val newState = it.copy(
                filterAccountIds = accountIds,
                filterAccountTypes = accountTypes,
                filterCategoryIds = categoryIds,
                filterTrxTypes = trxTypes,
            )

            // Re-process every month currently in the state using the new filters
            val stateByMonth = newState.stateByMonth.mapValues { (_, monthlyState) ->
                filterAndGroupTransactions(
                    trxs = monthlyState.rawTrxs,
                    accountIds = accountIds,
                    accountTypes = accountTypes,
                    categoryIds = categoryIds,
                    trxTypes = trxTypes,
                ).let { filteredRecords ->
                    monthlyState.copy(trxRecordsByDate = filteredRecords)
                }
            }

            newState.copy(stateByMonth = stateByMonth)
        }
    }

    fun copyTrx(id: String) {
        viewModelScope.launch {
            try {
                val trx = trxRepository.getTrxById(id) ?: throw Exception("Trx not found")
                if (trx is Trx.Adjustment || (trx as? Trx.Expense)?.installment != null) {
                    throw UnsupportedOperationException("Transaction cannot be copied")
                }
                val newId = trxRepository.addTrx(
                    type = trx.type,
                    transactionAt = Clock.System.now(),
                    amount = trx.amount,
                    description = trx.description,
                    sourceAccount = trx.sourceAccount,
                    targetAccount = (trx as? Trx.Transfer)?.targetAccount,
                    category = trx.category,
                )
                val newTrx = trxRepository.getTrxById(newId)
                    ?: throw Exception("Failed to retrieve copied transaction")
                appEventBus.emit(AppEvent.TrxChanged.Copied(newTrx))
            } catch (e: Exception) {
                this@TrxListViewModel.log(e)
            }
        }
    }

    fun deleteTrx(id: String) {
        viewModelScope.launch {
            try {
                val trx = trxRepository.getTrxById(id) ?: throw Exception("Trx not found")
                val charge = (trx as? Trx.Expense)?.installment
                if (charge is InstallmentInfo.Charge) {
                    installmentRepository.deleteInstallment(charge.installmentId)
                } else {
                    trxRepository.deleteTrx(id)
                }
            } catch (e: Exception) {
                this@TrxListViewModel.log(e)
            }
        }
    }

    private fun filterAndGroupTransactions(
        trxs: List<Trx>,
        accountIds: Set<String>,
        accountTypes: Set<AccountType>,
        categoryIds: Set<String>,
        trxTypes: Set<TrxType>,
    ): Map<LocalDate, DailyTrxRecord> {
        return groupTrxsByDate(
            trxs.filter { trx ->
                val matchAccount = accountIds.isEmpty()
                        || accountIds.contains(trx.sourceAccount.id)
                        || (trx as? Trx.Transfer)?.let { accountIds.contains(it.targetAccount.id) } ?: false
                val matchCategory = categoryIds.isEmpty()
                        || trx.category?.let { categoryIds.contains(it.id) } ?: false
                val sourceType = when (val account = trx.sourceAccount) {
                    is TrxAccount.Regular -> account.account.type
                    is TrxAccount.Credit -> AccountType.Credit
                }
                val targetType = (trx as? Trx.Transfer)?.let {
                    when (val account = it.targetAccount) {
                        is TrxAccount.Regular -> account.account.type
                        is TrxAccount.Credit -> AccountType.Credit
                    }
                }
                val matchAccountType = accountTypes.isEmpty()
                        || accountTypes.contains(sourceType)
                        || (targetType != null && accountTypes.contains(targetType))
                val matchTrxType = trxTypes.isEmpty() || trxTypes.contains(trx.type)

                matchAccount && matchCategory && matchAccountType && matchTrxType
            }
        )
    }

    private fun updateMonthlyState(
        month: YearMonth,
        transform: (TrxListUiState.MonthlyState) -> TrxListUiState.MonthlyState
    ) {
        _uiState.update { currentState ->
            val currentMonthState = currentState.stateByMonth[month] ?: TrxListUiState.MonthlyState()
            currentState.copy(
                stateByMonth = currentState.stateByMonth + (month to transform(currentMonthState))
            )
        }
    }
}