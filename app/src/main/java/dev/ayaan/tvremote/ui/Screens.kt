package dev.ayaan.tvremote.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ayaan.tvremote.R
import dev.ayaan.tvremote.discovery.Tv
import dev.ayaan.tvremote.proto.RemoteKeyCode
import dev.ayaan.tvremote.remote.KnownApp
import dev.ayaan.tvremote.remote.Link
import dev.ayaan.tvremote.remote.TextPrompt
import kotlin.math.roundToInt

@Composable
fun DiscoverScreen(
    devices: List<Tv>,
    busy: Boolean,
    onSelect: (Tv) -> Unit,
    onManual: (String, Protocol) -> Unit
) {
    var address by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf(Protocol.ANDROID_TV) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.Base)
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Text("ON THIS NETWORK", style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(8.dp))
        Text("Pick your TV", style = MaterialTheme.typography.headlineSmall, color = Ink.Text)
        Spacer(Modifier.height(20.dp))

        if (devices.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 28.dp)) {
                Text(
                    "Nothing found yet. Wake the TV and make sure this phone is on the same Wi-Fi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.Muted
                )
            }
        }

        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(devices, key = { it.host }) { tv ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Ink.Panel, RoundedCornerShape(14.dp))
                        .border(1.dp, Ink.Hairline, RoundedCornerShape(14.dp))
                        .clickable(enabled = !busy) { onSelect(tv) }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(8.dp).background(Ink.Signal, CircleShape))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(tv.name, style = MaterialTheme.typography.titleMedium, color = Ink.Text)
                        Text(tv.host, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("OR TYPE AN ADDRESS", style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProtocolChip("Android TV", protocol == Protocol.ANDROID_TV) { protocol = Protocol.ANDROID_TV }
            ProtocolChip("Fire TV", protocol == Protocol.FIRE_TV) { protocol = Protocol.FIRE_TV }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                placeholder = { Text("192.168.1.42", color = Ink.Muted) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = { if (address.isNotBlank()) onManual(address.trim(), protocol) },
                enabled = address.isNotBlank() && !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Ink.Signal, contentColor = Ink.Base)
            ) { Text("Connect") }
        }
        if (protocol == Protocol.FIRE_TV) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Uses ADB debugging. Turn it on in Settings → My Fire TV → Developer " +
                    "Options, then accept the prompt using the Fire TV's own remote the first time.",
                style = MaterialTheme.typography.bodySmall,
                color = Ink.Muted
            )
        }
    }
}

@Composable
private fun ProtocolChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (selected) Ink.Signal else Ink.Panel, RoundedCornerShape(16.dp))
            .border(1.dp, if (selected) Ink.Signal else Ink.Hairline, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Ink.Base else Ink.Text
        )
    }
}

@Composable
fun CodeScreen(
    busy: Boolean,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    var code by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.Base)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("PAIRING", style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(10.dp))
        Text("Enter the code on your TV", style = MaterialTheme.typography.headlineSmall, color = Ink.Text)
        Spacer(Modifier.height(10.dp))
        Text(
            "Six characters, letters and numbers. The TV keeps this phone trusted afterwards.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.Muted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { if (it.length <= 6) code = it.uppercase() },
            singleLine = true,
            placeholder = { Text("A1B2C3", color = Ink.Muted) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { onSubmit(code) },
            enabled = code.length == 6 && !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink.Signal, contentColor = Ink.Base)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Ink.Base, strokeWidth = 2.dp)
            else Text("Pair")
        }
        TextButton(onClick = onCancel) { Text("Cancel", color = Ink.Muted) }
    }
}

/**
 * The remote itself. Four launchers, the ring, transport keys, and a volume
 * slab you drag rather than tap -- the design's premise is that the two things
 * a thumb reaches for blind (direction and volume) get the two biggest targets
 * on the slab, and everything else shrinks to a pill.
 */
