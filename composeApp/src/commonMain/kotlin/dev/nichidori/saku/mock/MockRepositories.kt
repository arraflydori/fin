package dev.nichidori.saku.mock

import dev.nichidori.saku.core.event.AppEvent
import dev.nichidori.saku.core.event.AppEventBus
import dev.nichidori.saku.domain.model.Account
import dev.nichidori.saku.domain.model.AccountType
import dev.nichidori.saku.domain.model.Budget
import dev.nichidori.saku.domain.model.BudgetTemplate
import dev.nichidori.saku.domain.model.Category
import dev.nichidori.saku.domain.model.Credit
import dev.nichidori.saku.domain.model.Installment
import dev.nichidori.saku.domain.model.InstallmentInfo
import dev.nichidori.saku.domain.model.Trx
import dev.nichidori.saku.domain.model.TrxAccount
import dev.nichidori.saku.domain.model.TrxFilter
import dev.nichidori.saku.domain.model.TrxTemplate
import dev.nichidori.saku.domain.model.TrxType
import dev.nichidori.saku.domain.repo.AccountRepository
import dev.nichidori.saku.domain.repo.BudgetRepository
import dev.nichidori.saku.domain.repo.CategoryRepository
import dev.nichidori.saku.domain.repo.InstallmentRepository
import dev.nichidori.saku.domain.repo.TrxRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private val mockTz: TimeZone get() = TimeZone.currentSystemDefault()

private fun Instant.monthOf(): YearMonth =
    toLocalDateTime(mockTz).let { YearMonth(it.year, it.monthNumber) }

class MockAccountRepository(
    private val data: MockData,
    private val appEventBus: AppEventBus,
) : AccountRepository {

    override suspend fun addAccount(name: String, currentAmount: Long, type: AccountType) {
        data.accounts.add(
            Account(
                id = data.newEntityId("acc"),
                name = name,
                currentAmount = currentAmount,
                type = type,
                createdAt = Clock.System.now(),
                updatedAt = null,
            )
        )
    }

    override suspend fun getAccountById(id: String): Account? {
        return data.accounts.firstOrNull { it.id == id }
    }

    override suspend fun getAllAccounts(): List<Account> {
        return data.accounts.toList()
    }

    override suspend fun updateAccount(id: String, name: String, type: AccountType) {
        data.accounts.firstOrNull { it.id == id }
            ?: throw NoSuchElementException("Account not found")
        data.accounts.replaceAll {
            if (it.id == id) it.copy(name = name, type = type, updatedAt = Clock.System.now()) else it
        }
    }

    override suspend fun deleteAccount(id: String) {
        val existing = data.accounts.firstOrNull { it.id == id }
            ?: throw NoSuchElementException("Account not found")
        data.accounts.replaceAll {
            if (it.id == id) {
                it.copy(currentAmount = 0L, deletedAt = Clock.System.now(), updatedAt = Clock.System.now())
            } else {
                it
            }
        }
        appEventBus.emit(AppEvent.AccountChanged.Deleted(existing.id))
    }

    override suspend fun getTotalBalance(): Long {
        return data.accounts.filter { !it.isDeleted }.sumOf { it.currentAmount }
    }

    override suspend fun getAllTrxAccounts(): List<TrxAccount> {
        return data.accounts.filter { !it.isDeleted }.map { TrxAccount.Regular(it) } +
                data.credits.map { TrxAccount.Credit(it) }
    }

    override suspend fun getAllTrxAccountsIncludingDeleted(): List<TrxAccount> {
        return data.accounts.map { TrxAccount.Regular(it) } +
                data.credits.map { TrxAccount.Credit(it) }
    }

    override suspend fun getNetWorthHistory(months: List<YearMonth>): List<Long> {
        return data.netWorthHistory(months)
    }

    override suspend fun ensureCurrentMonthNetWorth() {
        // Net worth series is derived in-memory; nothing to persist.
    }

    override suspend fun addCredit(name: String, limit: Long, currentAmount: Long) {
        data.credits.add(
            Credit(
                id = data.newEntityId("crd"),
                name = name,
                limit = limit,
                currentAmount = currentAmount,
                createdAt = Clock.System.now(),
                updatedAt = null,
            )
        )
    }

    override suspend fun getCreditById(id: String): Credit? {
        return data.credits.firstOrNull { it.id == id }
    }

    override suspend fun updateCredit(id: String, name: String, limit: Long, currentAmount: Long) {
        data.credits.firstOrNull { it.id == id }
            ?: throw NoSuchElementException("Credit not found")
        data.credits.replaceAll {
            if (it.id == id) {
                it.copy(name = name, limit = limit, currentAmount = currentAmount, updatedAt = Clock.System.now())
            } else {
                it
            }
        }
    }

    override suspend fun deleteCredit(id: String) {
        data.credits.removeIf { it.id == id }
    }

    override suspend fun getPendingInstallmentCount(creditId: String): Int {
        return 0
    }
}

