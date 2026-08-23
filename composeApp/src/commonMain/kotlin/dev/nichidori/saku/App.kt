package dev.nichidori.saku

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.composables.icons.lucide.*
import dev.nichidori.saku.core.composable.*
import dev.nichidori.saku.core.event.AppEvent
import dev.nichidori.saku.core.event.AppEventBus
import dev.nichidori.saku.core.navigation.TrxTypeNavType
import dev.nichidori.saku.core.platform.getAppVersion
import dev.nichidori.saku.core.theme.MyTheme
import dev.nichidori.saku.core.util.collectAsStateWithLifecycleIfAvailable
import dev.nichidori.saku.core.util.toYearMonth
import dev.nichidori.saku.domain.model.Trx
import dev.nichidori.saku.domain.model.TrxType
import dev.nichidori.saku.domain.repo.*
import dev.nichidori.saku.feature.account.AccountPage
import dev.nichidori.saku.feature.account.AccountViewModel
import dev.nichidori.saku.feature.budget.*
import dev.nichidori.saku.feature.category.CategoryPage
import dev.nichidori.saku.feature.category.CategoryViewModel
import dev.nichidori.saku.feature.categoryList.CategoryListPage
import dev.nichidori.saku.feature.categoryList.CategoryListViewModel
import dev.nichidori.saku.feature.home.HomePage
import dev.nichidori.saku.feature.home.HomeViewModel
import dev.nichidori.saku.feature.statistic.StatisticPage
import dev.nichidori.saku.feature.statistic.StatisticViewModel
import dev.nichidori.saku.feature.trx.TrxPage
import dev.nichidori.saku.feature.trx.TrxViewModel
import dev.nichidori.saku.feature.trxList.TrxListPage
import dev.nichidori.saku.feature.trxList.TrxListViewModel
import dev.nichidori.saku.feature.trxSearch.TrxSearchPage
import dev.nichidori.saku.feature.trxSearch.TrxSearchViewModel
import dev.nichidori.saku.feature.trxTemplate.TrxTemplatePage
import dev.nichidori.saku.feature.trxTemplate.TrxTemplateViewModel
import dev.nichidori.saku.feature.trxTemplateList.TrxTemplateListBottomSheet
import dev.nichidori.saku.feature.trxTemplateList.TrxTemplateListBottomSheetViewModel
import dev.nichidori.saku.feature.trxTemplateList.TrxTemplateListPage
import dev.nichidori.saku.feature.trxTemplateList.TrxTemplateListViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.reflect.typeOf
import kotlin.time.Clock

@Serializable
sealed interface Route {
    @Serializable
    data object Main : Route

    @Serializable
    data object Home : Route

    @Serializable
    data object Statistic : Route

    @Serializable
    data object CategoryList : Route

    @Serializable
    data object TrxTemplateList : Route

    @Serializable
    data object TrxList : Route

    @Serializable
    data object TrxSearch : Route

    @Serializable
    data class Account(val id: String?) : Route

    @Serializable
    data class Category(val id: String?, val type: TrxType = TrxType.Expense) : Route

    @Serializable
    data class Trx(val id: String?) : Route

    @Serializable
    data class TrxTemplate(val id: String?, val type: TrxType = TrxType.Expense) : Route

    @Serializable
    data class CategoryBudget(val templateId: String) : Route

    @Serializable
    data class DefaultBudget(val templateId: String?) : Route

    @Serializable
    data class MonthBudget(val budgetId: String) : Route
}

