package dev.nichidori.saku.feature.trx

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.nichidori.saku.core.model.Status
import dev.nichidori.saku.core.model.Status.*
import dev.nichidori.saku.core.util.log
import dev.nichidori.saku.core.util.toRupiah
import dev.nichidori.saku.domain.model.Category
import dev.nichidori.saku.domain.model.InstallmentInfo
import dev.nichidori.saku.domain.model.Trx
import dev.nichidori.saku.domain.model.TrxAccount
import dev.nichidori.saku.domain.model.TrxType
import dev.nichidori.saku.domain.repo.AccountRepository
import dev.nichidori.saku.domain.repo.CategoryRepository
import dev.nichidori.saku.domain.repo.InstallmentRepository
import dev.nichidori.saku.domain.repo.TrxRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

data class TrxUiState(
    val isLoading: Boolean = false,
    val type: TrxType = TrxType.Expense,
    val time: Instant? = null,
    val amount: Long? = null,
    val description: String = "",
    val sourceAccount: TrxAccount? = null,
    val targetAccount: TrxAccount? = null,
    val category: Category? = null,
    val enableFee: Boolean = false,
    val feeAmount: Long? = null,
    val feeAccount: TrxAccount? = null,
    val feeCategory: Category? = null,
    val enableInstallment: Boolean = false,
    val months: Int? = null,
    val monthlyRatePercent: String = "",
    val installment: InstallmentInfo? = null,
    val accountOptions: List<TrxAccount> = listOf(),
    val incomesByParent: Map<Category, List<Category>> = emptyMap(),
    val expensesByParent: Map<Category, List<Category>> = emptyMap(),
    val canCopy: Boolean = false,
    val canDelete: Boolean = false,
    val saveStatus: Status<Unit, Exception> = Initial,
    val deleteStatus: Status<Trx, Exception> = Initial,
    val copyStatus: Status<Unit, Exception> = Initial,
) {
    val categoriesByParent = when (type) {
        TrxType.Income -> incomesByParent
        TrxType.Expense -> expensesByParent
        else -> emptyMap()
    }
    val amountFormatted = (amount ?: 0).toRupiah()
    val feeAmountFormatted = (feeAmount ?: 0).toRupiah()
    val monthsFormatted = months?.toString().orEmpty()
    val monthlyRateFormatted = if (monthlyRatePercent.isNotEmpty()) "$monthlyRatePercent%" else ""
    val isReadOnly: Boolean
        get() = installment != null || isAdjustment
    val isAdjustment = type == TrxType.Adjustment
    val canSave = !isReadOnly
            && time != null
            && amount != null
            && sourceAccount != null
            && (if (type == TrxType.Transfer) targetAccount != null else true)
            && (if (type != TrxType.Transfer) category != null else true)
            && (if (type == TrxType.Transfer && enableFee) feeAmount != null && feeAmount > 0 && feeAccount != null && feeCategory != null else true)
            && (if (enableInstallment && sourceAccount is TrxAccount.Credit) months != null && months > 0 && monthlyRatePercent.trimEnd('.').toDoubleOrNull() != null else true)
}

