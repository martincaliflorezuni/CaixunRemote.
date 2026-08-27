package dev.ayaan.tvremote.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The remote deck: a black slab with white hardware on it.
 *
 * Kept apart from [Ink] on purpose. Ink is the setup palette -- discovery and
 * pairing, where a warm signal colour reads as "waiting". Once a TV is
 * connected the app becomes an object in a dark room, so the deck drops to true
 * black with white keys and a single blue reserved for volume, the only value
 * on screen that is a number rather than a control.
 */
object Deck {
    val Base = Color(0xFF000000)
    val Panel = Color(0xFF0A0A0A)
    val Hairline = Color(0xFF2E2E2E)
    val Divider = Color(0xFF262626)
    val Ring = Color(0xFF444444)
    val Bright = Color(0xFFFFFFFF)
    val BrightDown = Color(0xFFD9D9D9)
    val Dim = Color(0xFFC9C9C9)
    val Arrow = Color(0xFF7A7A7A)
    val Muted = Color(0xFF8A8A8A)
    val Faint = Color(0xFF5A5A5A)
    val Volume = Color(0xFF2E9BFF)
    val VolumeTop = Color(0xFF7CC4FF)
    val Unlit = Color(0xFF1A1A1A)
}

/** Tracking values off the design, in sp at the size each label is used. */
val TrackWide = 2.0.sp
val TrackTight = (-0.4).sp

private fun strokeIcon(vararg d: String, width: Float = 2f): ImageVector =
    ImageVector.Builder(
        name = "deck",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        d.forEach { path ->
            addPath(
                pathData = PathParser().parsePathString(path).toNodes(),
                stroke = SolidColor(Color.White),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()

/**
 * The design draws its own icon set rather than pulling one in, so these are
 * the same path strings it uses, parsed once at class-load.
 */
object DeckIcon {
    val Power = strokeIcon("M12 3v9", "M18.4 6.6a9 9 0 1 1-12.8 0")
    val Back = strokeIcon("M9 14 4 9l5-5", "M4 9h10a6 6 0 0 1 0 12h-3", width = 1.9f)
    val Home = strokeIcon("M3 10.5 12 3l9 7.5", "M6 10v10h12V10", width = 1.9f)
    val Guide = strokeIcon("M4 6h16", "M4 12h16", "M4 18h10", width = 1.9f)
    val PlayPause = strokeIcon("M9 5v14", "M15 5v14", width = 1.9f)
    val Mute = strokeIcon("M11 5 6 9H3v6h3l5 4V5z", "m17 9 4 6", "m21 9-4 6")
    val Mic = strokeIcon(
        "M12 2a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0V5a3 3 0 0 1 3-3z",
        "M5 11a7 7 0 0 0 14 0",
        "M12 18v4"
    )
    val Keyboard = strokeIcon(
        "M4 6h16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z",
        "M6 10h.01M10 10h.01M14 10h.01M18 10h.01M8 14h8",
        width = 1.8f
    )
    val DragHint = strokeIcon("M12 4v16", "M7 9l5-5 5 5", "M7 15l5 5 5-5", width = 2.2f)
    val Up = strokeIcon("M6 14l6-6 6 6", width = 2.2f)
    val Down = strokeIcon("M6 10l6 6 6-6", width = 2.2f)
    val Left = strokeIcon("M14 6l-6 6 6 6", width = 2.2f)
    val Right = strokeIcon("M10 6l6 6-6 6", width = 2.2f)
    val Search = strokeIcon("M11 4a7 7 0 1 1 0 14 7 7 0 0 1 0-14z", "m16 16 4 4", width = 1.9f)
    val Input = strokeIcon("M3 5h18v11H3z", "M8 20h8", width = 1.9f)
    val Rewind = strokeIcon("M11 6 4 12l7 6z", "M20 6l-7 6 7 6z", width = 1.9f)
    val Forward = strokeIcon("M4 6l7 6-7 6z", "M13 6l7 6-7 6z", width = 1.9f)
}

/**
 * The faint graph paper behind the header, masked so it fades out downward --
 * it must never compete with the D-pad, the one thing on screen meant to look
 * lit.
 */
@Composable
fun GridBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val cell = 26.dp.toPx()
        val hair = 1.dp.toPx()
        val ink = Color.White.copy(alpha = 0.04f)

        var x = 0f
        while (x <= size.width) {
            drawLine(ink, Offset(x, 0f), Offset(x, size.height), hair)
            x += cell
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(ink, Offset(0f, y), Offset(size.width, y), hair)
            y += cell
        }

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Black, Color.Transparent),
                center = Offset(size.width / 2f, 0f),
                radius = size.width * 0.8f
            ),
            blendMode = BlendMode.DstIn
        )
    }
}

/** A white app tile: square, logo edge to edge, dips on press. */
@Composable
fun AppTile(
    logo: Int,
    label: String,
    inset: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squeeze by animateFloatAsState(if (pressed) 0.94f else 1f, label = "tile")

    Box(
        modifier
            .aspectRatio(1f)
            .scale(squeeze)
            .clip(RoundedCornerShape(18.dp))
            .background(Deck.Bright)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(logo),
            contentDescription = label,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(inset)
        )
    }
}

/**
 * A pill key. Dark at rest, inverting to solid white while held -- the only
 * press feedback the design gives a key, so it has to land immediately.
 */
@Composable
fun Pill(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
    iconSize: Dp = 19.dp,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier
            .height(height)
            .clip(shape)
            .background(if (pressed) Deck.Bright else Deck.Panel)
            .border(1.dp, if (pressed) Deck.Bright else Deck.Hairline, shape)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (pressed) Deck.Base else Deck.Dim,
            modifier = Modifier.size(iconSize)
        )
    }
}
