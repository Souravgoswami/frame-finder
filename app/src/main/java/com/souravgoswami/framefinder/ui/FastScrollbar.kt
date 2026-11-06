package com.souravgoswami.framefinder.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
internal fun BoxScope.LazyGridFastScrollbar(
    state: LazyGridState,
    modifier: Modifier = Modifier
) {
    val metrics by remember(state) {
        derivedStateOf {
            val layout = state.layoutInfo
            val visibleItems = layout.visibleItemsInfo
            FastScrollMetrics(
                totalItems = layout.totalItemsCount,
                visibleItems = visibleItems.size,
                firstVisibleItem = state.firstVisibleItemIndex,
                firstVisibleItemOffset = state.firstVisibleItemScrollOffset,
                lanes = ((visibleItems.maxOfOrNull { it.column } ?: 0) + 1).coerceAtLeast(1),
                itemExtent = (visibleItems.maxOfOrNull { it.size.height } ?: 0) + layout.mainAxisItemSpacing,
                isScrollInProgress = state.isScrollInProgress
            )
        }
    }
    DraggableFastScrollbar(
        metrics = metrics,
        onScrollToItem = { index, offset -> state.scrollToItem(index, offset) },
        modifier = modifier.align(Alignment.CenterEnd)
    )
}

@Composable
internal fun BoxScope.LazyListFastScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier
) {
    val metrics by remember(state) {
        derivedStateOf {
            val layout = state.layoutInfo
            val visibleItems = layout.visibleItemsInfo
            FastScrollMetrics(
                totalItems = layout.totalItemsCount,
                visibleItems = visibleItems.size,
                firstVisibleItem = state.firstVisibleItemIndex,
                firstVisibleItemOffset = state.firstVisibleItemScrollOffset,
                lanes = 1,
                itemExtent = (visibleItems.maxOfOrNull { it.size } ?: 0) + layout.mainAxisItemSpacing,
                isScrollInProgress = state.isScrollInProgress
            )
        }
    }
    DraggableFastScrollbar(
        metrics = metrics,
        onScrollToItem = { index, offset -> state.scrollToItem(index, offset) },
        modifier = modifier.align(Alignment.CenterEnd)
    )
}

