package dev.nichidori.saku.feature.trx

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.*
import dev.nichidori.saku.core.composable.*
import dev.nichidori.saku.core.model.Status.Failure
import dev.nichidori.saku.core.model.Status.Success
import dev.nichidori.saku.core.platform.ToastDuration
import dev.nichidori.saku.core.platform.showToast
import dev.nichidori.saku.core.util.collectAsStateWithLifecycleIfAvailable
import dev.nichidori.saku.core.util.format
import dev.nichidori.saku.core.util.toRupiah
import dev.nichidori.saku.domain.model.*
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
fun TrxPage(
    viewModel: TrxViewModel,
    onUp: () -> Unit,
    onSaveSuccess: () -> Unit,
    onDeleteSuccess: (Trx) -> Unit,
    onCopySuccess: (Trx) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycleIfAvailable()

    uiState.saveStatus.let { status ->
        LaunchedEffect(status) {
            when (status) {
                is Success -> onSaveSuccess()
                is Failure -> showToast(
                    status.error.toString(),
                    duration = ToastDuration.Long
                )

                else -> {}
            }
        }
    }

    uiState.deleteStatus.let { status ->
        LaunchedEffect(status) {
            when (val status = status) {
                is Success -> onDeleteSuccess(status.data)
                is Failure -> showToast(
                    status.error.toString(),
                    duration = ToastDuration.Long
                )

                else -> {}
            }
        }
    }

    uiState.copyStatus.let { status ->
        LaunchedEffect(status) {
            when (val status = status) {
                is Success -> onCopySuccess(status.data)
                is Failure -> showToast(
                    status.error.toString(),
                    duration = ToastDuration.Long
                )

                else -> {}
            }
        }
    }

    if (!uiState.isLoading) {
        TrxPageContent(
            uiState = uiState,
            types = viewModel.types,
            onUp = onUp,
            onTypeChange = viewModel::onTypeChange,
            onTimeChange = viewModel::onTimeChange,
            onAmountChange = viewModel::onAmountChange,
            onDescriptionChange = viewModel::onDescriptionChange,
            onSourceAccountChange = viewModel::onSourceAccountChange,
            onTargetAccountChange = viewModel::onTargetAccountChange,
            onCategoryChange = viewModel::onCategoryChange,
            onEnableFeeToggle = viewModel::onEnableFeeToggle,
            onFeeAmountChange = viewModel::onFeeAmountChange,
            onFeeAccountChange = viewModel::onFeeAccountChange,
            onFeeCategoryChange = viewModel::onFeeCategoryChange,
            onEnableInstallmentToggle = viewModel::onEnableInstallmentToggle,
            onMonthsChange = viewModel::onMonthsChange,
            onMonthlyRateChange = viewModel::onMonthlyRateChange,
            onSaveClick = viewModel::saveTrx,
            onCopyClick = viewModel::copyTrx,
            onDeleteClick = viewModel::deleteTrx,
            modifier = modifier
        )
    }
}