@Composable
fun App(
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    trxRepository: TrxRepository,
    budgetRepository: BudgetRepository,
    installmentRepository: InstallmentRepository,
    appEventBus: AppEventBus,
    dataStore: DataStore<Preferences>,
    onDarkTheme: (darkTheme: Boolean) -> Unit = {},
) {
    val appViewModel: AppViewModel = viewModel {
        AppViewModel(
            dataStore = dataStore,
            trxRepository = trxRepository,
            accountRepository = accountRepository,
            installmentRepository = installmentRepository,
        )
    }

    val focusManager = LocalFocusManager.current
    val rootNavController = rememberNavController()
    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)

    val appUiState by appViewModel.uiState.collectAsStateWithLifecycleIfAvailable()

    var request by remember { mutableStateOf<ThemeSwitcherRequest?>(null) }
    var counter by remember { mutableLongStateOf(0L) }
    var showMenu by remember { mutableStateOf(false) }
    var themeToggleOffset by remember { mutableStateOf(Offset.Zero) }
    val snackbarHostState = remember { SnackbarHostState() }

    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = showMenu,
        onBackCompleted = { showMenu = false }
    )

    LaunchedEffect(appUiState.darkTheme) {
        appUiState.darkTheme?.let {
            onDarkTheme(it)
            request = ThemeSwitcherRequest(
                id = ++counter,
                origin = themeToggleOffset,
            )
        }
    }

    LaunchedEffect(Unit) {
        appEventBus.events.collect { event ->
            when (event) {
                is AppEvent.TrxChanged.Deleted -> {
                    if ((event.trx as? Trx.Expense)?.installment != null) return@collect
                    val result = snackbarHostState.showSnackbar(
                        message = "Transaction deleted",
                        actionLabel = "Undo",
                        withDismissAction = true,
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        appViewModel.restoreTrx(event.trx)
                    }
                }

                is AppEvent.TrxChanged.Copied -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Transaction copied",
                        actionLabel = "View",
                        withDismissAction = true,
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        rootNavController.navigate(Route.Trx(id = event.trxId))
                    }
                }

                else -> {}
            }
        }
    }

    val darkTheme = appUiState.darkTheme ?: return

    MyThemeSwitcher(
        darkTheme = darkTheme,
        request = request,
    ) { darkTheme ->
        MyTheme(darkTheme = darkTheme) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            focusManager.clearFocus()
                        }
                    },
            ) {
                @OptIn(ExperimentalAnimationApi::class)
                NavHost(
                    rootNavController,
                    startDestination = Route.Main,
                    enterTransition = {
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start)
                    },
                    exitTransition = {
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start) +
                                veilOut(targetColor = Color.Black.copy(alpha = 0.4f))
                    },
                    popEnterTransition = {
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End) +
                                unveilIn(initialColor = Color.Black.copy(alpha = 0.4f))
                    },
                    popExitTransition = {
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End) +
                                veilOut(targetColor = Color.Black.copy(alpha = 0.4f))
                    }
                ) {
                    composable<Route.Main> {
                        SettingsMenu(
                            showMenu = showMenu,
                            darkTheme = darkTheme,
                            onMenuClose = { showMenu = false },
                            onCategoryClick = {
                                showMenu = false
                                rootNavController.navigate(Route.CategoryList)
                            },
                            onTrxTemplateClick = {
                                showMenu = false
                                rootNavController.navigate(Route.TrxTemplateList)
                            },
                            onThemeToggleRequest = {
                                appViewModel.toggleDarkTheme()
                            },
                            onThemeToggleOffsetChange = { themeToggleOffset = it },
                            appVersion = { getAppVersion() }
                        ) {
                            MainContainer(
                                rootNavController = rootNavController,
                                accountRepository = accountRepository,
                                categoryRepository = categoryRepository,
                                trxRepository = trxRepository,
                                budgetRepository = budgetRepository,
                                appEventBus = appEventBus,
                                snackbarHostState = snackbarHostState,
                                onMenuClick = { showMenu = !showMenu },
                            )
                        }
                    }
                    composable<Route.CategoryList> {
                        CategoryListPage(
                            viewModel = viewModel {
                                CategoryListViewModel(categoryRepository)
                            },
                            onUp = { rootNavController.popBackStack() },
                            onNewCategoryClick = { type ->
                                rootNavController.navigate(Route.Category(id = null, type = type))
                            },
                            onCategoryClick = { id ->
                                rootNavController.navigate(Route.Category(id))
                            }
                        )
                    }
                    composable<Route.TrxTemplateList> {
                        TrxTemplateListPage(
                            viewModel = viewModel {
                                TrxTemplateListViewModel(trxRepository)
                            },
                            onUp = { rootNavController.popBackStack() },
                            onNewTemplateClick = { type ->
                                rootNavController.navigate(Route.TrxTemplate(id = null, type = type))
                            },
                            onTemplateClick = { id ->
                                rootNavController.navigate(Route.TrxTemplate(id))
                            }
                        )
                    }
                    composable<Route.Account> { backStackEntry ->
                        val account = backStackEntry.toRoute<Route.Account>()
                        AccountPage(
                            viewModel = viewModel {
                                AccountViewModel(accountRepository, trxRepository, account.id)
                            },
                            onUp = { rootNavController.popBackStack() },
                            onSaveSuccess = { rootNavController.popBackStack() },
                            onDeleteSuccess = { rootNavController.popBackStack() }
                        )
                    }
                    composable<Route.Category>(
                        typeMap = mapOf(typeOf<TrxType>() to TrxTypeNavType)
                    ) { backStackEntry ->
                        val category = backStackEntry.toRoute<Route.Category>()
                        CategoryPage(
                            viewModel = viewModel {
                                CategoryViewModel(categoryRepository, category.id, category.type)
                            },
                            onUp = { rootNavController.popBackStack() },
                            onSaveSuccess = { rootNavController.popBackStack() },
                            onDeleteSuccess = { rootNavController.popBackStack() }
                        )
                    }
                    composable<Route.Trx> { backStackEntry ->
                        val trx = backStackEntry.toRoute<Route.Trx>()
                        TrxPage(
                            viewModel = viewModel {
                                TrxViewModel(
                                    accountRepository,
                                    categoryRepository,
                                    trxRepository,
                                    installmentRepository,
                                    trx.id
                                )
                            },
                            onUp = { rootNavController.popBackStack() },
                            onSaveSuccess = { rootNavController.popBackStack() },
                            onDeleteSuccess = {
                                rootNavController.popBackStack()
                            },
                            onCopySuccess = { copiedTrxId ->
                                appEventBus.emit(AppEvent.TrxChanged.Copied(copiedTrxId))
                                rootNavController.popBackStack()
                            },
                        )
                    }
                    composable<Route.TrxSearch> {
                        TrxSearchPage(
                            viewModel = viewModel {
                                TrxSearchViewModel(trxRepository)
                            },
                            onUp = { rootNavController.popBackStack() },
                            onTrxClick = { id ->
                                rootNavController.navigate(Route.Trx(id))
                            }
                        )
                    }
                    composable<Route.TrxTemplate>(
                        typeMap = mapOf(typeOf<TrxType>() to TrxTypeNavType)
                    ) { backStackEntry ->
                        val route = backStackEntry.toRoute<Route.TrxTemplate>()
                        TrxTemplatePage(
                            viewModel = viewModel {
                                TrxTemplateViewModel(
                                    accountRepository,
                                    categoryRepository,
                                    trxRepository,
                                    route.id,
                                    route.type
                                )
                            },
                            onUp = { rootNavController.popBackStack() },
                            onSaveSuccess = { rootNavController.popBackStack() },
                            onDeleteSuccess = { rootNavController.popBackStack() },
                        )
                    }
                    composable<Route.CategoryBudget> { backStackEntry ->
                        val route = backStackEntry.toRoute<Route.CategoryBudget>()
                        CategoryBudgetPage(
                            viewModel = viewModel {
                                CategoryBudgetViewModel(budgetRepository, route.templateId)
                            },
                            onUp = { rootNavController.popBackStack() },
                            onDefaultBudgetClick = { rootNavController.navigate(Route.DefaultBudget(it)) },
                            onMonthBudgetClick = { rootNavController.navigate(Route.MonthBudget(it)) }
                        )
                    }
                    composable<Route.DefaultBudget> { backStackEntry ->
                        val route = backStackEntry.toRoute<Route.DefaultBudget>()
                        DefaultBudgetPage(
                            viewModel = viewModel {
                                DefaultBudgetViewModel(categoryRepository, budgetRepository, route.templateId)
                            },
                            onUp = { rootNavController.popBackStack() },
                            onSaveSuccess = { rootNavController.popBackStack() }
                        )
                    }
                    composable<Route.MonthBudget> { backStackEntry ->
                        val route = backStackEntry.toRoute<Route.MonthBudget>()
                        MonthBudgetPage(
                            viewModel = viewModel {
                                MonthBudgetViewModel(budgetRepository, route.budgetId)
                            },
                            onUp = { rootNavController.popBackStack() },
                            onSaveSuccess = { rootNavController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContainer(
    rootNavController: NavHostController,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    trxRepository: TrxRepository,
    budgetRepository: BudgetRepository,
    appEventBus: AppEventBus,
    snackbarHostState: SnackbarHostState,
    onMenuClick: () -> Unit,
) {
    val innerNavController = rememberNavController()
    val scope = rememberCoroutineScope()
    var showTemplateSheet by remember { mutableStateOf(false) }
    var selectedMonth by rememberSaveable { mutableStateOf(Clock.System.now().toYearMonth()) }
    val navBackStackEntry by innerNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    LaunchedEffect(currentDestination) {
        if (currentDestination?.hierarchy?.any { it.hasRoute<Route.Home>() } == true) {
            selectedMonth = Clock.System.now().toYearMonth()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            MyNavBar(
                selectedDestination = when {
                    currentDestination?.hierarchy?.any { it.hasRoute<Route.Home>() } == true -> NavBarDestination.Home
                    currentDestination?.hierarchy?.any { it.hasRoute<Route.TrxList>() } == true -> NavBarDestination.Trx
                    currentDestination?.hierarchy?.any { it.hasRoute<Route.Statistic>() } == true -> NavBarDestination.Statistic
                    else -> null
                },
                onHomeClick = {
                    val currentDestination = innerNavController.currentBackStackEntry?.destination
                    if (currentDestination?.hierarchy?.none { it.hasRoute<Route.Home>() } == true) {
                        innerNavController.navigate(Route.Home) {
                            popUpTo(Route.Home) {
                                inclusive = true
                            }
                        }
                    }
                },
                onTrxClick = {
                    val currentDestination = innerNavController.currentBackStackEntry?.destination
                    if (currentDestination?.hierarchy?.none { it.hasRoute<Route.TrxList>() } == true) {
                        innerNavController.navigate(Route.TrxList) {
                            popUpTo(Route.Home) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                },
                onStatisticClick = {
                    val currentDestination = innerNavController.currentBackStackEntry?.destination
                    if (currentDestination?.hierarchy?.none { it.hasRoute<Route.Statistic>() } == true) {
                        innerNavController.navigate(Route.Statistic) {
                            popUpTo(Route.Home) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                },
                onAddClick = {
                    rootNavController.navigate(Route.Trx(id = null))
                },
                onAddLongPress = {
                    showTemplateSheet = true
                },
            )
        }
    ) { contentPadding ->
        val monthChipsListState = rememberLazyListState()

        NavHost(
            innerNavController,
            startDestination = Route.Home,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
            modifier = Modifier.padding(contentPadding).consumeWindowInsets(contentPadding)
        ) {
            composable<Route.Home> {
                val viewModel = viewModel {
                    HomeViewModel(appEventBus, accountRepository, trxRepository, budgetRepository)
                }

                HomePage(
                    viewModel = viewModel,
                    onMenuClick = onMenuClick,
                    onAccountClick = { id ->
                        rootNavController.navigate(Route.Account(id))
                    },
                    onNewAccountClick = {
                        rootNavController.navigate(Route.Account(id = null))
                    },
                    onBudgetClick = { templateId ->
                        rootNavController.navigate(Route.CategoryBudget(templateId))
                    },
                    onNewBudgetClick = {
                        rootNavController.navigate(Route.DefaultBudget(templateId = null))
                    }
                )
            }
            composable<Route.TrxList> {
                val viewModel = viewModel {
                    TrxListViewModel(appEventBus, accountRepository, categoryRepository, trxRepository)
                }

                TrxListPage(
                    initialMonth = selectedMonth,
                    viewModel = viewModel,
                    monthChipsListState = monthChipsListState,
                    onMonthChange = { month ->
                        selectedMonth = month
                    },
                    onTrxClick = { id ->
                        rootNavController.navigate(Route.Trx(id))
                    },
                    onSearchClick = {
                        rootNavController.navigate(Route.TrxSearch)
                    }
                )
            }
            composable<Route.Statistic> {
                val viewModel = viewModel {
                    StatisticViewModel(appEventBus, trxRepository)
                }

                StatisticPage(
                    initialMonth = selectedMonth,
                    viewModel = viewModel,
                    monthChipsListState = monthChipsListState,
                    onMonthChange = { month ->
                        selectedMonth = month
                    }
                )
            }
        }

        if (showTemplateSheet) {
            TrxTemplateListBottomSheet(
                viewModel = viewModel {
                    TrxTemplateListBottomSheetViewModel(trxRepository)
                },
                onDismissRequest = { showTemplateSheet = false },
                onTrxCreated = { newTrxId ->
                    showTemplateSheet = false
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "Transaction created!",
                            actionLabel = "View",
                            withDismissAction = true,
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            rootNavController.navigate(Route.Trx(id = newTrxId))
                        }
                    }
                },
                onManageClick = {
                    showTemplateSheet = false
                    rootNavController.navigate(Route.TrxTemplateList)
                },
            )
        }
    }
}

@Suppress("COMPOSE_APPLIER_CALL_MISMATCH")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsMenu(
    showMenu: Boolean,
    darkTheme: Boolean,
    onMenuClose: () -> Unit,
    onCategoryClick: () -> Unit,
    onTrxTemplateClick: () -> Unit,
    onThemeToggleRequest: () -> Unit,
    onThemeToggleOffsetChange: (Offset) -> Unit,
    appVersion: () -> String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val menuWidth = maxWidth.coerceIn(240.dp, 320.dp) - 20.dp
        val menuOffsetPx = with(LocalDensity.current) { menuWidth.toPx() }

        val menuTranslation by animateFloatAsState(
            targetValue = if (showMenu) 0f else menuOffsetPx,
            animationSpec = tween(durationMillis = 300),
            label = "menuTranslation"
        )

        val contentTranslation by animateFloatAsState(
            targetValue = if (showMenu) -menuOffsetPx else 0f,
            animationSpec = tween(durationMillis = 300),
            label = "contentTranslation"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = contentTranslation }
        ) {
            content()
        }

        AnimatedVisibility(
            visible = showMenu,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .graphicsLayer { translationX = contentTranslation }
                    .clickable { onMenuClose() }
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .width(menuWidth)
                .graphicsLayer { translationX = menuTranslation }
                .background(color = MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Column(modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    MyIconButton(onClick = onMenuClose) {
                        Icon(
                            imageVector = Lucide.X,
                            contentDescription = "Close menu",
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                MyBox(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCategoryClick() }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            "Categories",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Lucide.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                MyBox(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrxTemplateClick() }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            "Templates",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Lucide.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                MyBox(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeToggleRequest() }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "Theme",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (darkTheme) "Dark" else "Light",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Icon(
                            imageVector = if (darkTheme) Lucide.Sun else Lucide.Moon,
                            contentDescription = "Toggle theme",
                            modifier = Modifier
                                .size(20.dp)
                                .onGloballyPositioned { coords ->
                                    val pos = coords.positionInRoot()
                                    val size = coords.size
                                    onThemeToggleOffsetChange(
                                        Offset(
                                            x = pos.x + size.width / 2f,
                                            y = pos.y + size.height / 2f,
                                        )
                                    )
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                appVersion()?.let {
                    Text(
                        text = "v$it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

