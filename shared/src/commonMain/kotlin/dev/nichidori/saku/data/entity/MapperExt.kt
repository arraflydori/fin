package dev.nichidori.saku.data.entity

import dev.nichidori.saku.domain.model.*
import kotlinx.datetime.YearMonth
import kotlinx.datetime.number
import kotlin.time.Instant

fun AccountEntity.toDomain(): Account = Account(
    id = id,
    name = name,
    currentAmount = currentAmount,
    type = type.toDomain(),
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = updatedAt?.let { Instant.fromEpochMilliseconds(it) },
    deletedAt = deletedAt?.let { Instant.fromEpochMilliseconds(it) }
)

fun Account.toEntity(): AccountEntity = AccountEntity(
    id = id,
    name = name,
    currentAmount = currentAmount,
    type = type.toEntity(),
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt?.toEpochMilliseconds(),
    deletedAt = deletedAt?.toEpochMilliseconds()
)

fun CreditEntity.toDomain(): Credit = Credit(
    id = id,
    name = name,
    limit = limit,
    currentAmount = currentAmount,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = updatedAt?.let { Instant.fromEpochMilliseconds(it) }
)

fun Credit.toEntity(): CreditEntity = CreditEntity(
    id = id,
    name = name,
    limit = limit,
    currentAmount = currentAmount,
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt?.toEpochMilliseconds()
)

fun InstallmentEntity.toDomain(category: Category, credit: Credit): Installment = Installment(
    id = id,
    description = description,
    category = category,
    credit = credit,
    principal = principal,
    months = months,
    monthlyRatePercent = monthlyRatePercent,
    totalAmount = totalAmount,
    monthlyPayment = monthlyPayment,
    lastPayment = lastPayment,
    startAt = Instant.fromEpochMilliseconds(startAt),
    dueDay = dueDay,
    nextIndex = nextIndex,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = updatedAt?.let { Instant.fromEpochMilliseconds(it) }
)

fun Installment.toEntity(): InstallmentEntity = InstallmentEntity(
    id = id,
    description = description,
    categoryId = category.id,
    creditId = credit.id,
    principal = principal,
    months = months,
    monthlyRatePercent = monthlyRatePercent,
    totalAmount = totalAmount,
    monthlyPayment = monthlyPayment,
    lastPayment = lastPayment,
    startAt = startAt.toEpochMilliseconds(),
    dueDay = dueDay,
    nextIndex = nextIndex,
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt?.toEpochMilliseconds()
)

fun MonthlyNetWorthEntity.toDomain(): MonthlyNetWorth = MonthlyNetWorth(
    month = YearMonth(year, month),
    netWorth = netWorth,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = updatedAt?.let { Instant.fromEpochMilliseconds(it) }
)

fun MonthlyNetWorth.toEntity(): MonthlyNetWorthEntity = MonthlyNetWorthEntity(
    year = month.year,
    month = month.month.number,
    netWorth = netWorth,
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt?.toEpochMilliseconds()
)

fun CategoryEntity.toDomain(parent: Category? = null): Category = Category(
    id = id,
    name = name,
    type = type.toDomain(),
    parent = parent,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = updatedAt?.let { Instant.fromEpochMilliseconds(it) },
    icon = icon,
    sortOrder = sortOrder
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    type = type.toEntity(),
    parentId = parent?.id,
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt?.toEpochMilliseconds(),
    icon = icon,
    sortOrder = sortOrder
)

fun AccountTypeEntity.toDomain(): AccountType = when (this) {
    AccountTypeEntity.Cash -> AccountType.Cash
    AccountTypeEntity.Bank -> AccountType.Bank
    AccountTypeEntity.Ewallet -> AccountType.Ewallet
    AccountTypeEntity.Emoney -> AccountType.Emoney
}

fun AccountType.toEntity(): AccountTypeEntity = when (this) {
    AccountType.Cash -> AccountTypeEntity.Cash
    AccountType.Bank -> AccountTypeEntity.Bank
    AccountType.Credit -> throw IllegalStateException("Credit type cannot be persisted to account table")
    AccountType.Ewallet -> AccountTypeEntity.Ewallet
    AccountType.Emoney -> AccountTypeEntity.Emoney
}

