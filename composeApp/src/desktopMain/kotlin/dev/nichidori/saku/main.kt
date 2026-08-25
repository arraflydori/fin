package dev.nichidori.saku

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.room.Room
import java.awt.Toolkit
import dev.nichidori.saku.core.event.AppEventBus
import dev.nichidori.saku.data.createDataStore
import dev.nichidori.saku.data.getDatabaseBuilder
import dev.nichidori.saku.data.getRoomDatabase
import dev.nichidori.saku.data.repo.DefaultAccountRepository
import dev.nichidori.saku.data.repo.DefaultBudgetRepository
import dev.nichidori.saku.data.repo.DefaultCategoryRepository
import dev.nichidori.saku.data.repo.DefaultInstallmentRepository
import dev.nichidori.saku.data.repo.DefaultTrxRepository
import dev.nichidori.saku.mock.MockAccountRepository
import dev.nichidori.saku.mock.MockBudgetRepository
import dev.nichidori.saku.mock.MockCategoryRepository
import dev.nichidori.saku.mock.MockData
import dev.nichidori.saku.mock.MockInstallmentRepository
import dev.nichidori.saku.mock.MockTrxRepository

const val useInMemoryDb = false
const val useMockData = true

fun main() = application {
    val density = LocalDensity.current
    val windowWidth = 400.dp
    val topPadding = 48.dp
    val rightPadding = 36.dp
    val screenWidth = with(density) { Toolkit.getDefaultToolkit().screenSize.width.toDp() }
    val state = rememberWindowState(
        position = WindowPosition(
            x = screenWidth - windowWidth - rightPadding,
            y = topPadding,
        ),
        size = DpSize(
            width = windowWidth,
            height = windowWidth * 2,
        ),
    )
    Window(
        alwaysOnTop = true,
        onCloseRequest = ::exitApplication,
        title = "Saku",
        state = state,
    ) {
        val db = getRoomDatabase(
            builder = if (useInMemoryDb) {
                Room.inMemoryDatabaseBuilder()
            } else {
                getDatabaseBuilder()
            }
        )
        val dataStore = createDataStore()
        val appEventBus = AppEventBus()

        if (useMockData) {
            val mockData = remember { MockData() }
            App(
                accountRepository = MockAccountRepository(data = mockData, appEventBus = appEventBus),
                categoryRepository = MockCategoryRepository(data = mockData),
                trxRepository = MockTrxRepository(data = mockData, appEventBus = appEventBus),
                budgetRepository = MockBudgetRepository(data = mockData),
                installmentRepository = MockInstallmentRepository(data = mockData),
                appEventBus = appEventBus,
                dataStore = dataStore
            )
        } else {
            val trxRepository = DefaultTrxRepository(db = db, appEventBus = appEventBus)
            App(
                accountRepository = DefaultAccountRepository(db = db, appEventBus = appEventBus),
                categoryRepository = DefaultCategoryRepository(db = db),
                trxRepository = trxRepository,
                budgetRepository = DefaultBudgetRepository(db = db),
                installmentRepository = DefaultInstallmentRepository(
                    db = db,
                    trxRepository = trxRepository,
                    appEventBus = appEventBus
                ),
                appEventBus = appEventBus,
                dataStore = dataStore
            )
        }
    }
}