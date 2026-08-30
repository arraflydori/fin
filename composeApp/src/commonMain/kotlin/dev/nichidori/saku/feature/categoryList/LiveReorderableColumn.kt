package dev.nichidori.saku.feature.categoryList

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import sh.calvin.reorderable.DragGestureDetector

internal data class LiveItemInterval(val start: Float = 0f, val size: Int = 0) {
    val center: Float get() = start + size / 2
    val end: Float get() = start + size
}

private inline fun <T> List<T>.firstIndexOfIndexed(predicate: (Int, T) -> Boolean): Int? {
    forEachIndexed { i, e -> if (predicate(i, e)) return i }
    return null
}

private inline fun <T> List<T>.lastIndexOfIndexed(predicate: (Int, T) -> Boolean): Int? {
    for (i in lastIndex downTo 0) if (predicate(i, this[i])) return i
    return null
}

private val liveAnimationSpec = spring<Float>(stiffness = Spring.StiffnessMediumLow)

internal fun Modifier.liveDraggable(
    key1: Any?,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    dragGestureDetector: DragGestureDetector = DragGestureDetector.Press,
    onDragStarted: (Offset) -> Unit = { },
    onDragStopped: () -> Unit = { },
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) = composed {
    val coroutineScope = rememberCoroutineScope()
    var dragInteractionStart by remember { mutableStateOf<DragInteraction.Start?>(null) }
    var dragStarted by remember { mutableStateOf(false) }
    DisposableEffect(key1) {
        onDispose {
            if (dragStarted) {
                dragInteractionStart?.also { coroutineScope.launch { interactionSource?.emit(DragInteraction.Cancel(it)) } }
                if (dragStarted) onDragStopped()
                dragStarted = false
            }
        }
    }
    pointerInput(key1, enabled) {
        if (!enabled) return@pointerInput
        with(dragGestureDetector) {
            detect(
                onDragStart = {
                    dragStarted = true
                    dragInteractionStart = DragInteraction.Start().also { coroutineScope.launch { interactionSource?.emit(it) } }
                    onDragStarted(it)
                },
                onDragEnd = {
                    dragInteractionStart?.also { coroutineScope.launch { interactionSource?.emit(DragInteraction.Stop(it)) } }
                    if (dragStarted) onDragStopped()
                    dragStarted = false
                },
                onDragCancel = {
                    dragInteractionStart?.also { coroutineScope.launch { interactionSource?.emit(DragInteraction.Cancel(it)) } }
                    if (dragStarted) onDragStopped()
                    dragStarted = false
                },
                onDrag = onDrag,
            )
        }
    }
}