fun TrxEntity.toDomain(
    category: Category?,
    sourceAccount: TrxAccount,
    targetAccount: TrxAccount? = null,
): Trx = when (type) {
    TrxTypeEntity.Income -> Trx.Income(
        id = id,
        description = description,
        amount = amount,
        category = category,
        sourceAccount = sourceAccount,
        transactionAt = Instant.fromEpochMilliseconds(transactionAt),
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        updatedAt = updatedAt?.let { Instant.fromEpochMilliseconds(it) },
    )

    TrxTypeEntity.Expense -> Trx.Expense(
        id = id,
        description = description,
        amount = amount,
        category = category,
        sourceAccount = sourceAccount,
        transactionAt = Instant.fromEpochMilliseconds(transactionAt),
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        updatedAt = updatedAt?.let { Instant.fromEpochMilliseconds(it) },
        installment = null
    )

    TrxTypeEntity.Transfer -> Trx.Transfer(
        id = id,
        description = description,
        amount = amount,
        category = category,
        sourceAccount = sourceAccount,
        targetAccount = requireNotNull(targetAccount),
        transactionAt = Instant.fromEpochMilliseconds(transactionAt),
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        updatedAt = updatedAt?.let { Instant.fromEpochMilliseconds(it) },
    )

    TrxTypeEntity.Adjustment -> Trx.Adjustment(
        id = id,
        description = description,
        amount = amount,
        sourceAccount = sourceAccount,
        transactionAt = Instant.fromEpochMilliseconds(transactionAt),
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        updatedAt = updatedAt?.let { Instant.fromEpochMilliseconds(it) },
    )
}

fun Trx.toEntity(): TrxEntity = when (this) {
    is Trx.Income -> TrxEntity(
        id = id,
        description = description,
        amount = amount,
        categoryId = category?.id,
        sourceAccountId = (sourceAccount as? TrxAccount.Regular)?.account?.id,
        sourceCreditId = (sourceAccount as? TrxAccount.Credit)?.credit?.id,
        targetAccountId = null,
        targetCreditId = null,
        transactionAt = transactionAt.toEpochMilliseconds(),
        createdAt = createdAt.toEpochMilliseconds(),
        updatedAt = updatedAt?.toEpochMilliseconds(),
        type = TrxTypeEntity.Income,
    )
    is Trx.Expense -> TrxEntity(
        id = id,
        description = description,
        amount = amount,
        categoryId = category?.id,
        sourceAccountId = (sourceAccount as? TrxAccount.Regular)?.account?.id,
        sourceCreditId = (sourceAccount as? TrxAccount.Credit)?.credit?.id,
        targetAccountId = null,
        targetCreditId = null,
        transactionAt = transactionAt.toEpochMilliseconds(),
        createdAt = createdAt.toEpochMilliseconds(),
        updatedAt = updatedAt?.toEpochMilliseconds(),
        type = TrxTypeEntity.Expense,
        installmentId = installment?.installmentId,
        installmentIndex = (installment as? InstallmentInfo.Installment)?.index,
    )
    is Trx.Transfer -> TrxEntity(
        id = id,
        description = description,
        amount = amount,
        categoryId = null,
        sourceAccountId = (sourceAccount as? TrxAccount.Regular)?.account?.id,
        sourceCreditId = (sourceAccount as? TrxAccount.Credit)?.credit?.id,
        targetAccountId = (targetAccount as? TrxAccount.Regular)?.account?.id,
        targetCreditId = (targetAccount as? TrxAccount.Credit)?.credit?.id,
        transactionAt = transactionAt.toEpochMilliseconds(),
        createdAt = createdAt.toEpochMilliseconds(),
        updatedAt = updatedAt?.toEpochMilliseconds(),
        type = TrxTypeEntity.Transfer,
    )

    is Trx.Adjustment -> TrxEntity(
        id = id,
        description = description,
        amount = amount,
        categoryId = null,
        sourceAccountId = (sourceAccount as? TrxAccount.Regular)?.account?.id,
        sourceCreditId = (sourceAccount as? TrxAccount.Credit)?.credit?.id,
        targetAccountId = null,
        targetCreditId = null,
        transactionAt = transactionAt.toEpochMilliseconds(),
        createdAt = createdAt.toEpochMilliseconds(),
        updatedAt = updatedAt?.toEpochMilliseconds(),
        type = TrxTypeEntity.Adjustment,
    )
}

