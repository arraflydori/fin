package dev.nichidori.saku.feature.categoryList

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.GripVertical
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import dev.nichidori.saku.core.composable.*
import dev.nichidori.saku.core.model.toPickerIcon
import dev.nichidori.saku.core.util.collectAsStateWithLifecycleIfAvailable
import dev.nichidori.saku.domain.model.Category
import dev.nichidori.saku.domain.model.TrxType
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun CategoryListPage(
    viewModel: CategoryListViewModel,
    onUp: () -> Unit,
    onNewCategoryClick: (TrxType) -> Unit,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycleIfAvailable()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    CategoryListContent(
        uiState = uiState,
        onUp = onUp,
        onSelectedTypeChange = viewModel::onSelectedTypeChange,
        onReorder = viewModel::onReorder,
        onNewCategoryClick = onNewCategoryClick,
        onCategoryClick = onCategoryClick,
        modifier = modifier
    )
}

@Composable
fun CategoryListContent(
    uiState: CategoryListUiState,
    onUp: () -> Unit,
    onSelectedTypeChange: (TrxType) -> Unit,
    onReorder: (Int, Int) -> Unit,
    onNewCategoryClick: (TrxType) -> Unit,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            MyAppBar(
                title = "Categories",
                onUp = onUp,
                action = {
                    MyIconButton(
                        onClick = { onNewCategoryClick(uiState.selectedType) }
                    ) {
                        Icon(
                            imageVector = Lucide.Plus,
                            contentDescription = "Add category"
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
            )
        },
        modifier = modifier,
    ) { contentPadding ->
        Column(modifier = Modifier.padding(contentPadding)) {
            MySegmentedControl(
                items = listOf(TrxType.Income, TrxType.Expense),
                selectedItem = uiState.selectedType,
                onItemSelection = onSelectedTypeChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) { type ->
                Text(text = if (type == TrxType.Income) "Income" else "Expense")
            }
            val categoriesByParent = if (uiState.selectedType == TrxType.Income) {
                uiState.incomesByParent
            } else {
                uiState.expensesByParent
            }
            if (categoriesByParent.isNotEmpty()) {
                val haptic = LocalHapticFeedback.current
                val lazyListState = rememberLazyListState()
                val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                    onReorder(from.index, to.index)
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                }

                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    modifier = Modifier.weight(1f).padding(top = 16.dp),
                ) {
                    items(categoriesByParent.size, key = { categoriesByParent[it].first.id }) { idx ->
                        val (parent, children) = categoriesByParent[idx]
                        ReorderableItem(reorderableState, key = parent.id) { isDragging ->
                            val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
                            Surface(
                                shadowElevation = elevation,
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .animateItem(fadeInSpec = null, fadeOutSpec = null)
                                    .padding(top = if (idx != 0) 16.dp else 0.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    CategoryCardWithHandle(
                                        category = parent,
                                        onClick = { onCategoryClick(it) },
                                        modifier = Modifier.fillMaxWidth(),
                                        reorderScope = this@ReorderableItem,
                                        haptic = haptic
                                    )
                                    children.forEachIndexed { index, child ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(IntrinsicSize.Min)
                                                .padding(top = 16.dp)
                                        ) {
                                            ChildNodeIndicator(
                                                isLast = index == children.lastIndex,
                                                topPadding = 16.dp,
                                                modifier = Modifier.fillMaxHeight()
                                                    .width((48 + 4).dp)
                                            )
                                            CategoryCard(
                                                category = child,
                                                onClick = { onCategoryClick(it) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                MyNoData(
                    message = "No categories yet",
                    contentDescription = "No categories",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun CategoryCard(
    category: Category,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    MyBox(
        modifier = modifier
            .clip(MyDefaultShape)
            .clickable { onClick(category.id) }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp)
            ) {
                val icon = category.icon.toPickerIcon()?.icon
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = category.name,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        category.name.firstOrNull()?.toString() ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                category.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Lucide.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
private fun CategoryCardWithHandle(
    category: Category,
    onClick: (String) -> Unit,
    reorderScope: ReorderableCollectionItemScope,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    modifier: Modifier = Modifier,
) {
    MyBox(
        modifier = modifier
            .clip(MyDefaultShape)
            .clickable { onClick(category.id) }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp)
            ) {
                val icon = category.icon.toPickerIcon()?.icon
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = category.name,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        category.name.firstOrNull()?.toString() ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                category.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            with(reorderScope) {
                Icon(
                    imageVector = Lucide.GripVertical,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .draggableHandle(
                            onDragStarted = {
                                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            },
                            onDragStopped = {
                                haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                            }
                        )
                        .size(32.dp)
                        .padding(6.dp)
                )
            }
            Icon(
                imageVector = Lucide.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
fun ChildNodeIndicator(
    modifier: Modifier = Modifier,
    isLast: Boolean = false,
    topPadding: Dp = 0.dp,
) {
    val density = LocalDensity.current
    val strokeWidth = with(density) { 2.dp.toPx() }
    val topPaddingPx = with(density) { topPadding.toPx() }
    val color = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = topPaddingPx + (size.height - topPaddingPx) / 2

        drawLine(
            color = color,
            start = Offset(x = centerX, y = 0f),
            end = Offset(x = centerX, y = if (isLast) centerY else size.height),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(x = centerX, y = centerY),
            end = Offset(x = size.width, y = centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}
