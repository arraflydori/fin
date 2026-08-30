package dev.nichidori.saku.mock

import dev.nichidori.saku.domain.model.Account
import dev.nichidori.saku.domain.model.AccountType
import dev.nichidori.saku.domain.model.Budget
import dev.nichidori.saku.domain.model.BudgetTemplate
import dev.nichidori.saku.domain.model.Category
import dev.nichidori.saku.domain.model.Credit
import dev.nichidori.saku.domain.model.Trx
import dev.nichidori.saku.domain.model.TrxAccount
import dev.nichidori.saku.domain.model.TrxTemplate
import dev.nichidori.saku.domain.model.TrxType
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Hardcoded demo dataset for Play Store screenshots.
 * Dates are generated relative to "now" so the current month always looks fresh;
 * names, descriptions, and amounts are fixed English content in Rupiah.
 */
class MockData(now: Instant = Clock.System.now()) {

    private val tz: TimeZone = TimeZone.currentSystemDefault()
    private val today = now.toLocalDateTime(tz).date
    val currentMonth: YearMonth = YearMonth(today.year, today.monthNumber)

    /** 12 months ending at the current month, oldest first (matches Home net-worth window). */
    val historyMonths: List<YearMonth> =
        (11 downTo 0).map { currentMonth.minus(it, DateTimeUnit.MONTH) }

    /** Last 6 months including the current one, oldest first (budget coverage). */
    val budgetMonths: List<YearMonth> =
        (5 downTo 0).map { currentMonth.minus(it, DateTimeUnit.MONTH) }

    val accounts = mutableListOf<Account>()
    val credits = mutableListOf<Credit>()
    val categories = mutableListOf<Category>()
    val trxs = mutableListOf<Trx>()
    val budgets = mutableListOf<Budget>()
    val budgetTemplates = mutableListOf<BudgetTemplate>()
    val trxTemplates = mutableListOf<TrxTemplate>()

    private var nextId = 1
    private fun newId(prefix: String): String = "$prefix-${(nextId++).toString().padStart(4, '0')}"

    private fun instant(month: YearMonth, day: Int, hour: Int, minute: Int = 0): Instant {
        val safeDay = day.coerceAtMost(month.numberOfDays)
        return LocalDate(month.year, month.month, safeDay).atTime(hour, minute).toInstant(tz)
    }

    private val epochStart: Instant = instant(historyMonths.first(), 1, 0)

    // Accounts
    private lateinit var cash: Account
    private lateinit var bank: Account
    private lateinit var wallet: Account
    private lateinit var card: Credit

    // Categories (income)
    private lateinit var catSalary: Category
    private lateinit var catFreelance: Category
    private lateinit var catInterest: Category

    // Categories (expense roots)
    private lateinit var catFood: Category
    private lateinit var catTransport: Category
    private lateinit var catBills: Category
    private lateinit var catHousing: Category
    private lateinit var catShopping: Category
    private lateinit var catEntertainment: Category
    private lateinit var catHealth: Category
    private lateinit var catEducation: Category
    private lateinit var catTravel: Category

    // Categories (expense subs)
    private lateinit var catGroceries: Category
    private lateinit var catGroceriesOnline: Category
    private lateinit var catDining: Category
    private lateinit var catCoffee: Category
    private lateinit var catFuel: Category
    private lateinit var catRideHailing: Category
    private lateinit var catParking: Category
    private lateinit var catElectricity: Category
    private lateinit var catInternet: Category
    private lateinit var catPhone: Category
    private lateinit var catRent: Category
    private lateinit var catClothing: Category
    private lateinit var catElectronics: Category
    private lateinit var catStreaming: Category
    private lateinit var catGames: Category
    private lateinit var catMedicine: Category
    private lateinit var catCheckup: Category

    // Running balances while replaying generated transactions.
    private val balanceById = mutableMapOf<String, Long>()

    // Net worth snapshot at the end of each history month (same order as historyMonths).
    val netWorthByMonth = linkedMapOf<YearMonth, Long>()

    private val groceryDescriptions = listOf(
        "Weekly groceries", "Supermarket run", "Grocery restock", "Fresh market haul"
    )
    private val coffeeDescriptions = listOf(
        "Morning latte", "Iced americano", "Coffee break", "Espresso to go", "Weekend cappuccino"
    )
    private val rideDescriptions = listOf(
        "Ride to office", "Ride back home", "Trip to the mall", "Airport drop-off", "Ride to gym"
    )
    private val diningDescriptions = listOf(
        "Dinner with friends", "Weekend brunch", "Family dinner out", "Sushi night", "Pizza Friday"
    )