fun TrxTypeEntity.toDomain(): TrxType = when (this) {
    TrxTypeEntity.Income -> TrxType.Income
    TrxTypeEntity.Expense -> TrxType.Expense
    TrxTypeEntity.Transfer -> TrxType.Transfer
    TrxTypeEntity.Adjustment -> TrxType.Adjustment
}

fun TrxType.toEntity(): TrxTypeEntity = when (this) {
    TrxType.Income -> TrxTypeEntity.Income
    TrxType.Expense -> TrxTypeEntity.Expense
    TrxType.Transfer -> TrxTypeEntity.Transfer
    TrxType.Adjustment -> TrxTypeEntity.Adjustment
}

fun TrxWithDetailsEntity.toDomain(): Trx {
    val source = resolveSourceAccount()
        ?: error("Source account not found for trx ${trx.id}")
    val target = resolveTargetAccount()

    return when (trx.type) {
        TrxTypeEntity.Income -> Trx.Income(
            id = trx.id,
            description = trx.description,
            amount = trx.amount,
            sourceAccount = source,
            transactionAt = Instant.fromEpochMilliseconds(trx.transactionAt),
            category = categoryWithParent?.category?.toDomain(
                parent = categoryWithParent.parent?.toDomain()
            ),
            createdAt = Instant.fromEpochMilliseconds(trx.createdAt),
            updatedAt = trx.updatedAt?.let { Instant.fromEpochMilliseconds(it) },
        )

        TrxTypeEntity.Expense -> Trx.Expense(
            id = trx.id,
            description = trx.description,
            amount = trx.amount,
            sourceAccount = source,
            transactionAt = Instant.fromEpochMilliseconds(trx.transactionAt),
            category = categoryWithParent?.category?.toDomain(
                parent = categoryWithParent.parent?.toDomain()
            ),
            createdAt = Instant.fromEpochMilliseconds(trx.createdAt),
            updatedAt = trx.updatedAt?.let { Instant.fromEpochMilliseconds(it) },
            installment = when {
                trx.installmentId == null -> null
                trx.installmentIndex == null -> InstallmentInfo.Charge(
                    installmentId = trx.installmentId,
                    totalMonths = installmentPlan?.months ?: 0
                )
                else -> InstallmentInfo.Installment(
                    installmentId = trx.installmentId,
                    index = trx.installmentIndex,
                    totalMonths = installmentPlan?.months ?: 0
                )
            },
        )

        TrxTypeEntity.Transfer -> Trx.Transfer(
            id = trx.id,
            description = trx.description,
            amount = trx.amount,
            sourceAccount = source,
            targetAccount = checkNotNull(target),
            transactionAt = Instant.fromEpochMilliseconds(trx.transactionAt),
            category = null,
            createdAt = Instant.fromEpochMilliseconds(trx.createdAt),
            updatedAt = trx.updatedAt?.let { Instant.fromEpochMilliseconds(it) },
        )

        TrxTypeEntity.Adjustment -> Trx.Adjustment(
            id = trx.id,
            description = trx.description,
            amount = trx.amount,
            sourceAccount = source,
            transactionAt = Instant.fromEpochMilliseconds(trx.transactionAt),
            createdAt = Instant.fromEpochMilliseconds(trx.createdAt),
            updatedAt = trx.updatedAt?.let { Instant.fromEpochMilliseconds(it) },
        )
    }
}

private fun TrxWithDetailsEntity.resolveSourceAccount(): TrxAccount? {
    return sourceAccount?.toDomain()?.let { TrxAccount.Regular(it) }
        ?: sourceCredit?.toDomain()?.let { TrxAccount.Credit(it) }
}