class MockCategoryRepository(
    private val data: MockData,
) : CategoryRepository {

    override suspend fun addCategory(name: String, type: TrxType, icon: String?, parent: Category?) {
        data.categories.add(
            Category(
                id = data.newEntityId("cat"),
                name = name,
                type = type,
                parent = parent?.let { resolved -> data.categories.firstOrNull { it.id == resolved.id } },
                createdAt = Clock.System.now(),
                updatedAt = null,
                icon = icon,
            )
        )
    }

    override suspend fun getCategoryById(id: String): Category? {
        return data.categories.firstOrNull { it.id == id }
    }

    override suspend fun getAllCategories(): List<Category> {
        return data.categories.toList()
    }

    override suspend fun getRootCategories(): List<Category> {
        return data.categories.filter { it.parent == null }
    }

    override suspend fun getSubcategories(parentId: String): List<Category> {
        return data.categories.filter { it.parent?.id == parentId }
    }

    override suspend fun updateCategory(id: String, name: String, type: TrxType, icon: String?, parent: Category?) {
        data.categories.firstOrNull { it.id == id }
            ?: throw NoSuchElementException("Category not found")
        val resolvedParent = parent?.let { requested ->
            data.categories.firstOrNull { candidate -> candidate.id == requested.id && candidate.id != id }
        }
        data.categories.replaceAll {
            if (it.id == id) {
                it.copy(
                    name = name,
                    type = type,
                    icon = icon,
                    parent = resolvedParent,
                    updatedAt = Clock.System.now(),
                )
            } else {
                it
            }
        }
    }

    override suspend fun deleteCategory(id: String) {
        val removedIds = collectWithChildren(id)
        data.categories.removeAll { it.id in removedIds }
    }

    private fun collectWithChildren(id: String): Set<String> {
        val result = mutableSetOf(id)
        var frontier = setOf(id)
        while (frontier.isNotEmpty()) {
            frontier = data.categories
                .filter { it.parent?.id in frontier && it.id !in result }
                .map { it.id }
                .toSet()
            result.addAll(frontier)
        }
        return result
    }
}