@Composable
fun TrxPageContent(
    uiState: TrxUiState,
    types: List<TrxType>,
    onUp: () -> Unit,
    onTypeChange: (TrxType) -> Unit,
    onTimeChange: (Instant) -> Unit,
    onAmountChange: ((String) -> String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSourceAccountChange: (TrxAccount) -> Unit,
    onTargetAccountChange: (TrxAccount) -> Unit,
    onCategoryChange: (Category) -> Unit,
    onEnableFeeToggle: () -> Unit,
    onFeeAmountChange: ((String) -> String) -> Unit,
    onFeeAccountChange: (TrxAccount) -> Unit,
    onFeeCategoryChange: (Category) -> Unit,
    onEnableInstallmentToggle: () -> Unit,
    onMonthsChange: ((String) -> String) -> Unit,
    onMonthlyRateChange: ((String) -> String) -> Unit,
    onSaveClick: () -> Unit,
    onCopyClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showTimeInput by remember { mutableStateOf(false) }
    var showAmountInput by remember { mutableStateOf(false) }
    var showSourceAccountInput by remember { mutableStateOf(false) }
    var showTargetAccountInput by remember { mutableStateOf(false) }
    var showCategoryInput by remember { mutableStateOf(false) }
    var showFeeAmountInput by remember { mutableStateOf(false) }
    var showFeeAccountInput by remember { mutableStateOf(false) }
    var showFeeCategoryInput by remember { mutableStateOf(false) }
    var showMonthsInput by remember { mutableStateOf(false) }
    var showRateInput by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val isReadOnly = uiState.isReadOnly
    val amountDisplay = if (uiState.isAdjustment && uiState.sourceAccount is TrxAccount.Credit) {
        (-(uiState.amount ?: 0)).toRupiah()
    } else {
        uiState.amountFormatted
    }

    Scaffold(
        topBar = {
            MyAppBar(
                title = "Transaction",
                onUp = onUp,
                action = {
                    if (uiState.canCopy) {
                        MyIconButton(
                            content = {
                                Icon(
                                    imageVector = Lucide.Copy,
                                    contentDescription = "Copy transaction",
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = onCopyClick,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (uiState.canDelete) {
                        MyIconButton(
                            content = {
                                Icon(
                                    imageVector = Lucide.Trash,
                                    contentDescription = "Delete account",
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = onDeleteClick,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            )
        },
        bottomBar = {
            when {
                showTimeInput -> {
                    MyDateTimePicker(
                        startDateTime = (uiState.time ?: Clock.System.now())
                            .toLocalDateTime(TimeZone.currentSystemDefault()),
                        onDateTimePicked = {
                            onTimeChange(it.toInstant(TimeZone.currentSystemDefault()))
                        },
                    )
                }

                showAmountInput -> {
                    NumberKeyboard(
                        actionLabel = "Next",
                        onValueClick = {
                            onAmountChange { current ->
                                current + it
                            }
                        },
                        onDeleteClick = {
                            onAmountChange { current ->
                                current.dropLast(1)
                            }
                        },
                        onActionClick = {
                            focusManager.moveFocus(FocusDirection.Next)
                        },
                    )
                }

                showSourceAccountInput -> {
                    AccountSelector(
                        accounts = uiState.accountOptions,
                        onSelected = {
                            onSourceAccountChange(it)
                            focusManager.moveFocus(FocusDirection.Next)
                        },
                        selectedWhen = { it == uiState.sourceAccount },
                        enabledWhen = { it != uiState.targetAccount },
                    )
                }

                showTargetAccountInput -> {
                    AccountSelector(
                        accounts = uiState.accountOptions,
                        onSelected = {
                            onTargetAccountChange(it)
                            focusManager.moveFocus(FocusDirection.Next)
                        },
                        selectedWhen = { it == uiState.targetAccount },
                        enabledWhen = { it != uiState.sourceAccount },
                    )
                }

                showCategoryInput -> {
                    val hasNestedCategories = remember(uiState.categoriesByParent) {
                        uiState.categoriesByParent.values.any { it.isNotEmpty() }
                    }
                    var selectedParent by remember(uiState.category, uiState.type, hasNestedCategories) {
                        mutableStateOf(
                            if (hasNestedCategories) {
                                uiState.categoriesByParent.keys.firstOrNull {
                                    it.id == uiState.category?.id || it.id == uiState.category?.parent?.id
                                } ?: uiState.categoriesByParent.keys.firstOrNull()
                            } else null
                        )
                    }
                    val categories by remember(selectedParent, hasNestedCategories) {
                        derivedStateOf {
                            if (hasNestedCategories) {
                                uiState.categoriesByParent[selectedParent] ?: emptyList()
                            } else {
                                uiState.categoriesByParent.keys.toList()
                            }
                        }
                    }

                    CategorySelector(
                        categories = categories,
                        onSelected = {
                            onCategoryChange(it)
                            focusManager.clearFocus()
                        },
                        selectedWhen = { it.id == uiState.category?.id },
                        header = {
                            if (hasNestedCategories) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 12.dp)
                                ) {
                                    items(uiState.categoriesByParent.keys.toList()) { parent ->
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .background(
                                                    color = if (parent == selectedParent) {
                                                        MaterialTheme.colorScheme.primaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.background
                                                    },
                                                    shape = MyDefaultShape
                                                )
                                                .clip(MyDefaultShape)
                                                .focusProperties { canFocus = false }
                                                .clickable {
                                                    if (uiState.categoriesByParent[parent]?.isNotEmpty() != true) {
                                                        selectedParent = parent
                                                        onCategoryChange(parent)
                                                        focusManager.clearFocus()
                                                    } else {
                                                        selectedParent = parent
                                                    }
                                                }
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                parent.name,
                                                textAlign = TextAlign.Center,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (parent == selectedParent) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onBackground
                                                },
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    )
                }

                showFeeAmountInput -> {
                    NumberKeyboard(
                        actionLabel = "Next",
                        onValueClick = {
                            onFeeAmountChange { current ->
                                current + it
                            }
                        },
                        onDeleteClick = {
                            onFeeAmountChange { current ->
                                current.dropLast(1)
                            }
                        },
                        onActionClick = {
                            focusManager.moveFocus(FocusDirection.Next)
                        },
                    )
                }

                showFeeAccountInput -> {
                    AccountSelector(
                        accounts = uiState.accountOptions,
                        onSelected = {
                            onFeeAccountChange(it)
                            focusManager.moveFocus(FocusDirection.Next)
                        },
                        selectedWhen = { it == (uiState.feeAccount ?: uiState.sourceAccount) },
                        enabledWhen = { true },
                    )
                }

                showFeeCategoryInput -> {
                    val hasNestedCategories = remember(uiState.expensesByParent) {
                        uiState.expensesByParent.values.any { it.isNotEmpty() }
                    }
                    var selectedParent by remember(uiState.feeCategory, hasNestedCategories) {
                        mutableStateOf(
                            if (hasNestedCategories) {
                                uiState.expensesByParent.keys.firstOrNull {
                                    it.id == uiState.feeCategory?.id || it.id == uiState.feeCategory?.parent?.id
                                } ?: uiState.expensesByParent.keys.firstOrNull()
                            } else null
                        )
                    }
                    val categories by remember(selectedParent, hasNestedCategories) {
                        derivedStateOf {
                            if (hasNestedCategories) {
                                uiState.expensesByParent[selectedParent] ?: emptyList()
                            } else {
                                uiState.expensesByParent.keys.toList()
                            }
                        }
                    }

                    CategorySelector(
                        categories = categories,
                        onSelected = {
                            onFeeCategoryChange(it)
                            focusManager.clearFocus()
                        },
                        selectedWhen = { it.id == uiState.feeCategory?.id },
                        header = {
                            if (hasNestedCategories) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    items(uiState.expensesByParent.keys.toList()) { parent ->
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .background(
                                                    color = if (parent == selectedParent) {
                                                        MaterialTheme.colorScheme.primaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.background
                                                    },
                                                    shape = MyDefaultShape
                                                )
                                                .clip(MyDefaultShape)
                                                .focusProperties { canFocus = false }
                                                .clickable {
                                                    if (uiState.expensesByParent[parent]?.isNotEmpty() != true) {
                                                        selectedParent = parent
                                                        onFeeCategoryChange(parent)
                                                        focusManager.clearFocus()
                                                    } else {
                                                        selectedParent = parent
                                                    }
                                                }
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                parent.name,
                                                textAlign = TextAlign.Center,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (parent == selectedParent) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onBackground
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    )
                }

                showMonthsInput -> {
                    NumberKeyboard(
                        actionLabel = "Next",
                        onValueClick = {
                            onMonthsChange { current ->
                                current + it
                            }
                        },
                        onDeleteClick = {
                            onMonthsChange { current ->
                                current.dropLast(1)
                            }
                        },
                        onActionClick = {
                            focusManager.moveFocus(FocusDirection.Next)
                        },
                    )
                }

                showRateInput -> {
                    NumberKeyboard(
                        actionLabel = "Next",
                        showDecimal = true,
                        onDecimalClick = {
                            onMonthlyRateChange { current ->
                                current + "."
                            }
                        },
                        onValueClick = {
                            onMonthlyRateChange { current ->
                                current + it
                            }
                        },
                        onDeleteClick = {
                            onMonthlyRateChange { current ->
                                current.dropLast(1)
                            }
                        },
                        onActionClick = {
                            focusManager.moveFocus(FocusDirection.Next)
                        },
                    )
                }

                else -> {
                    MyButton(
                        text = "Save",
                        enabled = uiState.canSave,
                        onClick = onSaveClick,
                        modifier = modifier
                            .background(color = MaterialTheme.colorScheme.background)
                            .navigationBarsPadding()
                            .padding(16.dp)
                    )
                }
            }
        }
    ) { contentPadding ->
        Column(
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .consumeWindowInsets(contentPadding)
                .imePadding()
                .padding(bottom = 20.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                MySegmentedControl(
                    items = types,
                    selectedItem = uiState.type,
                    onItemSelection = { type ->
                        if (!isReadOnly) {
                            onTypeChange(type)
                            showTargetAccountInput = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (uiState.isAdjustment) 0.5f else 1f)
                ) { type ->
                    Text(
                        text = when (type) {
                            TrxType.Income -> "Income"
                            TrxType.Expense -> "Expense"
                            TrxType.Transfer -> "Transfer"
                            TrxType.Adjustment -> "Adjustment"
                        },
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))

                MyLargeTextField(
                    value = amountDisplay,
                    onValueChange = { },
                    enabled = !isReadOnly,
                    readOnly = true,
                    trailingIcon = if (uiState.type == TrxType.Expense && uiState.installment is InstallmentInfo.Installment) {
                        val installment = uiState.installment
                        {
                            Text(
                                text = "Installment ${installment.index + 1}/${installment.totalMonths}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else null,
                    modifier = Modifier
                        .onFocusChanged { focusState ->
                            showAmountInput = focusState.isFocused
                        }
                )
                Spacer(modifier = Modifier.height(24.dp))

                MyTextField(
                    value = uiState.description,
                    onValueChange = onDescriptionChange,
                    label = "Description",
                    enabled = !isReadOnly,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))

                val sourceFieldModifier = if (uiState.isAdjustment) {
                    Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            showSourceAccountInput = focusState.isFocused
                        }
                } else {
                    Modifier
                        .weight(1f)
                        .onFocusChanged { focusState ->
                            showSourceAccountInput = focusState.isFocused
                        }
                }
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    MyTextField(
                        value = uiState.sourceAccount?.name.orEmpty(),
                        onValueChange = { },
                        label = if (uiState.type == TrxType.Transfer) "From" else "Account",
                        enabled = !isReadOnly && (uiState.accountOptions.isNotEmpty() || uiState.sourceAccount?.isDeleted == true),
                        readOnly = true,
                        dimmed = uiState.sourceAccount?.isDeleted == true,
                        modifier = sourceFieldModifier
                    )

                    if (uiState.type == TrxType.Transfer) {
                        Spacer(modifier = Modifier.width(24.dp))
                        MyTextField(
                            value = uiState.targetAccount?.name.orEmpty(),
                            onValueChange = { },
                            label = "To",
                            enabled = !isReadOnly && (uiState.accountOptions.isNotEmpty() || uiState.targetAccount?.isDeleted == true),
                            readOnly = true,
                            dimmed = uiState.targetAccount?.isDeleted == true,
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { focusState ->
                                    showTargetAccountInput = focusState.isFocused
                                }
                        )
                    } else if (!uiState.isAdjustment) {
                        Spacer(modifier = Modifier.width(24.dp))
                        MyTextField(
                            value = uiState.category?.name.orEmpty(),
                            onValueChange = { },
                            label = "Category",
                            enabled = !isReadOnly && uiState.categoriesByParent.isNotEmpty(),
                            readOnly = true,
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { focusState ->
                                    showCategoryInput = focusState.isFocused
                                }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                MyTextField(
                    value = uiState.time?.format(
                        LocalDateTime.Format {
                            day()
                            chars(" ")
                            monthName(MonthNames.ENGLISH_ABBREVIATED)
                            chars(" ")
                            year()
                            chars(" ")
                            hour()
                            chars(":")
                            minute()
                        }
                    ).orEmpty(),
                    onValueChange = { },
                    label = "Time",
                    enabled = !isReadOnly,
                    readOnly = true,
                    trailingIcon = {
                        if (!isReadOnly) {
                            MyTextButton(
                                text = "Now",
                                onClick = {
                                    onTimeChange(Clock.System.now())
                                },
                            )
                        }
                    },
                    modifier = Modifier
                        .onFocusChanged { focusState ->
                            showTimeInput = focusState.isFocused
                        }
                )
            }

            AnimatedVisibility(visible = uiState.type == TrxType.Transfer && !uiState.canDelete) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(40.dp))

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp)
                    ) {
                        Text(
                            text = "Additional Fee",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        MyIconButton(onClick = onEnableFeeToggle) {
                            Icon(
                                imageVector = if (uiState.enableFee) Lucide.X else Lucide.Plus,
                                contentDescription = if (uiState.enableFee) "Hide Additional Fee"
                                else "Show Additional Fee",
                            )
                        }
                    }

                    AnimatedVisibility(visible = uiState.enableFee) {
                        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                            Spacer(modifier = Modifier.height(8.dp))

                            MyLargeTextField(
                                value = uiState.feeAmountFormatted,
                                onValueChange = { },
                                readOnly = true,
                                modifier = Modifier
                                    .onFocusChanged { focusState ->
                                        showFeeAmountInput = focusState.isFocused
                                    }
                            )
                            Spacer(modifier = Modifier.height(24.dp))

                            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                                MyTextField(
                                    value = (uiState.feeAccount ?: uiState.sourceAccount)?.name.orEmpty(),
                                    onValueChange = { },
                                    label = "Account",
                                    enabled = uiState.accountOptions.isNotEmpty()
                                        || (uiState.feeAccount ?: uiState.sourceAccount)?.isDeleted == true,
                                    readOnly = true,
                                    dimmed = (uiState.feeAccount ?: uiState.sourceAccount)?.isDeleted == true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .onFocusChanged { focusState ->
                                            showFeeAccountInput = focusState.isFocused
                                        }
                                )
                                Spacer(modifier = Modifier.width(24.dp))

                                MyTextField(
                                    value = uiState.feeCategory?.name.orEmpty(),
                                    onValueChange = { },
                                    label = "Category",
                                    enabled = uiState.expensesByParent.isNotEmpty(),
                                    readOnly = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .onFocusChanged { focusState ->
                                            showFeeCategoryInput = focusState.isFocused
                                        }
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = uiState.type == TrxType.Expense
                    && uiState.sourceAccount is TrxAccount.Credit
                    && !uiState.canDelete
                    && uiState.installment == null
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(40.dp))

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp)
                    ) {
                        Text(
                            text = "Installment",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        MyIconButton(onClick = onEnableInstallmentToggle) {
                            Icon(
                                imageVector = if (uiState.enableInstallment) Lucide.X else Lucide.Plus,
                                contentDescription = if (uiState.enableInstallment) "Hide Installment"
                                else "Show Installment",
                            )
                        }
                    }

                    AnimatedVisibility(visible = uiState.enableInstallment) {
                        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                            Spacer(modifier = Modifier.height(8.dp))

                            MyTextField(
                                value = uiState.monthsFormatted,
                                onValueChange = { },
                                label = "Total Months",
                                readOnly = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { focusState ->
                                        showMonthsInput = focusState.isFocused
                                    }
                            )
                            Spacer(modifier = Modifier.height(24.dp))

                            MyTextField(
                                value = uiState.monthlyRateFormatted,
                                onValueChange = { },
                                label = "Monthly Interest (%)",
                                readOnly = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { focusState ->
                                        showRateInput = focusState.isFocused
                                    }
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = uiState.installment is InstallmentInfo.Charge) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(40.dp))

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Installment",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 20.dp, end = 12.dp)
                    )

                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Spacer(modifier = Modifier.height(8.dp))

                        MyTextField(
                            value = uiState.monthsFormatted,
                            onValueChange = { },
                            label = "Total Months",
                            enabled = false,
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        MyTextField(
                            value = uiState.monthlyRateFormatted,
                            onValueChange = { },
                            label = "Monthly Interest (%)",
                            enabled = false,
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}