    init {
        seedCategories()
        seedAccounts()
        generateHistory()
        finalizeBalances()
        buildTemplates()
        buildBudgets()
    }

    private fun seedAccounts() {
        cash = Account(
            id = "mock-acc-cash",
            name = "Cash Wallet",
            currentAmount = 400_000L,
            type = AccountType.Cash,
            createdAt = epochStart,
            updatedAt = null,
        )
        bank = Account(
            id = "mock-acc-bank",
            name = "Main Savings",
            currentAmount = 18_000_000L,
            type = AccountType.Bank,
            createdAt = epochStart,
            updatedAt = null,
        )
        wallet = Account(
            id = "mock-acc-wallet",
            name = "E-Wallet",
            currentAmount = 150_000L,
            type = AccountType.Ewallet,
            createdAt = epochStart,
            updatedAt = null,
        )
        card = Credit(
            id = "mock-acc-card",
            name = "Sapphire Card",
            limit = 30_000_000L,
            currentAmount = 0L,
            createdAt = epochStart,
            updatedAt = null,
        )
        accounts.addAll(listOf(cash, bank, wallet))
        credits.add(card)
    }

    private fun category(
        id: String,
        name: String,
        type: TrxType,
        icon: String?,
        parent: Category? = null,
    ): Category {
        val sortOrder = if (parent == null) {
            categories.count { it.parent == null }
        } else {
            categories.count { it.parent?.id == parent.id }
        }
        val category = Category(
            id = id,
            name = name,
            type = type,
            parent = parent,
            createdAt = epochStart,
            updatedAt = null,
            icon = icon,
            sortOrder = sortOrder,
        )
        categories.add(category)
        return category
    }

    private fun seedCategories() {
        catSalary = category("mock-cat-salary", "Salary", TrxType.Income, "Banknote")
        catFreelance = category("mock-cat-freelance", "Freelance", TrxType.Income, "Laptop")
        catInterest = category("mock-cat-interest", "Interest", TrxType.Income, "Piggy Bank")

        catFood = category("mock-cat-food", "Food & Drinks", TrxType.Expense, "Utensils")
        catTransport = category("mock-cat-transport", "Transportation", TrxType.Expense, "Car")
        catBills = category("mock-cat-bills", "Bills & Utilities", TrxType.Expense, "Receipt")
        catHousing = category("mock-cat-housing", "Housing", TrxType.Expense, "House")
        catShopping = category("mock-cat-shopping", "Shopping", TrxType.Expense, "Shopping Bag")
        catEntertainment = category("mock-cat-fun", "Entertainment", TrxType.Expense, "Film")
        catHealth = category("mock-cat-health", "Health", TrxType.Expense, "Heart Pulse")
        catEducation = category("mock-cat-education", "Education", TrxType.Expense, "Book")
        catTravel = category("mock-cat-travel", "Travel", TrxType.Expense, "Plane")

        catGroceries = category("mock-cat-groceries", "Groceries", TrxType.Expense, "Shopping Cart", catFood)
        catGroceriesOnline = category("mock-cat-groceries-online", "Online Order", TrxType.Expense, "Store", catFood)
        catDining = category("mock-cat-dining", "Dining Out", TrxType.Expense, "Pizza", catFood)
        catCoffee = category("mock-cat-coffee", "Coffee", TrxType.Expense, "Coffee", catFood)
        catFuel = category("mock-cat-fuel", "Fuel", TrxType.Expense, "Fuel", catTransport)
        catRideHailing = category("mock-cat-taxi", "Ride-Hailing", TrxType.Expense, "Taxi", catTransport)
        catParking = category("mock-cat-parking", "Parking", TrxType.Expense, "Map Pin", catTransport)
        catElectricity = category("mock-cat-electricity", "Electricity", TrxType.Expense, "Lightbulb", catBills)
        catInternet = category("mock-cat-internet", "Internet", TrxType.Expense, "Wifi", catBills)
        catPhone = category("mock-cat-phone", "Phone Plan", TrxType.Expense, "Smartphone", catBills)
        catRent = category("mock-cat-rent", "Rent", TrxType.Expense, "Key", catHousing)
        catClothing = category("mock-cat-clothing", "Clothing", TrxType.Expense, "Shirt", catShopping)
        catElectronics = category("mock-cat-electronics", "Electronics", TrxType.Expense, "Monitor", catShopping)
        catStreaming = category("mock-cat-streaming", "Streaming", TrxType.Expense, "Popcorn", catEntertainment)
        catGames = category("mock-cat-games", "Games", TrxType.Expense, "Gamepad", catEntertainment)
        catMedicine = category("mock-cat-medicine", "Medicine", TrxType.Expense, "Pill", catHealth)
        catCheckup = category("mock-cat-checkup", "Checkup", TrxType.Expense, "Stethoscope", catHealth)
    }