class MockTrxRepository(
    private val data: MockData,
    private val appEventBus: AppEventBus,
) : TrxRepository {

    override suspend fun addTrx(
        type: TrxType,
        transactionAt: Instant,
        amount: Long,
        description: String,
        sourceAccount: TrxAccount,
        targetAccount: TrxAccount?,
        category: Category?,
        installment: InstallmentInfo?,
    ): String {
        require(!(type == TrxType.Transfer && sourceAccount.id == targetAccount?.id)) {
            "Target account cannot be the same as source account"
        }

        val now = Clock.System.now()
        val trx = when (type) {
            TrxType.Income -> Trx.Income(
                id = data.newEntityId("trx"),
                description = description,
                amount = amount,
                category = category,
                sourceAccount = sourceAccount,
                transactionAt = transactionAt,
                createdAt = now,
                updatedAt = null,
            )

            TrxType.Expense -> Trx.Expense(
                id = data.newEntityId("trx"),
                description = description,
                amount = amount,
                category = category,
                sourceAccount = sourceAccount,
                transactionAt = transactionAt,
                createdAt = now,
                updatedAt = null,
                installment = installment,
            )

            TrxType.Transfer -> Trx.Transfer(
                id = data.newEntityId("trx"),
                description = description,
                amount = amount,
                category = null,
                sourceAccount = sourceAccount,
                targetAccount = targetAccount
                    ?: throw IllegalArgumentException("Transfer requires a target account"),
                transactionAt = transactionAt,
                createdAt = now,
                updatedAt = null,
            )

            TrxType.Adjustment -> Trx.Adjustment(
                id = data.newEntityId("trx"),
                description = description,
                amount = amount,
                sourceAccount = sourceAccount,
                transactionAt = transactionAt,
                createdAt = now,
                updatedAt = null,
            )
        }

        data.trxs.add(trx)
        data.applyTrxDelta(trx, sign = 1)
        if (trx is Trx.Expense) {
            data.adjustBudgetSpent(transactionAt.monthOf(), category, amount, now)
        }
        appEventBus.emit(AppEvent.TrxChanged.Created(trx))
        return trx.id
    }

    override suspend fun getTrxById(id: String): Trx? {
        return data.trxs.firstOrNull { it.id == id }
    }

    override suspend fun getFilteredTrxs(filter: TrxFilter): List<Trx> {
        return data.trxs.asSequence()
            .filter { it.transactionAt.monthOf() == filter.month }
            .filter { filter.type == null || it.type == filter.type }
            .filter { trx ->
                val wanted = filter.categoryId ?: return@filter true
                trx.category?.id == wanted
            }
            .filter { trx ->
                val accountId = filter.accountId ?: return@filter true
                trx.sourceAccount.id == accountId ||
                        (trx as? Trx.Transfer)?.targetAccount?.id == accountId
            }
            .filter { trx ->
                val accountType = filter.accountType ?: return@filter true
                when (accountType) {
                    AccountType.Credit -> trx.sourceAccount is TrxAccount.Credit
                    else -> (trx.sourceAccount as? TrxAccount.Regular)?.account?.type == accountType
                }
            }
            .filter { trx ->
                !filter.excludeInstallmentCharges || (trx as? Trx.Expense)?.installment == null
            }
            .sortedByDescending { it.transactionAt }
            .toList()
    }

    override suspend fun searchTrxsByDescription(keyword: String): List<Trx> {
        return data.trxs
            .filter { it.description.contains(keyword, ignoreCase = true) }
            .sortedByDescending { it.transactionAt }
    }

    override suspend fun updateTrx(
        id: String,
        type: TrxType,
        transactionAt: Instant,
        amount: Long,
        description: String,
        sourceAccount: TrxAccount,
        targetAccount: TrxAccount?,
        category: Category?,
        installment: InstallmentInfo?,
    ) {
        val existing = data.trxs.firstOrNull { it.id == id }
            ?: throw NoSuchElementException("Transaction not found")
        val now = Clock.System.now()

        data.applyTrxDelta(existing, sign = -1)
        if (existing is Trx.Expense) {
            data.adjustBudgetSpent(existing.transactionAt.monthOf(), existing.category, -existing.amount, now)
        }

        val updated = when (type) {
            TrxType.Income -> (existing as? Trx.Income)?.copy(
                description = description,
                amount = amount,
                category = category,
                sourceAccount = sourceAccount,
                transactionAt = transactionAt,
                updatedAt = now,
            ) ?: Trx.Income(
                id = id,
                description = description,
                amount = amount,
                category = category,
                sourceAccount = sourceAccount,
                transactionAt = transactionAt,
                createdAt = existing.createdAt,
                updatedAt = now,
            )

            TrxType.Expense -> (existing as? Trx.Expense)?.copy(
                description = description,
                amount = amount,
                category = category,
                sourceAccount = sourceAccount,
                transactionAt = transactionAt,
                updatedAt = now,
            ) ?: Trx.Expense(
                id = id,
                description = description,
                amount = amount,
                category = category,
                sourceAccount = sourceAccount,
                transactionAt = transactionAt,
                createdAt = existing.createdAt,
                updatedAt = now,
            )

            TrxType.Transfer -> Trx.Transfer(
                id = id,
                description = description,
                amount = amount,
                category = null,
                sourceAccount = sourceAccount,
                targetAccount = targetAccount
                    ?: throw IllegalArgumentException("Transfer requires a target account"),
                transactionAt = transactionAt,
                createdAt = existing.createdAt,
                updatedAt = now,
            )

            TrxType.Adjustment -> Trx.Adjustment(
                id = id,
                description = description,
                amount = amount,
                sourceAccount = sourceAccount,
                transactionAt = transactionAt,
                createdAt = existing.createdAt,
                updatedAt = now,
            )
        }

        data.trxs.replaceAll { if (it.id == id) updated else it }
        data.applyTrxDelta(updated, sign = 1)
        if (updated is Trx.Expense) {
            data.adjustBudgetSpent(transactionAt.monthOf(), category, amount, now)
        }
        appEventBus.emit(AppEvent.TrxChanged.Updated(before = existing, after = updated))
    }

    override suspend fun deleteTrx(id: String) {
        val index = data.trxs.indexOfFirst { it.id == id }
        if (index < 0) throw NoSuchElementException("Transaction not found")
        val removed = data.trxs.removeAt(index)
        val now = Clock.System.now()

        data.applyTrxDelta(removed, sign = -1)
        if (removed is Trx.Expense) {
            data.adjustBudgetSpent(removed.transactionAt.monthOf(), removed.category, -removed.amount, now)
        }
        appEventBus.emit(AppEvent.TrxChanged.Deleted(removed))
    }

    override suspend fun addTrxTemplate(
        name: String,
        type: TrxType,
        description: String,
        amount: Long,
        sourceAccount: TrxAccount,
        targetAccount: TrxAccount?,
        category: Category?,
    ) {
        data.trxTemplates.add(
            TrxTemplate(
                id = data.newEntityId("ttpl"),
                name = name,
                type = type,
                description = description,
                amount = amount,
                category = category,
                sourceAccount = sourceAccount,
                targetAccount = targetAccount,
                createdAt = Clock.System.now(),
                updatedAt = null,
            )
        )
    }

    override suspend fun getTrxTemplateById(id: String): TrxTemplate? {
        return data.trxTemplates.firstOrNull { it.id == id }
    }

    override suspend fun getAllTrxTemplates(): List<TrxTemplate> {
        return data.trxTemplates.toList()
    }

    override suspend fun updateTrxTemplate(
        id: String,
        name: String,
        type: TrxType,
        description: String,
        amount: Long,
        sourceAccount: TrxAccount,
        targetAccount: TrxAccount?,
        category: Category?,
    ) {
        data.trxTemplates.firstOrNull { it.id == id }
            ?: throw NoSuchElementException("Transaction template not found")
        data.trxTemplates.replaceAll {
            if (it.id == id) {
                it.copy(
                    name = name,
                    type = type,
                    description = description,
                    amount = amount,
                    category = category,
                    sourceAccount = sourceAccount,
                    targetAccount = targetAccount,
                    updatedAt = Clock.System.now(),
                )
            } else {
                it
            }
        }
    }

    override suspend fun deleteTrxTemplate(id: String) {
        data.trxTemplates.removeIf { it.id == id }
    }
}