private fun TrxWithDetailsEntity.resolveTargetAccount(): TrxAccount? {
    return targetAccount?.toDomain()?.let { TrxAccount.Regular(it) }
        ?: targetCredit?.toDomain()?.let { TrxAccount.Credit(it) }
}

fun BudgetEntity.toDomain(category: Category): Budget = Budget(
    id = id,
    templateId = templateId,
    category = category,
    month = YearMonth(year, month),
    baseAmount = baseAmount,
    spentAmount = spentAmount,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = updatedAt?.let { Instant.fromEpochMilliseconds(it) }
)

fun BudgetWithCategoryEntity.toDomain(): Budget {
    return budget.toDomain(category.toDomain())
}

fun Budget.toEntity(): BudgetEntity = BudgetEntity(
    id = id,
    templateId = templateId,
    categoryId = category.id,
    month = month.month.number,
    year = month.year,
    baseAmount = baseAmount,
    spentAmount = spentAmount,
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt?.toEpochMilliseconds()
)

fun TrxTemplateEntity.toDomain(
    category: Category?,
    sourceAccount: TrxAccount,
    targetAccount: TrxAccount?,
): TrxTemplate = TrxTemplate(
    id = id,
    name = name,
    type = type.toDomain(),
    description = description,
    amount = amount,
    category = category,
    sourceAccount = sourceAccount,
    targetAccount = targetAccount,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = updatedAt?.let { Instant.fromEpochMilliseconds(it) }
)

fun TrxTemplateWithDetailsEntity.toDomain(): TrxTemplate {
    val source = resolveSourceAccount()
        ?: error("Source account not found for trx template ${trxTemplate.id}")
    val target = resolveTargetAccount()

    return TrxTemplate(
        id = trxTemplate.id,
        name = trxTemplate.name,
        type = trxTemplate.type.toDomain(),
        description = trxTemplate.description,
        amount = trxTemplate.amount,
        category = categoryWithParent?.category?.toDomain(
            parent = categoryWithParent.parent?.toDomain()
        ),
        sourceAccount = source,
        targetAccount = target,
        createdAt = Instant.fromEpochMilliseconds(trxTemplate.createdAt),
        updatedAt = trxTemplate.updatedAt?.let { Instant.fromEpochMilliseconds(it) }
    )
}

fun TrxTemplate.toEntity(): TrxTemplateEntity = TrxTemplateEntity(
    id = id,
    name = name,
    type = type.toEntity(),
    description = description,
    amount = amount,
    categoryId = category?.id,
    sourceAccountId = (sourceAccount as? TrxAccount.Regular)?.account?.id,
    sourceCreditId = (sourceAccount as? TrxAccount.Credit)?.credit?.id,
    targetAccountId = (targetAccount as? TrxAccount.Regular)?.account?.id,
    targetCreditId = (targetAccount as? TrxAccount.Credit)?.credit?.id,
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt?.toEpochMilliseconds()
)

private fun TrxTemplateWithDetailsEntity.resolveSourceAccount(): TrxAccount? {
    return sourceAccount?.toDomain()?.let { TrxAccount.Regular(it) }
        ?: sourceCredit?.toDomain()?.let { TrxAccount.Credit(it) }
}

private fun TrxTemplateWithDetailsEntity.resolveTargetAccount(): TrxAccount? {
    return targetAccount?.toDomain()?.let { TrxAccount.Regular(it) }
        ?: targetCredit?.toDomain()?.let { TrxAccount.Credit(it) }
}

fun BudgetTemplateEntity.toDomain(category: Category): BudgetTemplate = BudgetTemplate(
    id = id,
    category = category,
    defaultAmount = defaultAmount,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = updatedAt?.let { Instant.fromEpochMilliseconds(it) }
)

fun BudgetTemplateWithCategoryEntity.toDomain(): BudgetTemplate {
    return budgetTemplate.toDomain(category.toDomain())
}

fun BudgetTemplate.toEntity(): BudgetTemplateEntity = BudgetTemplateEntity(
    id = id,
    categoryId = category.id,
    defaultAmount = defaultAmount,
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt?.toEpochMilliseconds()
)