@Composable
private fun DraggableFastScrollbar(
    metrics: FastScrollMetrics,
    onScrollToItem: suspend (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleItems = metrics.visibleItems.coerceAtLeast(1)
    if (metrics.totalItems <= visibleItems) return

    val density = LocalDensity.current
    val currentScrollToItem by rememberUpdatedState(onScrollToItem)
    val scrollRequests = remember { Channel<FastScrollRequest>(Channel.CONFLATED) }
    val requestState = remember { FastScrollRequestState() }
    var draggedFraction by remember { mutableStateOf<Float?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    val maxFirstItem = max(1, metrics.totalItems - visibleItems)
    val itemExtent = metrics.itemExtent.coerceAtLeast(1)
    val firstVisiblePosition = metrics.firstVisibleItem +
        metrics.firstVisibleItemOffset.toFloat() / itemExtent * metrics.lanes
    val scrollFraction = (firstVisiblePosition / maxFirstItem).coerceIn(0f, 1f)
    val animatedScrollFraction by animateFloatAsState(
        targetValue = scrollFraction,
        animationSpec = tween(durationMillis = 90),
        label = "Fast scrollbar position"
    )
    val thumbFraction = draggedFraction ?: animatedScrollFraction
    val active = isDragging || metrics.isScrollInProgress
    val trackAlpha by animateFloatAsState(
        targetValue = if (active) 0.18f else 0.07f,
        animationSpec = tween(durationMillis = 120),
        label = "Fast scrollbar track alpha"
    )
    val thumbAlpha by animateFloatAsState(
        targetValue = if (active) 0.90f else 0.58f,
        animationSpec = tween(durationMillis = 120),
        label = "Fast scrollbar thumb alpha"
    )

    LaunchedEffect(scrollRequests) {
        for (request in scrollRequests) {
            try {
                currentScrollToItem(request.target.itemIndex, request.target.scrollOffset)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IllegalArgumentException) {
                // The data set can shrink while a drag is in progress.
            } finally {
                if (!requestState.isDragging && request.id == requestState.latestRequestId) {
                    draggedFraction = null
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { scrollRequests.close() }
    }

    BoxWithConstraints(
        modifier
            .fillMaxHeight()
            .width(24.dp)
            .padding(vertical = 4.dp)
            .semantics {
                contentDescription = "Fast scroll"
                stateDescription = "${(thumbFraction * 100).roundToInt()} percent"
            }
    ) {
        val trackHeightPx = with(density) { maxHeight.toPx() }
        val minimumThumbHeightPx = with(density) { 40.dp.toPx() }.coerceAtMost(trackHeightPx)
        val visibleFraction = visibleItems.toFloat() / metrics.totalItems
        val thumbHeightPx = (trackHeightPx * visibleFraction)
            .coerceIn(minimumThumbHeightPx, trackHeightPx)
        val availableTravelPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
        val thumbOffsetPx = availableTravelPx * thumbFraction
        val currentThumbFraction by rememberUpdatedState(thumbFraction)

        fun requestScroll(requestedFraction: Float) {
            val fraction = requestedFraction.coerceIn(0f, 1f)
            draggedFraction = fraction
            val target = fastScrollTarget(
                fraction = fraction,
                maxFirstItem = maxFirstItem,
                lanes = metrics.lanes,
                itemExtent = itemExtent
            )
            val requestId = ++requestState.latestRequestId
            scrollRequests.trySend(FastScrollRequest(requestId, target))
        }

        fun scrollToPosition(pointerY: Float) {
            val requestedFraction = ((pointerY - requestState.grabOffset) / availableTravelPx)
                .coerceIn(0f, 1f)
            requestScroll(requestedFraction)
        }

        fun finishDrag() {
            requestState.isDragging = false
            isDragging = false
            draggedFraction?.let(::requestScroll)
        }

        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 2.dp)
                .fillMaxHeight()
                .width(2.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = trackAlpha))
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(y = with(density) { thumbOffsetPx.toDp() })
                .padding(end = 1.dp)
                .width(6.dp)
                .height(with(density) { thumbHeightPx.toDp() })
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = thumbAlpha))
        )
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(metrics.totalItems, trackHeightPx) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            requestState.isDragging = true
                            isDragging = true
                            val thumbTop = availableTravelPx * currentThumbFraction
                            requestState.grabOffset = if (offset.y in thumbTop..(thumbTop + thumbHeightPx)) {
                                offset.y - thumbTop
                            } else {
                                thumbHeightPx / 2f
                            }
                            scrollToPosition(offset.y)
                        },
                        onDragEnd = ::finishDrag,
                        onDragCancel = ::finishDrag,
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            scrollToPosition(change.position.y)
                        }
                    )
                }
        )
    }
}

private data class FastScrollMetrics(
    val totalItems: Int,
    val visibleItems: Int,
    val firstVisibleItem: Int,
    val firstVisibleItemOffset: Int,
    val lanes: Int,
    val itemExtent: Int,
    val isScrollInProgress: Boolean
)

internal data class FastScrollTarget(val itemIndex: Int, val scrollOffset: Int)

internal fun fastScrollTarget(
    fraction: Float,
    maxFirstItem: Int,
    lanes: Int,
    itemExtent: Int
): FastScrollTarget {
    val safeLanes = lanes.coerceAtLeast(1)
    val continuousItem = fraction.coerceIn(0f, 1f) * maxFirstItem.coerceAtLeast(0)
    val continuousLine = continuousItem / safeLanes
    val line = floor(continuousLine)
    return FastScrollTarget(
        itemIndex = (line * safeLanes).roundToInt().coerceIn(0, maxFirstItem.coerceAtLeast(0)),
        scrollOffset = ((continuousLine - line) * itemExtent.coerceAtLeast(1)).roundToInt()
    )
}

private data class FastScrollRequest(val id: Long, val target: FastScrollTarget)

private class FastScrollRequestState(
    var latestRequestId: Long = 0,
    var isDragging: Boolean = false,
    var grabOffset: Float = 0f
)