@Composable
fun ControlScreen(
    name: String,
    link: Link,
    textPrompt: TextPrompt?,
    listening: Boolean,
    onKey: (RemoteKeyCode) -> Unit,
    onSendText: (String) -> Unit,
    onDismissTextPrompt: () -> Unit,
    onMicPressed: () -> Unit,
    onMicReleased: () -> Unit,
    onLaunchApp: (KnownApp) -> Unit,
    onBack: () -> Unit,
    onForget: () -> Unit
) {
    var showTextEntry by remember { mutableStateOf(false) }
    var textEntryValue by remember { mutableStateOf("") }

    LaunchedEffect(textPrompt) {
        if (textPrompt != null) {
            textEntryValue = textPrompt.value
            showTextEntry = true
        }
    }

    Box(Modifier.fillMaxSize().background(Deck.Base)) {
        GridBackdrop(
            Modifier
                .fillMaxWidth()
                .height(360.dp)
                .offset(y = 51.dp)
                .align(Alignment.TopCenter)
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 30.dp)
        ) {
            Header(
                name = name,
                link = link,
                onBack = onBack,
                onPower = { onKey(RemoteKeyCode.KEYCODE_POWER) }
            )

            AppRow(onLaunchApp)

            Box(
                Modifier
                    .padding(top = 26.dp, bottom = 22.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Deck.Divider)
            )

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DPad(onKey = onKey)
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Pill(DeckIcon.Back, "Back", Modifier.weight(1f)) { onKey(RemoteKeyCode.KEYCODE_BACK) }
                Pill(DeckIcon.Home, "Home", Modifier.weight(1f)) { onKey(RemoteKeyCode.KEYCODE_HOME) }
                Pill(DeckIcon.Guide, "Guide", Modifier.weight(1f)) { onKey(RemoteKeyCode.KEYCODE_MENU) }
                Pill(DeckIcon.PlayPause, "Play / Pause", Modifier.weight(1f)) {
                    onKey(RemoteKeyCode.KEYCODE_MEDIA_PLAY_PAUSE)
                }
            }

            // Not in the design, which shows four pills. Kept as a shorter second
            // rank so the keys the design drops still have somewhere to live.
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Pill(DeckIcon.Input, "Input", Modifier.weight(1f), height = 44.dp, iconSize = 17.dp) {
                    onKey(RemoteKeyCode.KEYCODE_TV_INPUT)
                }
                Pill(DeckIcon.Search, "Search", Modifier.weight(1f), height = 44.dp, iconSize = 17.dp) {
                    onKey(RemoteKeyCode.KEYCODE_SEARCH)
                }
                Pill(DeckIcon.Rewind, "Rewind", Modifier.weight(1f), height = 44.dp, iconSize = 17.dp) {
                    onKey(RemoteKeyCode.KEYCODE_MEDIA_REWIND)
                }
                Pill(DeckIcon.Forward, "Fast forward", Modifier.weight(1f), height = 44.dp, iconSize = 17.dp) {
                    onKey(RemoteKeyCode.KEYCODE_MEDIA_FAST_FORWARD)
                }
            }

            VolumeDeck(
                link = link,
                onKey = onKey,
                modifier = Modifier.padding(top = 12.dp)
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MicBar(
                    modifier = Modifier.weight(1f),
                    listening = listening,
                    onPressed = onMicPressed,
                    onReleased = onMicReleased
                )
                Pill(
                    icon = DeckIcon.Keyboard,
                    label = "Keyboard",
                    modifier = Modifier.width(58.dp),
                    height = 56.dp,
                    iconSize = 20.dp
                ) {
                    textEntryValue = ""
                    showTextEntry = true
                }
            }

            StatusBar(link, Modifier.padding(top = 18.dp))

            TextButton(onClick = onForget, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(
                    "Forget this TV",
                    color = Deck.Faint,
                    fontSize = 11.sp,
                    letterSpacing = TrackWide
                )
            }
        }
    }

    if (showTextEntry) {
        TextEntryDialog(
            value = textEntryValue,
            onValueChange = { textEntryValue = it },
            onSend = {
                if (textEntryValue.isNotBlank()) onSendText(textEntryValue)
                showTextEntry = false
                onDismissTextPrompt()
            },
            onDismiss = {
                showTextEntry = false
                onDismissTextPrompt()
            }
        )
    }
}