class LiveReorderableListState internal constructor(
    listSize: Int,
    spacing: Float = 0f,
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    private val onSettle: (fromIndex: Int, toIndex: Int) -> Unit,
    scope: CoroutineScope,
    val orientation: Orientation,
    private val layoutDirection: LayoutDirection,
) {
    internal val itemIntervals = MutableList(listSize) { LiveItemInterval() }
    internal val itemOffsets = List(listSize) { Animatable(0f) }.toMutableStateList()
    private var draggingItemIndex by mutableStateOf<Int?>(null)
    private var animatingItemIndex by mutableStateOf<Int?>(null)
    private var currentTargetIndex by mutableStateOf<Int?>(null)
    internal val isAnyItemDragging by derivedStateOf { draggingItemIndex != null }
    val draggingIndex: Int? get() = draggingItemIndex
    val targetIndexLive: Int? get() = currentTargetIndex

    internal val draggableStates = List(listSize) { i ->
        DraggableState {
            if (!isItemDragging(i).value) return@DraggableState
            scope.launch { itemOffsets[i].snapTo(itemOffsets[i].targetValue + it) }
            val originalStart = itemIntervals[i].start
            val originalEnd = itemIntervals[i].end
            val size = itemIntervals[i].size
            val currentStart = itemIntervals[i].start + itemOffsets[i].targetValue
            val currentEnd = currentStart + size
            var moved = false
            itemIntervals.forEachIndexed { j, interval ->
                if (j != i) {
                    val targetOffset = if (currentStart < originalStart && interval.center in currentStart..originalStart) {
                        size.toFloat() + spacing
                    } else if (currentStart > originalStart && interval.center in originalEnd..currentEnd) {
                        -(size.toFloat() + spacing)
                    } else 0f
                    if (itemOffsets[j].targetValue != targetOffset) {
                        scope.launch { itemOffsets[j].animateTo(targetOffset, liveAnimationSpec) }
                        moved = true
                    }
                }
            }
            // compute live target for external isLast handling
            val targetIndexFunc = if (currentStart < originalStart) { j: Int, interval: LiveItemInterval ->
                j != i && interval.center in currentStart..<originalStart
            } else if (currentStart > originalStart) { j: Int, interval: LiveItemInterval ->
                j != i && interval.center in originalEnd..<currentEnd
            } else null
            val targetIndex = targetIndexFunc?.let {
                if (orientation == Orientation.Horizontal && layoutDirection == LayoutDirection.Rtl) {
                    if (currentStart < originalStart) itemIntervals.lastIndexOfIndexed(it)
                    else if (currentStart > originalStart) itemIntervals.firstIndexOfIndexed(it)
                    else null
                } else {
                    if (currentStart < originalStart) itemIntervals.firstIndexOfIndexed(it)
                    else if (currentStart > originalStart) itemIntervals.lastIndexOfIndexed(it)
                    else null
                }
            }
            if (targetIndex != null && targetIndex != currentTargetIndex) {
                val prev = currentTargetIndex
                currentTargetIndex = targetIndex
                // notify live move
                onMove(i, targetIndex)
            } else if (targetIndex == null && currentTargetIndex != null) {
                // dragged back to origin
                val origin = draggingItemIndex
                if (origin != null) {
                    currentTargetIndex = null
                    onMove(origin, origin)
                }
            }
            if (moved) {
                // keep for compatibility, but we already called onMove with indices
            }
        }
    }.toMutableStateList()

    internal fun isItemDragging(i: Int): State<Boolean> = derivedStateOf { i == draggingItemIndex }
    internal fun isItemAnimating(i: Int): State<Boolean> = derivedStateOf { i == animatingItemIndex }
    internal fun startDrag(i: Int) {
        draggingItemIndex = i
        animatingItemIndex = i
        currentTargetIndex = null
    }

    internal suspend fun settle(i: Int, velocity: Float) {
        val targetIndex = currentTargetIndex
        draggingItemIndex = null
        if (targetIndex != null) {
            val offsetToTarget = (itemIntervals[targetIndex].start - itemIntervals[i].start).let {
                if (it > 0) itemIntervals[targetIndex].end - itemIntervals[i].end else it
            }
            itemOffsets[i].animateTo(offsetToTarget, liveAnimationSpec, initialVelocity = velocity)
            onSettle(i, targetIndex)
        } else {
            itemOffsets[i].animateTo(0f, liveAnimationSpec, initialVelocity = velocity)
        }
        currentTargetIndex = null
        animatingItemIndex = null
    }
}

interface LiveReorderableListItemScope {
    fun Modifier.draggableHandle(
        enabled: Boolean = true,
        onDragStarted: (startedPosition: Offset) -> Unit = {},
        onDragStopped: (velocity: Float) -> Unit = {},
        interactionSource: MutableInteractionSource? = null,
        dragGestureDetector: DragGestureDetector = DragGestureDetector.Press
    ): Modifier
    fun Modifier.longPressDraggableHandle(
        enabled: Boolean = true,
        onDragStarted: (startedPosition: Offset) -> Unit = {},
        onDragStopped: (velocity: Float) -> Unit = {},
        interactionSource: MutableInteractionSource? = null,
    ): Modifier
}

