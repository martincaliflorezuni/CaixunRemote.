# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

An Android phone app (Kotlin + Jetpack Compose) that controls a TV over Wi-Fi, speaking two
unrelated protocols behind one UI. `README.md` covers what the app does and how a user drives it;
`SETUP.md` covers the version matrix and Gradle download failures. Read those for product and
setup detail — this file covers what you can only learn by reading several source files at once.

## Build

**The JDK version is a hard gate, not a preference.** Gradle 8.9 + AGP 8.6.1 will not run on
anything newer than JDK 22. A machine whose default `java` is 25 or 26 (including Android Studio's
bundled JBR 25) fails during Kotlin DSL script compilation with a bare
`IllegalArgumentException: 26.0.2.1` — an unparseable-version error out of `JavaVersion.parse`,
nothing to do with your code. Point Gradle at a JDK 17:

```bash
./gradlew :app:assembleDebug -Dorg.gradle.java.home="<path to a JDK 17>"
./gradlew :app:installDebug  -Dorg.gradle.java.home="<path to a JDK 17>"
```

`gradle.properties` is deliberately not pinned to a JDK path, since it is committed and any such
path is machine-local. Android Studio uses its own JDK setting and is unaffected.

There is no test source set, no lint configuration, and no release signing config — debug is the
only installable build. A cold `assembleDebug` takes ~3 minutes, mostly protoc.

## Architecture

### The `Session` interface is the seam

`remote/RemoteClient.kt` (Android TV, protobuf over TLS on 6466) and `remote/FireTvClient.kt`
(Fire TV, `dadb` shell commands over ADB on 5555) share nothing but the `Session` interface
declared in `RemoteClient.kt`. Everything above that seam — `RemoteViewModel`, every screen —
is protocol-agnostic and must stay that way. When adding a control, add it to `Session` and
implement it on both sides; where a capability genuinely has no ADB equivalent, the Fire TV
implementation degrades explicitly rather than silently:

- `FireTvClient.textPrompt` is a `StateFlow` that never emits — ADB has no focus-change push.
- `FireTvClient.press` ignores `RemoteDirection` entirely — `input keyevent` has no long-press form.
- Voice is `RemoteClient`-only. `RemoteViewModel.onMicPressed` does `session as? RemoteClient` and
  reports an error on null; `voiceSupported()` exists for the same check.

`KnownApp` (`remote/KnownApp.kt`) carries **both** a deep link and a package name for the same
reason — the two adapters launch apps by entirely different means.

### One state owner

`ui/RemoteViewModel.kt` holds a single `MutableStateFlow<UiState>` describing the whole screen.
Sessions expose their own `StateFlow`s (`state`, `textPrompt`); the ViewModel collects them in
`viewModelScope` and folds them into `UiState`. Composables receive plain values and lambdas — no
screen touches a `Session` directly. `UiState.error` is the single snackbar channel, drained by a
`LaunchedEffect` in `MainActivity` that calls `dismissError()`; `report()` exists so UI-layer code
(e.g. permission callbacks) can use that same channel.

`Step` (Discover → EnterCode → Control) is the only navigation — no nav library, just a `when` on
`state.step` in `MainActivity`.

### Connection flow

`select()` and `connectManually()` both persist a per-host protocol choice to `SharedPreferences`
before connecting, because the reconnect path on next launch (`lastTv()` in `init`) has no other
way to know which client to build. Android TV additionally stores a pinned certificate per host;
its presence is what decides pair-vs-reconnect. `forget()` clears cert, ADB key and protocol
together — leaving any one behind produces a half-paired state that fails confusingly.

### Protobuf

`app/src/main/proto/` is compiled at build time into `dev.ayaan.tvremote.proto` (javalite). The
`.proto` files are a hand-written partial description of a closed protocol — field numbers are
load-bearing and match the reverse-engineered Android TV Remote v2 wire format. `RemoteKeyCode`
values are identical to AOSP `KeyEvent` constants, so new keys can be added by number. Generated
sources under `app/build/generated/` are not checked in.