class MockBudgetRepository(
    private val data: MockData,
) : BudgetRepository {

    override suspend fun addBudgetTemplate(category: Category, defaultAmount: Long) {
        data.budgetTemplates.add(
            BudgetTemplate(
                id = data.newEntityId("btpl"),
                category = category,
                defaultAmount = defaultAmount,
                createdAt = Clock.System.now(),
                updatedAt = null,
            )
        )
    }

    override suspend fun getBudgetTemplateById(id: String): BudgetTemplate? {
        return data.budgetTemplates.firstOrNull { it.id == id }
    }

    override suspend fun getBudgetTemplateByCategoryId(categoryId: String): BudgetTemplate? {
        return data.budgetTemplates.firstOrNull { it.category.id == categoryId }
    }

    override suspend fun getAllBudgetTemplates(): List<BudgetTemplate> {
        return data.budgetTemplates.toList()
    }

    override suspend fun updateBudgetTemplate(id: String, defaultAmount: Long) {
        data.budgetTemplates.firstOrNull { it.id == id }
            ?: throw NoSuchElementException("Budget template not found")
        data.budgetTemplates.replaceAll {
            if (it.id == id) {
                it.copy(defaultAmount = defaultAmount, updatedAt = Clock.System.now())
            } else {
                it
            }
        }
    }

    override suspend fun deleteBudgetTemplate(id: String) {
        data.budgetTemplates.removeIf { it.id == id }
    }

    override suspend fun ensureBudgetsExist(now: YearMonth) {
        // Monthly budgets are pre-seeded for the last 6 months.
    }

    override suspend fun getBudgetById(id: String): Budget? {
        return data.budgets.firstOrNull { it.id == id }
    }

    override suspend fun getBudgetsByYearMonth(month: YearMonth): List<Budget> {
        return data.budgets.filter { it.month == month }
    }

    override suspend fun getBudgetsByCategory(categoryId: String): List<Budget> {
        return data.budgets
            .filter { it.category.id == categoryId }
            .sortedByDescending { it.month }
    }

    override suspend fun updateBudget(id: String, baseAmount: Long, spentAmount: Long) {
        data.budgets.firstOrNull { it.id == id }
            ?: throw NoSuchElementException("Budget not found")
        data.budgets.replaceAll {
            if (it.id == id) {
                it.copy(baseAmount = baseAmount, spentAmount = spentAmount, updatedAt = Clock.System.now())
            } else {
                it
            }
        }
    }

    override suspend fun deleteBudget(id: String) {
        data.budgets.removeIf { it.id == id }
    }
}

class MockInstallmentRepository(
    @Suppress("unused") private val data: MockData,
) : InstallmentRepository {

    override suspend fun createInstallment(
        description: String,
        category: Category,
        credit: Credit,
        principal: Long,
        months: Int,
        monthlyRatePercent: Double,
        purchaseAt: Instant,
    ): String {
        throw UnsupportedOperationException("Installments are not available in demo mode")
    }

    override suspend fun getInstallmentById(id: String): Installment? {
        return null
    }

    override suspend fun getAllInstallments(): List<Installment> {
        return emptyList()
    }

    override suspend fun deleteInstallment(id: String) {
        throw NoSuchElementException("Installment not found")
    }

    override suspend fun processDueInstallments() {
        // No installments exist in demo mode.
    }
}