    private fun applyBalance(accountId: String, delta: Long) {
        balanceById[accountId] = (balanceById[accountId] ?: 0L) + delta
    }

    private fun applyTrxBalance(trx: Trx) {
        when (trx) {
            is Trx.Income -> {
                if (trx.sourceAccount.id == card.id) applyBalance(card.id, -trx.amount)
                else applyBalance(trx.sourceAccount.id, trx.amount)
            }

            is Trx.Expense -> {
                if (trx.sourceAccount.id == card.id) applyBalance(card.id, trx.amount)
                else applyBalance(trx.sourceAccount.id, -trx.amount)
            }

            is Trx.Transfer -> {
                if (trx.sourceAccount.id == card.id) applyBalance(card.id, -trx.amount)
                else applyBalance(trx.sourceAccount.id, -trx.amount)
                if (trx.targetAccount.id == card.id) applyBalance(card.id, -trx.amount)
                else applyBalance(trx.targetAccount.id, trx.amount)
            }

            is Trx.Adjustment -> {
                if (trx.sourceAccount.id == card.id) applyBalance(card.id, -trx.amount)
                else applyBalance(trx.sourceAccount.id, trx.amount)
            }
        }
    }

    private fun regular(id: String) = accounts.first { it.id == id }.let { TrxAccount.Regular(it) }
    private fun creditTrx() = TrxAccount.Credit(card)

    private fun addTrx(trx: Trx) {
        trxs.add(trx)
        applyTrxBalance(trx)
    }

    private fun income(
        month: YearMonth, day: Int, hour: Int, amount: Long,
        description: String, accountId: String, categoryId: Category,
    ) {
        addTrx(
            Trx.Income(
                id = newId("trx"),
                description = description,
                amount = amount,
                category = categoryId,
                sourceAccount = regular(accountId),
                transactionAt = instant(month, day, hour),
                createdAt = instant(month, day, hour + 1),
                updatedAt = null,
            )
        )
    }

    private fun expense(
        month: YearMonth, day: Int, hour: Int, amount: Long,
        description: String, source: TrxAccount, category: Category?,
    ) {
        addTrx(
            Trx.Expense(
                id = newId("trx"),
                description = description,
                amount = amount,
                category = category,
                sourceAccount = source,
                transactionAt = instant(month, day, hour),
                createdAt = instant(month, day, hour + 1),
                updatedAt = null,
            )
        )
    }

    private fun transfer(
        month: YearMonth, day: Int, hour: Int, amount: Long,
        description: String, fromAccountId: String, toAccountId: String,
    ) {
        val target = if (toAccountId == card.id) creditTrx() else regular(toAccountId)
        addTrx(
            Trx.Transfer(
                id = newId("trx"),
                description = description,
                amount = amount,
                category = null,
                sourceAccount = regular(fromAccountId),
                targetAccount = target,
                transactionAt = instant(month, day, hour),
                createdAt = instant(month, day, hour + 1),
                updatedAt = null,
            )
        )
    }

    /** Deterministic small variation based on month/day so amounts differ between entries. */
    private fun variation(month: YearMonth, day: Int, step: Long, mod: Long): Long {
        val seed = ((month.year * 12L + month.month.number) * 31L + day) % mod
        return seed * step
    }

    private fun pick(list: List<String>, month: YearMonth, day: Int): String {
        val index = ((month.year * 12L + month.month.number) * 31L + day).mod(list.size)
        return list[index]
    }

