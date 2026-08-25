package dev.nichidori.saku

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsControllerCompat
import androidx.room.Room
import dev.nichidori.saku.core.event.AppEventBus
import dev.nichidori.saku.core.platform.setToastActivityProvider
import dev.nichidori.saku.data.AppDatabase
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
const val useMockData = false

class MainActivity : ComponentActivity() {
    var themeInitialized by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        splashScreen.apply {
            setKeepOnScreenCondition { !themeInitialized }
            setOnExitAnimationListener { splashScreenView ->
                val iconView = splashScreenView.iconView

                val iconScaleX = ObjectAnimator.ofFloat(iconView, View.SCALE_X, 1f, 0.0f)
                val iconScaleY = ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 1f, 0.0f)
                val iconFade = ObjectAnimator.ofFloat(iconView, View.ALPHA, 1f, 0f)
                val bgFade = ObjectAnimator.ofFloat(splashScreenView.view, View.ALPHA, 1f, 0f)

                AnimatorSet().run {
                    playTogether(iconScaleX, iconScaleY, iconFade, bgFade)
                    duration = 300L
                    interpolator = AccelerateInterpolator()
                    doOnEnd { splashScreenView.remove() }
                    start()
                }
            }
        }

        setToastActivityProvider { this }
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        val db = getRoomDatabase(
            builder = if (useInMemoryDb) {
                Room.inMemoryDatabaseBuilder(this, AppDatabase::class.java)
            } else {
                getDatabaseBuilder(this)
            }
        )
        val dataStore = createDataStore(this)
        val appEventBus = AppEventBus()

        setContent {
            val view = LocalView.current
            val window = LocalActivity.current?.window

            if (useMockData) {
                val mockData = remember { MockData() }
                App(
                    accountRepository = MockAccountRepository(data = mockData, appEventBus = appEventBus),
                    categoryRepository = MockCategoryRepository(data = mockData),
                    trxRepository = MockTrxRepository(data = mockData, appEventBus = appEventBus),
                    budgetRepository = MockBudgetRepository(data = mockData),
                    installmentRepository = MockInstallmentRepository(data = mockData),
                    appEventBus = appEventBus,
                    dataStore = dataStore,
                    onDarkTheme = { darkTheme ->
                        if (!themeInitialized) {
                            themeInitialized = true
                        }
                        window?.let {
                            WindowInsetsControllerCompat(it, view).isAppearanceLightStatusBars = !darkTheme
                        }
                    }
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
                    dataStore = dataStore,
                    onDarkTheme = { darkTheme ->
                        if (!themeInitialized) {
                            themeInitialized = true
                        }
                        window?.let {
                            WindowInsetsControllerCompat(it, view).isAppearanceLightStatusBars = !darkTheme
                        }
                    }
                )
            }
        }
    }
}