internal class LiveReorderableListItemScopeImpl(
    private val state: LiveReorderableListState,
    private val index: Int,
) : LiveReorderableListItemScope {
    override fun Modifier.draggableHandle(
        enabled: Boolean,
        onDragStarted: (startedPosition: Offset) -> Unit,
        onDragStopped: (velocity: Float) -> Unit,
        interactionSource: MutableInteractionSource?,
        dragGestureDetector: DragGestureDetector,
    ) = composed {
        val velocityTracker = remember { VelocityTracker() }
        val coroutineScope = rememberCoroutineScope()
        liveDraggable(
            key1 = state,
            enabled = enabled && (state.isItemDragging(index).value || !state.isAnyItemDragging),
            interactionSource = interactionSource,
            dragGestureDetector = dragGestureDetector,
            onDragStarted = {
                state.startDrag(index)
                onDragStarted(it)
            },
            onDragStopped = {
                val velocity = velocityTracker.calculateVelocity()
                velocityTracker.resetTracking()
                val velocityVal = when (state.orientation) {
                    Orientation.Vertical -> velocity.y
                    Orientation.Horizontal -> velocity.x
                }
                coroutineScope.launch { state.settle(index, velocityVal) }
                onDragStopped(velocityVal)
            },
            onDrag = { change, dragAmount ->
                velocityTracker.addPointerInputChange(change)
                state.draggableStates[index].dispatchRawDelta(
                    when (state.orientation) {
                        Orientation.Vertical -> dragAmount.y
                        Orientation.Horizontal -> dragAmount.x
                    }
                )
            },
        )
    }
    override fun Modifier.longPressDraggableHandle(
        enabled: Boolean,
        onDragStarted: (startedPosition: Offset) -> Unit,
        onDragStopped: (velocity: Float) -> Unit,
        interactionSource: MutableInteractionSource?,
    ) = draggableHandle(enabled, onDragStarted, onDragStopped, interactionSource, DragGestureDetector.LongPress)
}

open class LiveReorderableListScope(
    private val state: LiveReorderableListState,
    private val orientation: Orientation,
    private val index: Int,
    private val isAnimating: Boolean,
) {
    @Composable
    fun ReorderableItem(
        modifier: Modifier = Modifier,
        content: @Composable LiveReorderableListItemScope.() -> Unit,
    ) {
        Box(
            modifier = modifier
                .onGloballyPositioned { cord ->
                    state.itemIntervals[index] = when (orientation) {
                        Orientation.Vertical -> LiveItemInterval(start = cord.positionInParent().y, size = cord.size.height)
                        Orientation.Horizontal -> LiveItemInterval(start = cord.positionInParent().x, size = cord.size.width)
                    }
                }
                .graphicsLayer {
                    when (orientation) {
                        Orientation.Vertical -> translationY = state.itemOffsets[index].value
                        Orientation.Horizontal -> translationX = state.itemOffsets[index].value
                    }
                },
        ) {
            LiveReorderableListItemScopeImpl(state = this@LiveReorderableListScope.state, index = this@LiveReorderableListScope.index).content()
        }
    }
}

class LiveReorderableColumnScope(
    state: LiveReorderableListState,
    index: Int,
    isAnimating: Boolean,
    private val scope: ColumnScope
) : LiveReorderableListScope(state, Orientation.Vertical, index, isAnimating), ColumnScope by scope

@Composable
fun <T> LiveReorderableColumn(
    list: List<T>,
    onSettle: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    content: @Composable LiveReorderableColumnScope.(index: Int, item: T, isDragging: Boolean) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val spacing = with(density) { verticalArrangement.spacing.toPx() }
    val layoutDirection = LocalLayoutDirection.current
    val reorderableListState = remember(list, spacing) {
        LiveReorderableListState(list.size, spacing, onMove, onSettle, coroutineScope, Orientation.Vertical, layoutDirection)
    }
    Column(modifier = modifier, verticalArrangement = verticalArrangement, horizontalAlignment = horizontalAlignment) {
        list.forEachIndexed { i, item ->
            val isDragging by reorderableListState.isItemDragging(i)
            val isAnimating by reorderableListState.isItemAnimating(i)
            LiveReorderableColumnScope(state = reorderableListState, index = i, isAnimating = isAnimating, scope = this).content(i, item, isDragging)
        }
    }
}