    private fun generateHistory() {
        historyMonths.forEachIndexed { index, month ->
            val isCurrent = month == currentMonth
            fun visible(day: Int) = !isCurrent || day <= today.dayOfMonth

            // --- Recurring income ---
            if (visible(1)) {
                income(month, 1, 9, 11_000_000L, "Monthly salary", bank.id, catSalary)
            }
            if (index % 3 == 2 && visible(15)) {
                income(
                    month, 15, 14,
                    2_600_000L + variation(month, 15, 100_000L, 8),
                    pick(listOf("Freelance design project", "Mobile app consulting"), month, 15),
                    bank.id, catFreelance,
                )
            }
            if (visible(25)) {
                income(month, 25, 8, 45_000L + variation(month, 25, 5_000L, 6), "Monthly savings interest", bank.id, catInterest)
            }

            // --- Recurring bills & housing ---
            if (visible(1)) expense(month, 1, 10, 3_500_000L, "Apartment rent", regular(bank.id), catRent)
            if (visible(5)) expense(month, 5, 13, 385_000L + variation(month, 5, 20_000L, 7), "Electricity bill", regular(bank.id), catElectricity)
            if (visible(8)) expense(month, 8, 13, 350_000L, "Home internet bill", regular(bank.id), catInternet)
            if (visible(10)) expense(month, 10, 9, 150_000L, "Monthly phone plan", regular(wallet.id), catPhone)
            if (visible(12)) expense(month, 12, 8, 54_000L, "Streaming subscription", regular(wallet.id), catStreaming)

            // --- Cash flow management ---
            if (visible(2)) transfer(month, 2, 8, 1_200_000L, "ATM withdrawal", bank.id, cash.id)
            if (visible(3)) transfer(month, 3, 8, 600_000L, "E-Wallet top-up", bank.id, wallet.id)

            // --- Weekly groceries (Saturdays, debit card) ---
            for (day in 1..month.numberOfDays) {
                val date = month.firstDay.plus(day - 1, DateTimeUnit.DAY)
                if (date.dayOfWeek == DayOfWeek.SATURDAY && visible(day)) {
                    expense(
                        month, day, 11,
                        265_000L + variation(month, day, 15_000L, 8),
                        pick(groceryDescriptions, month, day),
                        regular(bank.id), catGroceries,
                    )
                }
            }
            // Online groceries on the credit card
            if (visible(14)) {
                expense(month, 14, 20, 320_000L + variation(month, 14, 10_000L, 5), "Online grocery order", creditTrx(), catGroceriesOnline)
            }

            // --- Coffee habit (cash) ---
            for (day in intArrayOf(3, 7, 11, 14, 18, 21, 24, 27)) {
                if (day <= month.numberOfDays && visible(day)) {
                    expense(
                        month, day, 8,
                        28_000L + variation(month, day, 3_000L, 6),
                        pick(coffeeDescriptions, month, day),
                        regular(cash.id), catCoffee,
                    )
                }
            }

            // --- Ride-hailing (e-wallet / cash mix) ---
            for (day in intArrayOf(2, 5, 9, 12, 16, 19, 23, 26)) {
                if (day <= month.numberOfDays && visible(day)) {
                    val fromWallet = day % 2 == 0
                    expense(
                        month, day, 18,
                        22_000L + variation(month, day, 6_000L, 8),
                        pick(rideDescriptions, month, day),
                        if (fromWallet) regular(wallet.id) else regular(cash.id),
                        catRideHailing,
                    )
                }
            }

            // --- Parking (cash, twice a month) ---
            for (day in intArrayOf(9, 23)) {
                if (day <= month.numberOfDays && visible(day)) {
                    expense(month, day, 10, 15_000L, "Mall parking fee", regular(cash.id), catParking)
                }
            }

            // --- Fuel (bank debit, biweekly) ---
            for (day in intArrayOf(6, 20)) {
                if (day <= month.numberOfDays && visible(day)) {
                    expense(month, day, 17, 200_000L + variation(month, day, 10_000L, 4), "Fuel refill", regular(bank.id), catFuel)
                }
            }

            // --- Dining out (credit card + cash) ---
            for (day in intArrayOf(6, 13, 20, 27)) {
                if (day <= month.numberOfDays && visible(day)) {
                    val onCard = day == 13 || day == 27
                    expense(
                        month, day, 19,
                        135_000L + variation(month, day, 25_000L, 7),
                        pick(diningDescriptions, month, day),
                        if (onCard) creditTrx() else regular(cash.id),
                        catDining,
                    )
                }
            }

            // --- Games every third month (e-wallet) ---
            if (index % 3 == 1 && visible(21)) {
                expense(month, 21, 21, 250_000L, "New game purchase", regular(wallet.id), catGames)
            }

            // --- Medicine occasionally (cash) ---
            if (index % 2 == 0 && visible(17)) {
                expense(month, 17, 16, 85_000L + variation(month, 17, 10_000L, 4), "Vitamins and supplements", regular(cash.id), catMedicine)
            }

            // --- Sprinkled extras for texture ---
            when (index) {
                2 -> if (visible(17)) expense(month, 17, 15, 420_000L, "T-shirt and jeans", creditTrx(), catClothing)
                4 -> if (visible(17)) expense(month, 17, 15, 510_000L, "Running shoes", creditTrx(), catClothing)
                6 -> if (visible(17)) expense(month, 17, 15, 380_000L, "Summer dress and accessories", creditTrx(), catClothing)
                7 -> if (visible(11)) expense(month, 11, 20, 1_850_000L, "27-inch monitor", creditTrx(), catElectronics)
                8 -> {
                    if (visible(7)) expense(month, 7, 10, 350_000L, "Annual health checkup", regular(bank.id), catCheckup)
                    if (visible(17)) expense(month, 17, 15, 295_000L, "Winter jacket", creditTrx(), catClothing)
                }

                9 -> {
                    if (visible(9)) expense(month, 9, 14, 2_400_000L, "Round-trip flight tickets", creditTrx(), catTravel)
                    if (visible(10)) expense(month, 10, 15, 1_750_000L, "Hotel booking - weekend getaway", creditTrx(), catTravel)
                    if (visible(19)) expense(month, 19, 20, 640_000L, "Noise-cancelling earbuds", creditTrx(), catElectronics)
                }

                10 -> if (visible(17)) expense(month, 17, 15, 460_000L, "Leather wallet and belt", creditTrx(), catClothing)

                11 -> {
                    if (visible(6)) expense(month, 6, 19, 210_000L, "Anniversary dinner", creditTrx(), catDining)
                    if (visible(15)) expense(month, 15, 14, 180_000L, "Online course subscription", regular(bank.id), catEducation)
                }
            }

            // --- Monthly credit card payment (~60% of running debt) ---
            if (visible(25)) {
                val debt = balanceById[card.id] ?: 0L
                val payment = if (debt <= 0) 0L else ((debt * 6 / 10) / 10_000L) * 10_000L
                if (payment > 0) transfer(month, 25, 21, payment, "Credit card payment", bank.id, card.id)
            }

            netWorthByMonth[month] =
                accounts.sumOf { balanceById[it.id] ?: 0L } -
                        credits.sumOf { balanceById[it.id] ?: 0L }
        }
    }

