# TV Remote

An Android phone app that controls a TV over Wi-Fi. It speaks two different
protocols depending on what the TV is:

- **Android TV / Google TV** — the Android TV Remote v2 protocol, over
  mutually-authenticated TLS. Devices are found automatically over mDNS, and the
  first connection is authorised by typing the six-character code the TV puts on
  screen.
- **Fire TV** — ADB over the network, using the device's own "Allow USB
  debugging?" prompt as the authorisation step. Fire TV devices are not
  discovered automatically; you type the IP address in.

Both paths end at the same remote UI, but they are not equally capable — see
[Differences between the two protocols](#differences-between-the-two-protocols).

## What it does

- Finds Android TV / Google TV devices on the local network via mDNS
  (`_androidtvremote2._tcp.`)
- Pairs with a TV using the on-screen code, then remembers it — subsequent
  launches reconnect to the last TV without pairing again
- Manual connection by IP address, for either protocol
- D-pad with select, plus Back, Home, Guide, Play/Pause, Input, Search, Rewind
  and Fast-forward
- Volume as a vertical drag surface, with a mute key. On Android TV the level
  readout comes from the TV itself; the drag sends the same up/down key presses
  a physical remote would
- Hold-to-talk voice, streamed to the TV's search (Android TV only)
- On-screen keyboard entry — either opened manually, or raised automatically
  when the TV reports that a text field has taken focus (Android TV only)
- One-tap launchers for YouTube, Hotstar, Prime Video and Netflix
- Power key
- "Forget this TV", which clears the stored certificate, ADB key and protocol
  choice

## Requirements

**To build**

| Component             | Version |
|-----------------------|---------|
| JDK                   | 17      |
| Gradle                | 8.9 (via the wrapper) |
| Android Gradle Plugin | 8.6.1   |
| Kotlin                | 2.0.21  |
| compileSdk            | 35      |

These move together. See [SETUP.md](SETUP.md) for the version matrix and for what
to do if the Gradle distribution won't download.

**To run**

- An Android phone or emulator on API 26 (Android 8.0) or higher
- The phone and the TV on the same Wi-Fi network
- For Fire TV: ADB debugging enabled on the device
  (Settings → My Fire TV → Developer Options)

## Setup

Clone the repository and build with the wrapper. `local.properties` is not
checked in; Android Studio writes it on first open, or you can create it
yourself pointing at your SDK:

```
sdk.dir=/path/to/Android/Sdk
```

**Android Studio** — open the project folder and let it sync, then Run.

**Command line**

```bash
./gradlew assembleDebug     # build the debug APK
./gradlew installDebug      # build and install onto a connected device
```

On Windows use `gradlew.bat` instead of `./gradlew`.

The debug APK lands in `app/build/outputs/apk/debug/`. The project defines no
release signing configuration, so debug is the only build that is signed and
installable as-is.

## Usage

### Android TV / Google TV

1. Open the app with the TV awake and on the same Wi-Fi. Discovered devices
   appear under **On this network**.
2. Tap your TV. The first time, it shows a six-character code — type that into
   the app and tap **Pair**.
3. The remote screen opens once the TV accepts.

Pairing stores a certificate for that TV. Next launch, the app reconnects to the
last TV automatically and skips straight to the remote.

If the TV doesn't appear in the list, type its IP address into **Or type an
address** with **Android TV** selected. Discovery needs multicast, which some
phones and networks filter.

### Fire TV

1. On the Fire TV, enable ADB debugging under
   Settings → My Fire TV → Developer Options.
2. In the app, select the **Fire TV** chip, type the device's IP address, and
   tap **Connect**. The app shows *Check your Fire TV* while it waits.
3. The Fire TV displays its own authorisation prompt. Accept it **using the Fire
   TV's own remote** — it cannot be accepted from inside this app. This is only
   required the first time.

### The remote

- **D-pad** — arrows to move, centre to select
- **Volume** — drag the volume panel up or down; tap the key beside it to mute
- **Mic** — press and hold to speak, release to stop. The phone asks for
  microphone permission the first time; after granting it, hold the key again
- **Keyboard** — type text and send it to the focused field on the TV
- **App tiles** — launch YouTube, Hotstar, Prime Video or Netflix
- **Forget this TV** — clears the stored credentials for that device, so the
  next connection pairs from scratch

## Differences between the two protocols

| | Android TV | Fire TV (ADB) |
|---|---|---|
| Discovery | Automatic (mDNS) | Manual IP entry |
| Authorisation | Code shown on TV, typed into the app | Prompt on the TV, accepted with its own remote |
| Voice | Yes | No — ADB has no equivalent channel |
| Volume readout | Reported by the TV | Not reported; the app shows its own running estimate |
| Long press | Distinct from a short press | Indistinguishable — `input keyevent` has no long-press form |
| Keyboard prompt | The TV pushes focus changes, so the keyboard opens on its own | No push; open the keyboard manually |

## How it works

- **Discovery** (`discovery/TvDiscovery.kt`) — `NsdManager` browsing for
  `_androidtvremote2._tcp.`, holding a Wi-Fi multicast lock for the duration
- **Identity** (`net/CertStore.kt`) — one self-signed RSA-2048 certificate per
  install, generated with BouncyCastle and kept in a PKCS#12 file in app-private
  storage. This is what the TV remembers after pairing
- **Pairing** (`pairing/PairingClient.kt`, `net/Secret.kt`) — the protobuf
  handshake on port 6467. The on-screen code carries a checksum byte and a
  nonce; both sides hash their own and the other's public key together with that
  nonce, and the checksum confirms whoever typed the code was looking at the
  screen
- **Control** (`remote/RemoteClient.kt`) — a long-lived TLS connection on port
  6466. The TV drives the opening exchange and keeps the socket alive with
  pings; key presses are fire-and-forget protobuf messages
- **Transport security** (`net/Tls.kt`) — TLS 1.2 with client certificate auth.
  First contact accepts whatever certificate the TV presents, since there is
  nothing yet to validate against; after pairing the certificate is pinned, and
  a change is treated as an error
- **Fire TV** (`remote/FireTvClient.kt`, `net/AdbKeyStore.kt`) — a `dadb`
  connection on port 5555, driving `input keyevent`, `input text` and `monkey`
  shell commands. The ADB keypair lives in app-private storage alongside the
  certificate
- **Voice** (`remote/VoiceRecorder.kt`) — `AudioRecord` capturing 16-bit PCM at
  8 kHz mono, which is what the voice channel expects, so nothing needs
  re-encoding between the mic and the wire
- **UI** (`ui/`) — Jetpack Compose with Material 3, a single `AndroidViewModel`
  holding a `StateFlow` of the whole screen state

The protobuf definitions in `app/src/main/proto/` are compiled at build time by
the protobuf Gradle plugin.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Talking to the TV |
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Network and Wi-Fi state for discovery |
| `CHANGE_WIFI_MULTICAST_STATE` | The multicast lock mDNS discovery needs |
| `RECORD_AUDIO` | Hold-to-talk voice; requested at the point of first use |

## Project layout

```
app/src/main/
  java/dev/ayaan/tvremote/
    MainActivity.kt          entry point; hosts the Compose tree
    discovery/               mDNS browsing
    net/                     certificates, ADB keys, TLS, pairing secret
    pairing/                 the pairing handshake
    remote/                  the two session implementations, voice capture
    ui/                      Compose screens, remote deck, theme
  proto/                     pairing and remote protocol definitions
  res/                       app logos and theme
```
