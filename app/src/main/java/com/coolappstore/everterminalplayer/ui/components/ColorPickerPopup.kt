package com.coolappstore.everterminalplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.coolappstore.everterminalplayer.ui.theme.TuiDim
import com.coolappstore.everterminalplayer.ui.theme.TuiFaint
import com.coolappstore.everterminalplayer.ui.theme.TuiLine
import com.coolappstore.everterminalplayer.ui.theme.TuiSurface
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A floating, freeform HSV color picker, anchored just under [anchorBounds]
 * (in window coordinates, e.g. from `Modifier.onGloballyPositioned {
 * it.boundsInWindow() }` on the row that opened it), right-aligned to the
 * anchor's right edge with a triangular pointer aimed back at it — flat,
 * square-edged and border-only, matching the rest of the app's terminal
 * look rather than Material's default elevated/rounded popup style.
 *
 * A saturation/value square plus a hue strip let the person drag to any
 * color, each with its own draggable indicator; the result streams live to
 * [onColorChange] as they drag. A "reset" action snaps back to
 * [defaultColor].
 */
@Composable
fun TuiColorPickerPopup(
    anchorBounds: IntRect,
    label: String,
    initialColor: Color,
    defaultColor: Color,
    onColorChange: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { 10.dp.roundToPx() }

    val provider = remember(anchorBounds, gapPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBoundsIgnored: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val margin = with(density) { 8.dp.roundToPx() }
                val x = (anchorBounds.right - popupContentSize.width)
                    .coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
                var y = anchorBounds.bottom + gapPx
                if (y + popupContentSize.height > windowSize.height - margin) {
                    // Not enough room below — flip above the anchor instead
                    // of running off the bottom of the screen.
                    y = anchorBounds.top - gapPx - popupContentSize.height
                }
                return IntOffset(x, y.coerceAtLeast(margin))
            }
        }
    }

    val startHsv = remember(initialColor) { colorToHsv(initialColor) }
    var hue by remember { mutableFloatStateOf(startHsv[0]) }
    var sat by remember { mutableFloatStateOf(startHsv[1]) }
    var value by remember { mutableFloatStateOf(startHsv[2]) }
    val currentColor = hsvToColor(hue, sat, value)

    Popup(popupPositionProvider = provider, onDismissRequest = onDismiss) {
        Column(horizontalAlignment = Alignment.End) {
            // Pointer, aimed back toward the anchor's right edge (which the
            // panel below is itself right-aligned to).
            val pointerColor = TuiLine
            Canvas(modifier = Modifier.size(width = 14.dp, height = 7.dp)) {
                val path = Path().apply {
                    moveTo(0f, size.height)
                    lineTo(size.width / 2f, 0f)
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(path, color = pointerColor)
            }
            Column(
                modifier = Modifier
                    .border(1.dp, TuiLine)
                    .background(TuiSurface)
                    .width(224.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = TuiDim,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .border(1.dp, TuiLine)
                                .background(currentColor)
                        )
                        Text(
                            text = " " + hexOf(currentColor),
                            style = MaterialTheme.typography.labelSmall,
                            color = TuiFaint,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                SaturationValueBox(
                    hue = hue,
                    sat = sat,
                    value = value,
                    onChange = { s, v -> sat = s; value = v },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )

                HueStrip(
                    hue = hue,
                    onChange = { h -> hue = h },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                        .tuiClickable {
                            val defHsv = colorToHsv(defaultColor)
                            hue = defHsv[0]
                            sat = defHsv[1]
                            value = defHsv[2]
                        }
                ) {
                    Text(
                        text = "‹ reset ›",
                        style = MaterialTheme.typography.labelSmall,
                        color = TuiFaint,
                    )
                }
            }
        }
    }

    // Streams live to the caller as the person drags either control.
    LaunchedEffect(currentColor) { onColorChange(currentColor) }
}

@Composable
private fun SaturationValueBox(
    hue: Float,
    sat: Float,
    value: Float,
    onChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val pureHue = hsvToColor(hue, 1f, 1f)

    fun updateFromOffset(offset: Offset) {
        if (boxSize.width == 0 || boxSize.height == 0) return
        val s = (offset.x / boxSize.width).coerceIn(0f, 1f)
        val v = (1f - offset.y / boxSize.height).coerceIn(0f, 1f)
        onChange(s, v)
    }

    Box(
        modifier = modifier
            .size(200.dp)
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    updateFromOffset(down.position)
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change != null && change.pressed) {
                            updateFromOffset(change.position)
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = pureHue)
            drawRect(brush = Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
            drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        }
        val indicatorPx = with(density) { 16.dp.roundToPx() }
        val px = (sat * boxSize.width - indicatorPx / 2f).roundToInt()
        val py = ((1f - value) * boxSize.height - indicatorPx / 2f).roundToInt()
        Box(
            modifier = Modifier
                .offset { IntOffset(px, py) }
                .size(16.dp)
                .border(2.dp, Color.White, CircleShape)
                .padding(2.dp)
                .border(1.dp, Color.Black, CircleShape)
        )
    }
}

@Composable
private fun HueStrip(
    hue: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var stripWidth by remember { mutableStateOf(0) }
    val hueColors = remember {
        listOf(
            Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
            Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000),
        )
    }

    fun updateFromOffset(x: Float) {
        if (stripWidth == 0) return
        val h = (x / stripWidth).coerceIn(0f, 1f) * 360f
        onChange(h)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
            .onSizeChanged { stripWidth = it.width }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    updateFromOffset(down.position.x)
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change != null && change.pressed) {
                            updateFromOffset(change.position.x)
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(brush = Brush.horizontalGradient(hueColors))
        }
        val markerPx = with(density) { 4.dp.roundToPx() }
        val px = ((hue / 360f) * stripWidth - markerPx / 2f).roundToInt()
        Box(
            modifier = Modifier
                .offset { IntOffset(px, 0) }
                .width(4.dp)
                .height(20.dp)
                .border(2.dp, Color.White)
                .padding(1.dp)
                .border(1.dp, Color.Black)
        )
    }
}

private fun hexOf(color: Color): String =
    "#%06X".format(color.toArgb() and 0xFFFFFF)

private fun hsvToColor(h: Float, s: Float, v: Float): Color {
    val c = v * s
    val hh = (h / 60f).mod(6f)
    val x = c * (1f - abs(hh.mod(2f) - 1f))
    val (r1, g1, b1) = when {
        hh < 1f -> Triple(c, x, 0f)
        hh < 2f -> Triple(x, c, 0f)
        hh < 3f -> Triple(0f, c, x)
        hh < 4f -> Triple(0f, x, c)
        hh < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = v - c
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
    )
}

private fun colorToHsv(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val h = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta).mod(6f))
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    val s = if (max == 0f) 0f else delta / max
    val v = max
    return floatArrayOf(h, s, v)
}