    private fun finalizeBalances() {
        accounts.replaceAll { account ->
            account.copy(currentAmount = balanceById[account.id] ?: account.currentAmount)
        }
        credits.replaceAll { credit ->
            credit.copy(currentAmount = balanceById[credit.id] ?: credit.currentAmount)
        }
    }

    private fun buildBudgets() {
        val baseByRootCategory: List<Pair<Category, Long>> = listOf(
            catFood to 2_700_000L,
            catTransport to 1_100_000L,
            catBills to 1_200_000L,
            catShopping to 1_300_000L,
            catEntertainment to 450_000L,
            catHealth to 500_000L,
        )

        budgetMonths.forEach { month ->
            baseByRootCategory.forEach { (rootCategory, baseAmount) ->
                val spent = trxs
                    .filterIsInstance<Trx.Expense>()
                    .sumOf { expense ->
                        val category = expense.category ?: return@sumOf 0L
                        val date = expense.transactionAt.toLocalDateTime(tz)
                        val inMonth = date.year == month.year && date.monthNumber == month.month.number
                        val underRoot = category.id == rootCategory.id ||
                                category.parent?.id == rootCategory.id
                        if (inMonth && underRoot) expense.amount else 0L
                    }

                val templateId = budgetTemplates
                    .first { it.category.id == rootCategory.id }
                    .id

                budgets.add(
                    Budget(
                        id = newId("bud"),
                        templateId = templateId,
                        category = rootCategory,
                        month = month,
                        baseAmount = baseAmount,
                        spentAmount = spent,
                        createdAt = month.firstDay.atTime(0, 0).toInstant(tz),
                        updatedAt = null,
                    )
                )
            }
        }
    }

