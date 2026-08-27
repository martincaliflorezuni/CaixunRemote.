package dev.ayaan.tvremote.remote

import dadb.Dadb
import dev.ayaan.tvremote.net.AdbKeyStore
import dev.ayaan.tvremote.proto.RemoteDirection
import dev.ayaan.tvremote.proto.RemoteKeyCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A live control session over ADB (port 5555), for devices -- Fire TV chief
 * among them -- that don't speak the Android TV Remote v2 protocol.
 *
 * Unlike the Android TV path, the first connection can't be approved from
 * inside this app: the device shows its own "Allow debugging?" dialog, and
 * the user has to accept it with the TV's own remote before the handshake
 * completes. `connect()` reflects that with [Link.Authorizing] rather than a
 * plain "connecting" state.
 */
class FireTvClient(
    private val host: String,
    private val keyStore: AdbKeyStore,
    private val scope: CoroutineScope
) : Session {

    private val _state = MutableStateFlow<Link>(Link.Idle)
    override val state: StateFlow<Link> = _state.asStateFlow()

    /** ADB has no "a text field just got focused" push -- this never emits. */
    override val textPrompt: StateFlow<TextPrompt?> = MutableStateFlow(null).asStateFlow()

    private var dadb: Dadb? = null
    private var job: Job? = null

    fun connect() {
        if (job?.isActive == true) return
        _state.value = Link.Authorizing

        job = scope.launch(Dispatchers.IO) {
            try {
                val connection = Dadb.create(
                    host,
                    PORT,
                    keyStore.keyPair,
                    connectTimeout = CONNECT_TIMEOUT_MS,
                    socketTimeout = AUTH_TIMEOUT_MS
                )
                dadb = connection
                _state.value = Link.Ready(null)
            } catch (e: Exception) {
                _state.value = Link.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /**
     * `input keyevent` has no long-press primitive, so every direction sends
     * the same single event -- short and held presses are indistinguishable
     * over ADB.
     */
    override fun press(key: RemoteKeyCode, direction: RemoteDirection) {
        scope.launch(Dispatchers.IO) {
            runCatching { dadb?.shell("input keyevent ${key.number}") }
        }
    }

    override fun launch(deepLink: String) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                dadb?.shell("am start -a android.intent.action.VIEW -d ${shellQuote(deepLink)}")
            }
        }
    }

    /**
     * `launch(deepLink)`'s `am start -a VIEW -d <url>` isn't reliable here --
     * these are Amazon Appstore builds and often don't register the same
     * https:// deep links a Google Play TV build does. Asking the package's
     * own launcher activity to start is what actually works on Fire TV.
     */
    override fun launchApp(app: KnownApp) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                dadb?.shell(
                    "monkey -p ${app.fireTvPackage} -c android.intent.category.LAUNCHER --pct-syskeys 0 1"
                )
            }
        }
    }

    override fun sendText(text: String) {
        if (text.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            runCatching { dadb?.shell("input text ${shellQuote(text)}") }
        }
    }

    override fun disconnect() {
        job?.cancel()
        runCatching { dadb?.close() }
        dadb = null
        _state.value = Link.Idle
    }

    private fun shellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"

    private companion object {
        const val PORT = 5555
        const val CONNECT_TIMEOUT_MS = 10_000
        const val AUTH_TIMEOUT_MS = 60_000
    }
}
