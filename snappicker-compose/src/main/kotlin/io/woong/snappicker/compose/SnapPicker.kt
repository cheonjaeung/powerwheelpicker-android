package io.woong.snappicker.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.chrisbanes.snapper.ExperimentalSnapperApi
import dev.chrisbanes.snapper.LazyListSnapperLayoutInfo
import dev.chrisbanes.snapper.rememberLazyListSnapperLayoutInfo
import dev.chrisbanes.snapper.rememberSnapperFlingBehavior
import kotlin.math.abs

/**
 * The horizontal scrollable picker that allows user to select one item from multiple items.
 *
 * @param state The state object to manage this picker's state.
 * @param modifier The modifier to apply to this composable.
 * @param itemWidth The width size of each item composable's container.
 * @param repeated Whether this picker has repeating list.
 * When `true`, user can scroll continuously over the end of list.
 * The first item will displayed before first and the last will displayed after first.
 * @param decorationBox Composable to add decoration around picker, such as indicator or something.
 * The actual picker will be passed to this lambda's parameter, "innerPicker".
 * You must call `innerPicker` to display picker.
 * If it is not called, the picker never visible.
 * @param itemContent The content composable of the single item.
 */
@ExperimentalSnapPickerApi
@Composable
public fun <T> HorizontalSnapPicker(
    state: SnapPickerState<T>,
    modifier: Modifier = Modifier,
    itemWidth: Dp = 48.dp,
    repeated: Boolean = false,
    decorationBox: @Composable BoxScope.(innerPicker: @Composable () -> Unit) -> Unit =
        @Composable { innerPicker -> innerPicker() },
    itemContent: @Composable BoxScope.(value: T) -> Unit
) {
    CoreSnapPicker(
        state = state,
        isVertical = false,
        itemSize = DpSize(width = itemWidth, height = 0.dp),
        repeated = repeated,
        modifier = modifier,
        decorationBox = decorationBox,
        itemContent = itemContent
    )
}

/**
 * The vertical scrollable picker that allows user to select one item from multiple items.
 *
 * @param state The state object to manage this picker's state.
 * @param modifier The modifier to apply to this composable.
 * @param itemHeight The height size of each item composable's container.
 * @param repeated Whether this picker has repeating list.
 * When `true`, user can scroll continuously over the end of list.
 * The first item will displayed before first and the last will displayed after first.
 * @param decorationBox Composable to add decoration around picker, such as indicator or something.
 * The actual picker will be passed to this lambda's parameter, "innerPicker".
 * You must call `innerPicker` to display picker.
 * If it is not called, the picker never visible.
 * @param itemContent The content composable of the single item.
 */
@ExperimentalSnapPickerApi
@Composable
public fun <T> VerticalSnapPicker(
    state: SnapPickerState<T>,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 48.dp,
    repeated: Boolean = false,
    decorationBox: @Composable BoxScope.(innerPicker: @Composable () -> Unit) -> Unit =
        @Composable { innerPicker -> innerPicker() },
    itemContent: @Composable BoxScope.(value: T) -> Unit
) {
    CoreSnapPicker(
        state = state,
        isVertical = true,
        itemSize = DpSize(width = 0.dp, height = itemHeight),
        repeated = repeated,
        modifier = modifier,
        decorationBox = decorationBox,
        itemContent = itemContent
    )
}