    private fun buildTemplates() {
        budgetTemplates.addAll(
            listOf(
                BudgetTemplate("mock-btpl-1", catFood, 2_700_000L, epochStart, null),
                BudgetTemplate("mock-btpl-2", catTransport, 1_100_000L, epochStart, null),
                BudgetTemplate("mock-btpl-3", catBills, 1_200_000L, epochStart, null),
                BudgetTemplate("mock-btpl-5", catShopping, 1_300_000L, epochStart, null),
                BudgetTemplate("mock-btpl-6", catEntertainment, 450_000L, epochStart, null),
                BudgetTemplate("mock-btpl-7", catHealth, 500_000L, epochStart, null),
            )
        )

        trxTemplates.addAll(
            listOf(
                TrxTemplate(
                    id = "mock-ttpl-1",
                    name = "Monthly Rent",
                    type = TrxType.Expense,
                    description = "Apartment rent",
                    amount = 3_500_000L,
                    category = catRent,
                    sourceAccount = regular(bank.id),
                    targetAccount = null,
                    createdAt = epochStart,
                    updatedAt = null,
                ),
                TrxTemplate(
                    id = "mock-ttpl-2",
                    name = "E-Wallet Top-Up",
                    type = TrxType.Transfer,
                    description = "E-Wallet top-up",
                    amount = 500_000L,
                    category = null,
                    sourceAccount = regular(bank.id),
                    targetAccount = regular(wallet.id),
                    createdAt = epochStart,
                    updatedAt = null,
                ),
                TrxTemplate(
                    id = "mock-ttpl-3",
                    name = "Morning Latte",
                    type = TrxType.Expense,
                    description = "Morning latte",
                    amount = 32_000L,
                    category = catCoffee,
                    sourceAccount = regular(cash.id),
                    targetAccount = null,
                    createdAt = epochStart,
                    updatedAt = null,
                ),
            )
        )
    }

    /** Net worth series aligned with [historyMonths]. */
    fun netWorthHistory(months: List<YearMonth>): List<Long> {
        var lastKnown = 0L
        return months.map { month ->
            netWorthByMonth[month]?.also { lastKnown = it } ?: lastKnown
        }
    }

    fun totalNetWorth(): Long =
        accounts.sumOf { it.currentAmount } - credits.sumOf { it.currentAmount }

    /**
     * Applies (sign = +1) or reverts (sign = -1) the balance effect of [trx]
     * against the current account snapshots.
     */
    fun applyTrxDelta(trx: Trx, sign: Int) {
        fun adjust(id: String, delta: Long) {
            val isCredit = credits.any { it.id == id }
            val effective = if (isCredit) -delta else delta
            if (isCredit) {
                credits.replaceAll { if (it.id == id) it.copy(currentAmount = it.currentAmount + effective * sign) else it }
            } else {
                accounts.replaceAll { if (it.id == id) it.copy(currentAmount = it.currentAmount + effective * sign) else it }
            }
        }

        when (trx) {
            is Trx.Income -> adjust(trx.sourceAccount.id, trx.amount)

            is Trx.Expense -> adjust(trx.sourceAccount.id, -trx.amount)

            is Trx.Transfer -> {
                adjust(trx.sourceAccount.id, -trx.amount)
                adjust(trx.targetAccount.id, trx.amount)
            }

            is Trx.Adjustment -> adjust(trx.sourceAccount.id, trx.amount)
        }
    }

    /** Adds/subtracts [amount] from every budget of [month] covering [category] or its parent root. */
    fun adjustBudgetSpent(month: YearMonth, category: Category?, amount: Long, updatedAt: Instant) {
        if (category == null) return
        val affectedIds = setOfNotNull(category.id, category.parent?.id)
        budgets.replaceAll { budget ->
            val sameMonth = budget.month == month
            val covered = budget.category.id in affectedIds
            if (sameMonth && covered) {
                budget.copy(spentAmount = budget.spentAmount + amount, updatedAt = updatedAt)
            } else {
                budget
            }
        }
    }

    fun newEntityId(prefix: String): String = newId(prefix)
}
