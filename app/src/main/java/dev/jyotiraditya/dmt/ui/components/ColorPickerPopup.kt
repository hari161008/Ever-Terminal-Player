package dev.jyotiraditya.dmt.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import dev.jyotiraditya.dmt.ui.theme.TuiDim
import dev.jyotiraditya.dmt.ui.theme.TuiFaint
import dev.jyotiraditya.dmt.ui.theme.TuiFg
import dev.jyotiraditya.dmt.ui.theme.TuiLine
import dev.jyotiraditya.dmt.ui.theme.TuiSurface

/**
 * A small floating swatch picker, anchored just under [anchorBounds] (in
 * window coordinates, e.g. from `Modifier.onGloballyPositioned {
 * it.boundsInWindow() }` on the row that opened it), right-aligned to the
 * anchor's right edge with a triangular pointer aimed back at it — flat,
 * square-edged and border-only, matching the rest of the app's terminal
 * look rather than Material's default elevated/rounded popup style.
 *
 * [defaultIndex] renders as its own swatch with a hollow "reset" mark
 * instead of a fill, so reverting to the app's default accent is always one
 * tap away regardless of which color is currently picked.
 */
@Composable
fun TuiColorPickerPopup(
    anchorBounds: IntRect,
    label: String,
    colors: List<Pair<String, Color>>,
    selectedIndex: Int,
    defaultIndex: Int = 0,
    onSelect: (Int) -> Unit,
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
                    .background(TuiSurface),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TuiDim,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 2.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    colors.forEachIndexed { index, (_, color) ->
                        ColorSwatch(
                            color = color,
                            selected = index == selectedIndex,
                            isDefault = index == defaultIndex,
                            onClick = { onSelect(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    isDefault: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .border(if (selected) 2.dp else 1.dp, if (selected) TuiFg else TuiLine)
                .padding(if (selected) 2.dp else 1.dp)
                .background(color)
                .tuiClickable(onClick),
        )
        if (isDefault) {
            Text(
                text = "def",
                style = MaterialTheme.typography.labelSmall,
                color = TuiFaint,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}