Two undocumented protocol details are easy to break:

- `beginVoice()` presses `KEYCODE_SEARCH`, waits for the TV to return a session id in
  `remote_voice_begin`, then **echoes that message back**. The TV will not accept audio without
  the echo.
- `sendText()` must echo the latest `imeCounter`/`fieldCounter` the TV pushed in
  `remote_ime_batch_edit`, or the edit is silently ignored.

### UI layer: two palettes, on purpose

`ui/Theme.kt` defines `Ink` (dark navy, warm `Signal` accent) used by the discovery and pairing
screens. `ui/Deck.kt` defines `Deck` (pure black, white keys) used by the remote itself, along
with `DeckIcon`, a set of stroke-path `ImageVector`s built by `strokeIcon`. Both appear in
`Screens.kt`; pick by which screen you are in, and don't mix them within one screen.

### Gesture handling on the remote deck

`ControlScreen`'s content sits in a `Column` with `.verticalScroll(...)`. Any press-and-hold
control inside it competes with that scroll: past touch slop the scroll wins and cancels the
gesture. `MicBar` therefore does **not** use `detectTapGestures`/`tryAwaitRelease` — it uses
`awaitEachGesture` and consumes changes on `PointerEventPass.Initial` so the hold stays local.
Any future hold-to-act key needs the same treatment. `DPad` uses `detectTapGestures` safely
because its presses are taps, and `VolumeDeck` uses `detectVerticalDragGestures`.

## Diagnosing a dead connection

`remote/Failure.kt` holds `describe()`, the one place raw transport exceptions become sentences a
user can act on, and `TAG` (`"TvRemote"`), the app's only logcat tag. Both session adapters and
`RemoteViewModel.reason()` route through it. Keep new failure paths going through `describe()`
rather than surfacing `e.message` — BoringSSL's messages in particular are unreadable and
identical across unrelated faults.

The Android TV path authenticates **both** ends, so "it stopped working" usually means one side's
certificate is no longer trusted. `adb logcat -s TvRemote` gives the real stack trace. To tell the
two cases apart without guessing — the debug build is `run-as`-able, so its state can be read
straight off the device:

```bash
adb shell run-as dev.ayaan.tvremote cat shared_prefs/tvremote.xml   # last_host, pinned TV cert
adb shell "run-as dev.ayaan.tvremote cat files/tvremote.p12 | base64"
```

The p12 password is `tvremote` (`net/CertStore.kt`), and OpenSSL 3.x needs `-legacy` to read
Android's PKCS#12. With the client cert extracted, ask the TV directly:

```bash
openssl s_client -connect <tv>:6466 -tls1_2 -cert client-cert.pem -key client-key.pem
```

- `alert 46 / certificate_unknown` — the **TV** forgot this phone (factory reset, firmware
  update). Port 6467 still pairs; the fix is Forget this TV and pair again.
- Our own `PinnedTrustManager` throwing `CertificateException` (wrapped in an `SSLException`, so
  `describe()` walks the cause chain) — the **TV's** certificate changed.
- `alert 40 / handshake_failure` with no client cert supplied is just the TV insisting on mutual
  auth, and is expected when probing without one.

## Conventions

Comments here explain *why* a non-obvious choice was made — protocol quirks, deliberate omissions,
platform behaviour — in prose, not as restatements of the code. Several carry real information
(`MainActivity` on why the permission callback does not auto-start voice; `VoiceRecorder` on why
`stop()` calls `AudioRecord.stop()` from the caller's thread). Match that density and register
rather than adding line-by-line narration.

Prose in comments and UI strings uses `--` for em dashes and British spelling in places
(`authorise`, `colour`, `recognise`); follow the surrounding file.

Failures reach the user as a sentence on the snackbar or in `Link.Failed`. A control that can fail
should never fail silently — the three bugs fixed most recently were all silent-failure or
unreadable-failure bugs, not logic bugs.
