package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.duinch.adbagent.core.designsystem.AppIcons
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

private val CARD_GAP = 8.dp

/** The cards are rounded, so a square shadow would show around their corners while lifted. */
private val LiftedCardShape = RoundedCornerShape(18.dp)

// A swap needs the dragged card past the middle of its neighbour, plus a little so it cannot flicker back.
private const val SWAP_THRESHOLD = 0.55f

/**
 * Renders connection items inside a server card and lets the user drag to reorder.
 * The top connection is what the server is asked to prefer. A card for which [canDrag] is false
 * keeps long presses for itself: it may hold a text field.
 */
@Composable
internal fun ReorderableConnectionList(
    connectionIds: List<String>,
    onReorder: (List<String>) -> Unit,
    canDrag: (String) -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (id: String) -> Unit
) {
    if (connectionIds.size <= 1) {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CARD_GAP)) {
            connectionIds.forEach { id -> key(id) { content(id) } }
        }
        return
    }

    var items by remember(connectionIds) { mutableStateOf(connectionIds) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<String, Int>() }
    val haptics = LocalHapticFeedback.current
    val gapPx = with(LocalDensity.current) { CARD_GAP.toPx() }
    val latestOnReorder by rememberUpdatedState(onReorder)
    val latestConnectionIds by rememberUpdatedState(connectionIds)

    fun moveWithFinger(dy: Float) {
        val id = draggingId ?: return
        dragOffsetY += dy
        val from = items.indexOf(id)
        val neighbour = items.getOrNull(if (dragOffsetY > 0f) from + 1 else from - 1)
        if (neighbour == null) {
            // Past the end of the list: the server frame would clip the card.
            dragOffsetY = 0f
            return
        }
        val step = (heights[neighbour] ?: return) + gapPx
        if (abs(dragOffsetY) < step * SWAP_THRESHOLD) return
        val to = if (dragOffsetY > 0f) from + 1 else from - 1
        items = items.toMutableList().apply { add(to, removeAt(from)) }
        // The card's slot just moved a step, so keep it under the finger.
        dragOffsetY -= if (dragOffsetY > 0f) step else -step
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CARD_GAP)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = AppIcons.ArrowUpDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = "Nhấn giữ và kéo để sắp xếp ưu tiên",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }

        items.forEach { id ->
            key(id) {
                val isDragging = draggingId == id
                val elevation by animateFloatAsState(if (isDragging) 12f else 0f, label = "reorder_elevation")
                val scale by animateFloatAsState(if (isDragging) 1.02f else 1f, label = "reorder_scale")
                val draggable = canDrag(id)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 10f else 1f)
                        .onSizeChanged { heights[id] = it.height }
                        // Outside the layer: inside it, the touch would be measured from a card moving with the finger.
                        .pointerInput(id, draggable) {
                            if (!draggable) return@pointerInput
                            detectLongPressDrag(
                                onStart = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    draggingId = id
                                    dragOffsetY = 0f
                                },
                                onDrag = ::moveWithFinger,
                                onEnd = {
                                    draggingId = null
                                    dragOffsetY = 0f
                                    if (items != latestConnectionIds) latestOnReorder(items)
                                },
                                onCancel = {
                                    draggingId = null
                                    dragOffsetY = 0f
                                    items = latestConnectionIds
                                }
                            )
                        }
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffsetY else 0f
                            shadowElevation = elevation.dp.toPx()
                            shape = LiftedCardShape
                            clip = false
                            scaleX = scale
                            scaleY = scale
                        }
                ) {
                    content(id)
                }
            }
        }
    }
}

/**
 * Watches the Initial pass, ahead of the cards inside: once held, every move is consumed so the
 * card's swipe, its press ripple and the page scroll all stand down and the drag owns the touch.
 */
private suspend fun PointerInputScope.detectLongPressDrag(
    onStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    // Null means the wait ran out with the finger still down: that is the long press.
    val endedEarly: Boolean = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        var ended = false
        while (!ended) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
            if (change == null || !change.pressed ||
                (change.position - down.position).getDistance() > viewConfiguration.touchSlop
            ) {
                ended = true
            }
        }
        true
    } ?: false
    if (endedEarly) return@awaitEachGesture

    onStart()
    try {
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
            if (change == null) {
                onCancel()
                return@awaitEachGesture
            }
            val dy = change.positionChangeIgnoreConsumed().y
            change.consume()
            if (!change.pressed) {
                onEnd()
                return@awaitEachGesture
            }
            onDrag(dy)
        }
    } catch (e: CancellationException) {
        onCancel()
        throw e
    }
}