@Composable
private fun Header(
    name: String,
    link: Link,
    onBack: () -> Unit,
    onPower: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val powerDown by interaction.collectIsPressedAsState()

    Row(
        Modifier.fillMaxWidth().padding(top = 34.dp, bottom = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = DeckIcon.Left,
            contentDescription = "Back to device list",
            tint = Deck.Muted,
            modifier = Modifier
                .size(22.dp)
                .clickable { onBack() }
        )
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(if (link is Link.Ready) Deck.Bright else Deck.Faint, CircleShape)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    statusLine(link),
                    color = Deck.Muted,
                    fontSize = 11.sp,
                    letterSpacing = TrackWide
                )
            }
            Text(
                name,
                color = Deck.Bright,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = TrackTight
            )
        }

        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (powerDown) Deck.Bright else Deck.Panel)
                .border(1.dp, if (powerDown) Deck.Bright else Deck.Hairline, CircleShape)
                .clickable(interactionSource = interaction, indication = null) { onPower() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = DeckIcon.Power,
                contentDescription = "Power",
                tint = if (powerDown) Deck.Base else Deck.Bright,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun AppRow(onLaunchApp: (KnownApp) -> Unit) {
    // Insets differ per logo: each raster carries its own margin, and the tile
    // only looks even once that is cancelled out.
    val tiles = listOf(
        Triple(KnownApp.YOUTUBE, R.drawable.ic_youtube, 10),
        Triple(KnownApp.HOTSTAR, R.drawable.ic_hotstar, 6),
        Triple(KnownApp.PRIME_VIDEO, R.drawable.ic_prime, 10),
        Triple(KnownApp.NETFLIX, R.drawable.ic_netflix, 12)
    )

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.forEach { (app, logo, inset) ->
            AppTile(
                logo = logo,
                label = app.label,
                inset = inset.dp,
                modifier = Modifier.weight(1f)
            ) { onLaunchApp(app) }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        tiles.forEach { (app, _, _) ->
            Text(
                app.label,
                color = Deck.Muted,
                fontSize = 10.sp,
                letterSpacing = 0.4.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Volume as a slab you drag, not a pair of keys you tap.
 *
 * The TV owns the real number, so a drag only sends the same up/down presses a
 * physical remote would; the readout moves optimistically so the gesture feels
 * attached, then snaps to whatever the TV reports back. Sets that never report
 * (Fire TV over ADB) keep the optimistic value, which is the best guess
 * available.
 */
@Composable
private fun VolumeDeck(
    link: Link,
    onKey: (RemoteKeyCode) -> Unit,
    modifier: Modifier = Modifier
) {
    val reported = (link as? Link.Ready)?.volume
    val ceiling = reported?.max?.takeIf { it > 0 } ?: 100

    var optimistic by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(reported?.level, reported?.muted) { optimistic = null }

    val level = optimistic ?: reported?.level
    val muted = reported?.muted == true
    val muteInteraction = remember { MutableInteractionSource() }

    // The drag handler outlives the composition that started it, so anything it
    // reads has to come through a state holder or it goes stale mid-gesture.
    val current by rememberUpdatedState(level)
    val press by rememberUpdatedState(onKey)

    fun bump(direction: Int) {
        press(if (direction > 0) RemoteKeyCode.KEYCODE_VOLUME_UP else RemoteKeyCode.KEYCODE_VOLUME_DOWN)
        optimistic = ((current ?: 0) + direction).coerceIn(0, ceiling)
    }

    Row(
        modifier.fillMaxWidth().height(152.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .background(Deck.Panel)
                .border(1.dp, Deck.Hairline, RoundedCornerShape(16.dp))
                .pointerInput(ceiling) {
                    val step = 9.dp.toPx()
                    var carry = 0f
                    detectVerticalDragGestures(
                        onDragEnd = { carry = 0f },
                        onDragCancel = { carry = 0f }
                    ) { change, dy ->
                        change.consume()
                        carry -= dy                       // up is louder
                        while (carry >= step) {
                            carry -= step
                            bump(1)
                        }
                        while (carry <= -step) {
                            carry += step
                            bump(-1)
                        }
                    }
                }
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text("VOLUME", color = Deck.Muted, fontSize = 10.sp, letterSpacing = TrackWide)
                Text(
                    text = if (muted || level == null) "--" else level.toString().padStart(2, '0'),
                    color = Deck.Volume,
                    fontSize = 56.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-0.5).sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = DeckIcon.DragHint,
                        contentDescription = null,
                        tint = Deck.Faint,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "DRAG TO SET",
                        color = Deck.Faint,
                        fontSize = 9.5.sp,
                        letterSpacing = 1.5.sp
                    )
                }
            }

            Ladder(
                level = level,
                ceiling = ceiling,
                muted = muted,
                modifier = Modifier.width(30.dp).fillMaxHeight()
            )
        }

        val muteDown by muteInteraction.collectIsPressedAsState()
        Box(
            Modifier
                .width(58.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .background(if (muted || muteDown) Deck.Bright else Deck.Panel)
                .border(1.dp, Deck.Hairline, RoundedCornerShape(16.dp))
                .clickable(interactionSource = muteInteraction, indication = null) {
                    onKey(RemoteKeyCode.KEYCODE_VOLUME_MUTE)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = DeckIcon.Mute,
                contentDescription = "Mute",
                tint = if (muted || muteDown) Deck.Base else Deck.Dim,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Eighteen segments, filling from the bottom, brightening over the last three. */
@Composable
private fun Ladder(
    level: Int?,
    ceiling: Int,
    muted: Boolean,
    modifier: Modifier = Modifier
) {
    val lit = when {
        muted || level == null -> 0
        else -> (level.toFloat() / ceiling * SEGMENTS).roundToInt().coerceIn(0, SEGMENTS)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (fromTop in 0 until SEGMENTS) {
            val index = SEGMENTS - 1 - fromTop
            val colour: Color = when {
                index >= lit -> Deck.Unlit
                index > SEGMENTS - 4 -> Deck.VolumeTop
                else -> Deck.Volume
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(colour, RoundedCornerShape(2.dp))
            )
        }
    }
}

private const val SEGMENTS = 18

/** Hold to talk. White, because it is the only key that starts a conversation. */
@Composable
private fun MicBar(
    modifier: Modifier = Modifier,
    listening: Boolean,
    onPressed: () -> Unit,
    onReleased: () -> Unit
) {
    var held by remember { mutableStateOf(false) }

    Row(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (held || listening) Deck.BrightDown else Deck.Bright)
            // Not detectTapGestures: this key sits inside the screen's vertical
            // scroll, and that scroll wins the slop competition the moment a
            // held thumb drifts a few pixels -- which cancels tryAwaitRelease
            // and releases the mic mid-sentence. Claiming the pointer on the
            // initial pass keeps the whole hold here, where it belongs.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    ).consume()
                    held = true
                    onPressed()
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        held = false
                        onReleased()
                    }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = DeckIcon.Mic,
            contentDescription = "Hold to speak",
            tint = Deck.Base,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(9.dp))
        Image(
            painter = painterResource(R.drawable.ic_google_wordmark),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(22.dp)
        )
    }
}

/**
 * The design's toast slot. Connected is the quiet state, so it stays grey;
 * anything else is worth a white pill.
 */
@Composable
private fun StatusBar(link: Link, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().heightIn(min = 34.dp),
        contentAlignment = Alignment.Center
    ) {
        if (link is Link.Ready) {
            Text("READY", color = Deck.Faint, fontSize = 11.5.sp, letterSpacing = TrackWide)
        } else {
            Text(
                text = statusLine(link),
                color = Deck.Base,
                fontSize = 11.5.sp,
                letterSpacing = TrackWide,
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(Deck.Bright)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun TextEntryDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Type on the TV") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = onSend) { Text("Send") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun statusLine(link: Link): String = when (link) {
    is Link.Ready -> "CONNECTED"
    Link.Connecting -> "CONNECTING"
    Link.Authorizing -> "CHECK YOUR FIRE TV"
    is Link.Failed -> link.reason.uppercase()
    Link.Idle -> "NOT CONNECTED"
}