@OptIn(ExperimentalSnapperApi::class)
@ExperimentalSnapPickerApi
@Composable
private fun <T> CoreSnapPicker(
    state: SnapPickerState<T>,
    isVertical: Boolean,
    itemSize: DpSize,
    repeated: Boolean,
    modifier: Modifier,
    decorationBox: @Composable BoxScope.(innerPicker: @Composable () -> Unit) -> Unit,
    itemContent: @Composable BoxScope.(value: T) -> Unit
) {
    val lazyListState = state.lazyListState
    val snapperLayoutInfo = rememberLazyListSnapperLayoutInfo(lazyListState)
    LaunchedEffect(Unit) {
        state.animateScrollToItem(
            if (repeated) {
                calculateRepeatedLazyListMidIndex(
                    index = state.initialIndex,
                    valuesCount = state.values.size
                )
            } else {
                state.initialIndex
            }
        )
    }
    if (repeated) {
        LaunchedEffect(state.isScrollInProgress) {
            if (!state.isScrollInProgress) {
                state.scrollToItem(calculateRepeatedLazyListMidIndex(
                    index = state.index,
                    valuesCount = state.values.size
                ))
            }
        }
    }
    BoxWithConstraints(modifier) {
        decorationBox {
            if (isVertical) {
                LazyColumn(
                    modifier = Modifier.size(maxWidth, maxHeight),
                    state = lazyListState,
                    contentPadding = PaddingValues(vertical = (maxHeight / 2) - (itemSize.height / 2)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    flingBehavior = rememberSnapperFlingBehavior(lazyListState)
                ) {
                    val itemBoxModifier = Modifier
                        .fillMaxWidth()
                        .height(itemSize.height)
                    if (repeated) {
                        items(count = Int.MAX_VALUE) { index ->
                            Box(
                                modifier = itemBoxModifier.pickerAlpha(
                                    isVertical = false,
                                    index = index,
                                    lazyListState = lazyListState,
                                    snapperLayoutInfo = snapperLayoutInfo
                                ),
                                content = { itemContent(state.values[index % state.values.size]) }
                            )
                        }
                    } else {
                        items(count = state.values.size) { index ->
                            Box(
                                modifier = itemBoxModifier.pickerAlpha(
                                    isVertical = false,
                                    index = index,
                                    lazyListState = lazyListState,
                                    snapperLayoutInfo = snapperLayoutInfo
                                ),
                                content = { itemContent(state.values[index]) }
                            )
                        }
                    }
                }
            } else {
                LazyRow(
                    modifier = Modifier.size(maxWidth, maxHeight),
                    state = lazyListState,
                    contentPadding = PaddingValues(horizontal = (maxWidth / 2) - (itemSize.width) / 2),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    flingBehavior = rememberSnapperFlingBehavior(lazyListState)
                ) {
                    val itemBoxModifier = Modifier
                        .width(itemSize.width)
                        .fillMaxHeight()
                    if (repeated) {
                        items(count = Int.MAX_VALUE) { index ->
                            Box(
                                modifier = itemBoxModifier.pickerAlpha(
                                    isVertical = false,
                                    index = index,
                                    lazyListState = lazyListState,
                                    snapperLayoutInfo = snapperLayoutInfo
                                ),
                                content = { itemContent(state.values[index % state.values.size]) }
                            )
                        }
                    } else {
                        items(count = state.values.size) { index ->
                            Box(
                                modifier = itemBoxModifier.pickerAlpha(
                                    isVertical = false,
                                    index = index,
                                    lazyListState = lazyListState,
                                    snapperLayoutInfo = snapperLayoutInfo
                                ),
                                content = { itemContent(state.values[index]) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun calculateRepeatedLazyListMidIndex(index: Int, valuesCount: Int): Int {
    // TODO improve calculating logics
    return valuesCount * 1000 + index
}

@OptIn(ExperimentalSnapperApi::class)
@Stable
private fun Modifier.pickerAlpha(
    isVertical: Boolean,
    index: Int,
    lazyListState: LazyListState,
    snapperLayoutInfo: LazyListSnapperLayoutInfo
): Modifier {
    return this.composed(
        inspectorInfo = {
            debugInspectorInfo {
                name = "pickerAlpha"
                properties["lazyListState"] = lazyListState
                properties["snapperLayoutInfo"] = snapperLayoutInfo
                properties["index"] = index
                properties["isVertical"] = isVertical
            }
        },
        factory = {
            val layoutInfo = remember { lazyListState.layoutInfo }
            val visibleItemCount = layoutInfo.visibleItemsInfo.size
            val viewPortSize: Float
            val singleItemSize: Float
            if (isVertical) {
                viewPortSize = layoutInfo.viewportSize.height.toFloat()
                singleItemSize = viewPortSize / visibleItemCount
            } else {
                viewPortSize = layoutInfo.viewportSize.width.toFloat()
                singleItemSize = viewPortSize / visibleItemCount
            }
            val absoluteDistanceToIndexSnap = abs(snapperLayoutInfo.distanceToIndexSnap(index))
            Modifier.alpha(
                alpha = if (absoluteDistanceToIndexSnap < singleItemSize) {
                    1f - (absoluteDistanceToIndexSnap / singleItemSize) + 0.66f
                } else {
                    (0.66f - (absoluteDistanceToIndexSnap / viewPortSize)).coerceIn(0.1f, 0.66f)
                }
            )
        }
    )
}
