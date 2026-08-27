package dev.ayaan.tvremote.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ayaan.tvremote.proto.RemoteKeyCode
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The signature control: one ring, not five buttons.
 *
 * The whole disc is live -- a tap resolves to a direction by the angle from the
 * centre, and the inner core is select. It maps to how a thumb actually moves
 * across a physical remote, and it makes the target far bigger than four
 * separate arrow keys ever are on glass. The chevrons only mark where those
 * zones are; they are not themselves the hit areas.
 */
@Composable
fun DPad(
    modifier: Modifier = Modifier,
    diameter: Dp = 258.dp,
    onKey: (RemoteKeyCode) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf<RemoteKeyCode?>(null) }
    val corePress by animateFloatAsState(
        targetValue = if (pressed == RemoteKeyCode.KEYCODE_DPAD_CENTER) 0.95f else 1f,
        label = "core"
    )

    Box(
        modifier = modifier
            .size(diameter)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        val key = resolve(offset, size.width.toFloat(), size.height.toFloat())
                        if (key != null) {
                            pressed = key
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onKey(key)
                        }
                        tryAwaitRelease()
                        pressed = null
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(diameter)) {
            val r = size.minDimension / 2f

            // The ring reads as a machined disc lit from above: the highlight sits
            // high (32% down), not dead centre, and it is the only lit thing here.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                    center = center,
                    radius = r * 1.32f
                ),
                radius = r * 1.32f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    0.00f to Color(0xFF2A2A2A),
                    0.46f to Color(0xFF171717),
                    0.78f to Color(0xFF0A0A0A),
                    1.00f to Color(0xFF000000),
                    center = Offset(center.x, size.height * 0.32f),
                    radius = r * 1.22f
                ),
                radius = r
            )
            drawCircle(color = Deck.Ring, radius = r, style = Stroke(width = 1.dp.toPx()))
        }

        Chevron(DeckIcon.Up, Alignment.TopCenter, pressed == RemoteKeyCode.KEYCODE_DPAD_UP)
        Chevron(DeckIcon.Down, Alignment.BottomCenter, pressed == RemoteKeyCode.KEYCODE_DPAD_DOWN)
        Chevron(DeckIcon.Left, Alignment.CenterStart, pressed == RemoteKeyCode.KEYCODE_DPAD_LEFT)
        Chevron(DeckIcon.Right, Alignment.CenterEnd, pressed == RemoteKeyCode.KEYCODE_DPAD_RIGHT)

        Box(
            modifier = Modifier.scale(corePress),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(diameter * CORE)) {
                val r = size.minDimension / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.33f), Color.Transparent),
                        center = center,
                        radius = r * 1.7f
                    ),
                    radius = r * 1.7f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        0.00f to Color(0xFFFFFFFF),
                        0.58f to Color(0xFFF2F2F2),
                        1.00f to Color(0xFFC9C9C9),
                        center = Offset(center.x, size.height * 0.12f),
                        radius = r * 1.1f
                    ),
                    radius = r
                )
            }
            Text(
                "OK",
                color = Deck.Base,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                fontStyle = FontStyle.Italic,
                letterSpacing = 3.2.sp
            )
        }
    }
}

/** 104dp core on a 258dp ring, as drawn. */
private const val CORE = 0.40f

@Composable
private fun BoxScope.Chevron(
    icon: ImageVector,
    where: Alignment,
    lit: Boolean
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (lit) Deck.Bright else Deck.Arrow,
        modifier = Modifier
            .align(where)
            .padding(16.dp)
            .size(20.dp)
    )
}

/** Angle from centre picks the direction; the inner disc is select. */
private fun resolve(offset: Offset, width: Float, height: Float): RemoteKeyCode? {
    val cx = width / 2f
    val cy = height / 2f
    val dx = offset.x - cx
    val dy = offset.y - cy
    val radius = hypot(dx, dy)

    if (radius > cx) return null                                  // outside the ring
    if (radius < cx * CORE) return RemoteKeyCode.KEYCODE_DPAD_CENTER

    val degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
    return when {
        degrees >= -45 && degrees < 45 -> RemoteKeyCode.KEYCODE_DPAD_RIGHT
        degrees >= 45 && degrees < 135 -> RemoteKeyCode.KEYCODE_DPAD_DOWN
        degrees >= -135 && degrees < -45 -> RemoteKeyCode.KEYCODE_DPAD_UP
        else -> RemoteKeyCode.KEYCODE_DPAD_LEFT
    }
}