class TrxViewModel(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val trxRepository: TrxRepository,
    private val installmentRepository: InstallmentRepository,
    private val id: String?
) : ViewModel() {
    private val _uiState = MutableStateFlow(TrxUiState())
    val uiState: StateFlow<TrxUiState> = _uiState.asStateFlow()

    val types = TrxType.entries.filter { it != TrxType.Adjustment }

    init {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true)
            }
            val accounts = accountRepository.getAllTrxAccounts()
            val categories = categoryRepository.getAllCategories()
            val (parents, children) = categories.partition { it.parent == null }
            val childrenByParentId = children.groupBy { it.parent?.id }
            val incomesByParent = parents
                .filter { it.type == TrxType.Income }
                .associateWith { childrenByParentId[it.id].orEmpty() }
            val expensesByParent = parents
                .filter { it.type == TrxType.Expense }
                .associateWith { childrenByParentId[it.id].orEmpty() }
            val trx = id?.let { trxRepository.getTrxById(id) }
            val installment = (trx as? Trx.Expense)?.installment
            val plan = if (installment is InstallmentInfo.Charge) {
                installmentRepository.getInstallmentById(installment.installmentId)
            } else null
            _uiState.update {
                with(trx) {
                    it.copy(
                        isLoading = false,
                        type = when (this) {
                            is Trx.Income -> TrxType.Income
                            is Trx.Expense -> TrxType.Expense
                            is Trx.Transfer -> TrxType.Transfer
                            is Trx.Adjustment -> TrxType.Adjustment
                            null -> it.type
                        },
                        time = this?.transactionAt ?: Clock.System.now(),
                        amount = this?.amount ?: it.amount,
                        description = this?.description ?: it.description,
                        sourceAccount = this?.sourceAccount ?: it.sourceAccount,
                        targetAccount = (this as? Trx.Transfer)?.targetAccount ?: it.targetAccount,
                        category = this?.category ?: it.category,
                        accountOptions = accounts,
                        incomesByParent = incomesByParent,
                        expensesByParent = expensesByParent,
                        installment = installment,
                        months = plan?.months ?: it.months,
                        monthlyRatePercent = plan?.let { plan -> formatRate(plan.monthlyRatePercent) } ?: it.monthlyRatePercent,
                        canCopy = this != null && this !is Trx.Adjustment
                                && (this as? Trx.Expense)?.installment == null,
                        canDelete = this != null && (this as? Trx.Expense)?.installment !is InstallmentInfo.Installment
                    )
                }
            }
        }
    }

    fun onTypeChange(newValue: TrxType) {
        _uiState.update {
            it.copy(
                type = newValue,
                category = null,
                targetAccount = null,
                enableInstallment = false,
                months = null,
                monthlyRatePercent = ""
            )
        }
    }

    fun onTimeChange(newValue: Instant) {
        _uiState.update { it.copy(time = newValue) }
    }

    fun onAmountChange(change: (String) -> String) {
        _uiState.update { currState ->
            val current = currState.amount?.toString().orEmpty()
            currState.copy(amount = change(current).toLongOrNull())
        }
    }

    fun onDescriptionChange(newValue: String) {
        _uiState.update { it.copy(description = newValue) }
    }

    fun onSourceAccountChange(newValue: TrxAccount) {
        _uiState.update { it.copy(sourceAccount = newValue) }
    }

    fun onTargetAccountChange(newValue: TrxAccount) {
        _uiState.update { it.copy(targetAccount = newValue) }
    }

    fun onCategoryChange(newValue: Category) {
        _uiState.update { it.copy(category = newValue) }
    }

    fun onEnableFeeToggle() {
        _uiState.update {
            val enableFee = !it.enableFee
            it.copy(
                enableFee = enableFee,
                feeAmount = null,
                feeAccount = if (enableFee) it.sourceAccount else null,
                feeCategory = null,
            )
        }
    }

    fun onFeeAmountChange(change: (String) -> String) {
        _uiState.update { currState ->
            val current = currState.feeAmount?.toString().orEmpty()
            currState.copy(feeAmount = change(current).toLongOrNull())
        }
    }

    fun onFeeAccountChange(newValue: TrxAccount) {
        _uiState.update { it.copy(feeAccount = newValue) }
    }

    fun onFeeCategoryChange(newValue: Category) {
        _uiState.update { it.copy(feeCategory = newValue) }
    }

    fun onEnableInstallmentToggle() {
        _uiState.update {
            val enableInstallment = !it.enableInstallment
            it.copy(
                enableInstallment = enableInstallment,
                months = null,
                monthlyRatePercent = "",
            )
        }
    }

    fun onMonthsChange(change: (String) -> String) {
        _uiState.update { currState ->
            val current = currState.months?.toString().orEmpty()
            currState.copy(months = change(current).toIntOrNull())
        }
    }

    fun onMonthlyRateChange(change: (String) -> String) {
        _uiState.update { currState ->
            val current = currState.monthlyRatePercent
            currState.copy(monthlyRatePercent = sanitizeRate(change(current)))
        }
    }

    private fun sanitizeRate(raw: String): String {
        val out = StringBuilder()
        var dotSeen = false
        for (c in raw) {
            when {
                c.isDigit() -> out.append(c)
                c == '.' && !dotSeen -> {
                    dotSeen = true
                    out.append('.')
                }
            }
        }
        return out.toString()
    }

    private fun formatRate(percent: Double): String {
        return if (percent % 1.0 == 0.0) {
            percent.toLong().toString()
        } else {
            percent.toString()
        }
    }

    fun saveTrx() {
        viewModelScope.launch {
            try {
                if (uiState.value.time == null) throw Exception("Time cannot be empty")
                if (uiState.value.amount == null) throw Exception("Amount cannot be empty")
                if (uiState.value.sourceAccount == null) throw Exception("Source account cannot be empty")
                if (uiState.value.type == TrxType.Transfer) {
                    if (uiState.value.targetAccount == null) throw Exception("Target account cannot be empty")
                    if (uiState.value.targetAccount == uiState.value.sourceAccount) throw Exception("Target account cannot be same as source account")
                    if (uiState.value.enableFee) {
                        if ((uiState.value.feeAmount ?: 0) <= 0) throw Exception("Fee amount cannot be empty")
                        if (uiState.value.feeAccount == null) throw Exception("Fee account cannot be empty")
                        if (uiState.value.feeCategory == null) throw Exception("Fee category cannot be empty")
                    }
                } else {
                    if (uiState.value.category == null) throw Exception("Category cannot be empty")
                }
                _uiState.update { it.copy(saveStatus = Loading) }
                if (id == null) {
                    _uiState.value.let {
                        if (it.type == TrxType.Expense
                            && it.sourceAccount is TrxAccount.Credit
                            && it.enableInstallment
                        ) {
                            val months = it.months ?: throw Exception("Months cannot be empty")
                            val rate = it.monthlyRatePercent.trimEnd('.').toDoubleOrNull()
                                ?: throw Exception("Interest rate cannot be empty")
                            installmentRepository.createInstallment(
                                description = it.description,
                                category = it.category!!,
                                credit = it.sourceAccount.credit,
                                principal = it.amount!!,
                                months = months,
                                monthlyRatePercent = rate,
                                purchaseAt = it.time!!,
                            )
                        } else {
                            trxRepository.addTrx(
                                type = it.type,
                                transactionAt = it.time!!,
                                amount = it.amount!!,
                                description = it.description,
                                sourceAccount = it.sourceAccount!!,
                                targetAccount = it.targetAccount,
                                category = it.category,
                            )
                            if (it.type == TrxType.Transfer && it.enableFee) {
                                trxRepository.addTrx(
                                    type = TrxType.Expense,
                                    transactionAt = it.time + 1.milliseconds,
                                    amount = it.feeAmount!!,
                                    description = "",
                                    sourceAccount = it.feeAccount!!,
                                    targetAccount = null,
                                    category = it.feeCategory,
                                )
                            }
                        }
                    }
                } else {
                    _uiState.value.let {
                        trxRepository.updateTrx(
                            id = id,
                            type = it.type,
                            transactionAt = it.time!!,
                            amount = it.amount!!,
                            description = it.description,
                            sourceAccount = it.sourceAccount!!,
                            targetAccount = it.targetAccount,
                            category = it.category,
                        )
                    }
                }
                _uiState.update { it.copy(saveStatus = Success(Unit)) }
            } catch (e: Exception) {
                this@TrxViewModel.log(e)
                _uiState.update { it.copy(saveStatus = Failure(e)) }
            }
        }
    }

    fun deleteTrx() {
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(deleteStatus = Loading)
                }
                val trxId = id ?: throw Exception("Trx id is null")
                val trx = trxRepository.getTrxById(trxId) ?: throw Exception("Trx not found")
                val charge = (trx as? Trx.Expense)?.installment
                if (charge is InstallmentInfo.Charge) {
                    installmentRepository.deleteInstallment(charge.installmentId)
                } else {
                    trxRepository.deleteTrx(trxId)
                }
                _uiState.update {
                    it.copy(deleteStatus = Success(trx))
                }
            } catch (e: Exception) {
                this@TrxViewModel.log(e)
                _uiState.update {
                    it.copy(deleteStatus = Failure(e))
                }
            }
        }
    }

    fun copyTrx() {
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(copyStatus = Loading)
                }
                val trxId = id ?: throw Exception("Trx id is null")
                val trx = trxRepository.getTrxById(trxId) ?: throw Exception("Trx not found")
                if (trx is Trx.Adjustment || (trx as? Trx.Expense)?.installment != null) {
                    throw UnsupportedOperationException("Transaction cannot be copied")
                }
                trxRepository.addTrx(
                    type = trx.type,
                    transactionAt = trx.transactionAt,
                    amount = trx.amount,
                    description = trx.description,
                    sourceAccount = trx.sourceAccount,
                    targetAccount = (trx as? Trx.Transfer)?.targetAccount,
                    category = trx.category,
                )
                _uiState.update {
                    it.copy(copyStatus = Success(Unit))
                }
            } catch (e: Exception) {
                this@TrxViewModel.log(e)
                _uiState.update {
                    it.copy(copyStatus = Failure(e))
                }
            }
        }
    }
}
