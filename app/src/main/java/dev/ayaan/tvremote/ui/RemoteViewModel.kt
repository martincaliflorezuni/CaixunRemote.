package dev.ayaan.tvremote.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ayaan.tvremote.discovery.Tv
import dev.ayaan.tvremote.discovery.TvDiscovery
import dev.ayaan.tvremote.net.AdbKeyStore
import dev.ayaan.tvremote.net.CertStore
import dev.ayaan.tvremote.net.Secret
import dev.ayaan.tvremote.pairing.PairingClient
import dev.ayaan.tvremote.proto.RemoteDirection
import dev.ayaan.tvremote.proto.RemoteKeyCode
import dev.ayaan.tvremote.remote.FireTvClient
import dev.ayaan.tvremote.remote.KnownApp
import dev.ayaan.tvremote.remote.Link
import dev.ayaan.tvremote.remote.RemoteClient
import dev.ayaan.tvremote.remote.Session
import dev.ayaan.tvremote.remote.TextPrompt
import dev.ayaan.tvremote.remote.VoiceRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import android.util.Base64

enum class Step { Discover, EnterCode, Control }

enum class Protocol { ANDROID_TV, FIRE_TV }

data class UiState(
    val step: Step = Step.Discover,
    val devices: List<Tv> = emptyList(),
    val connecting: Tv? = null,
    val link: Link = Link.Idle,
    val error: String? = null,
    val busy: Boolean = false,
    val textPrompt: TextPrompt? = null,
    val listening: Boolean = false
)

class RemoteViewModel(app: Application) : AndroidViewModel(app) {

    private val certStore = CertStore(app)
    private val adbKeyStore = AdbKeyStore(app)
    private val discovery = TvDiscovery(app)
    private val prefs = app.getSharedPreferences("tvremote", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var pairing: PairingClient? = null
    private var session: Session? = null
    private var voiceRecorder: VoiceRecorder? = null
    private var voiceSessionId: Int? = null
    private var micHeld = false

    init {
        viewModelScope.launch {
            discovery.devices()
                .catch { e -> _state.value = _state.value.copy(error = e.message) }
                .collect { found -> _state.value = _state.value.copy(devices = found) }
        }
        lastTv()?.let { tv ->
            when (savedProtocol(tv.host)) {
                Protocol.ANDROID_TV -> select(tv)
                Protocol.FIRE_TV -> openFireTvSession(tv)
            }
        }
    }

    /** Always Android TV: this is only ever called with an mDNS-discovered device. */
    fun select(tv: Tv) {
        rememberProtocol(tv.host, Protocol.ANDROID_TV)
        val saved = savedCert(tv.host)
        if (saved != null) {
            openSession(tv, saved)
        } else {
            startPairing(tv)
        }
    }

    fun connectManually(host: String, protocol: Protocol) {
        val tv = Tv(host, host)
        rememberProtocol(host, protocol)
        when (protocol) {
            Protocol.ANDROID_TV -> select(tv)
            Protocol.FIRE_TV -> openFireTvSession(tv)
        }
    }

    private fun startPairing(tv: Tv) {
        _state.value = _state.value.copy(connecting = tv, busy = true, error = null)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    PairingClient(tv.host, certStore).also { it.begin(deviceName()) }
                }
            }.onSuccess {
                pairing = it
                _state.value = _state.value.copy(step = Step.EnterCode, busy = false)
            }.onFailure { e ->
                _state.value = _state.value.copy(busy = false, error = reason(e))
            }
        }
    }

    fun submitCode(code: String) {
        val client = pairing ?: return
        val tv = _state.value.connecting ?: return
        _state.value = _state.value.copy(busy = true, error = null)

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { client.submitCode(code) }
            }.onSuccess { cert ->
                client.close()
                pairing = null
                rememberCert(tv.host, cert)
                openSession(tv, cert)
            }.onFailure { e ->
                _state.value = _state.value.copy(busy = false, error = reason(e))
            }
        }
    }

    private fun openSession(tv: Tv, cert: X509Certificate) {
        resetVoice()
        session?.disconnect()
        prefs.edit().putString(KEY_LAST_HOST, tv.host).putString(KEY_LAST_NAME, tv.name).apply()

        val client = RemoteClient(tv.host, certStore, cert, viewModelScope)
        session = client
        _state.value = _state.value.copy(
            step = Step.Control, connecting = tv, busy = false, error = null, textPrompt = null
        )

        viewModelScope.launch { client.state.collect { _state.value = _state.value.copy(link = it) } }
        viewModelScope.launch { client.textPrompt.collect { _state.value = _state.value.copy(textPrompt = it) } }
        client.connect()
    }

    private fun openFireTvSession(tv: Tv) {
        resetVoice()
        session?.disconnect()
        prefs.edit().putString(KEY_LAST_HOST, tv.host).putString(KEY_LAST_NAME, tv.name).apply()

        val client = FireTvClient(tv.host, adbKeyStore, viewModelScope)
        session = client
        _state.value = _state.value.copy(
            step = Step.Control, connecting = tv, busy = false, error = null, textPrompt = null
        )

        viewModelScope.launch { client.state.collect { _state.value = _state.value.copy(link = it) } }
        viewModelScope.launch { client.textPrompt.collect { _state.value = _state.value.copy(textPrompt = it) } }
        client.connect()
    }

    fun press(key: RemoteKeyCode, direction: RemoteDirection = RemoteDirection.SHORT) =
        session?.press(key, direction)

    fun launch(deepLink: String) = session?.launch(deepLink)

    fun launchApp(app: KnownApp) = session?.launchApp(app)

    fun sendText(text: String) = session?.sendText(text)

    fun dismissTextPrompt() {
        _state.value = _state.value.copy(textPrompt = null)
    }

    /** Whether the current session can stream voice -- only the Android TV path can. */
    fun voiceSupported(): Boolean = session is RemoteClient

    fun onMicPressed() {
        val client = session as? RemoteClient
        if (client == null) {
            _state.value = _state.value.copy(
                error = "Voice isn't available over ADB -- Fire TV has no equivalent channel"
            )
            return
        }
        if (_state.value.listening) return
        micHeld = true

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { client.beginVoice() }
            }.onSuccess { sessionId ->
                if (!micHeld) {
                    // Released before the TV answered -- nothing to tear down
                    // yet except the session the TV just opened for us. Say so,
                    // otherwise a too-quick tap looks like a dead key.
                    client.endVoice(sessionId)
                    _state.value = _state.value.copy(
                        error = "Keep holding the Google key while you speak"
                    )
                    return@onSuccess
                }
                voiceSessionId = sessionId
                _state.value = _state.value.copy(listening = true)
                val recorder = VoiceRecorder(viewModelScope)
                voiceRecorder = recorder
                recorder.start { chunk -> client.sendVoiceChunk(sessionId, chunk) }
            }.onFailure { e ->
                _state.value = _state.value.copy(error = reason(e))
            }
        }
    }

    fun onMicReleased() {
        micHeld = false
        val client = session as? RemoteClient ?: return
        val sessionId = voiceSessionId ?: return
        resetVoice()
        client.endVoice(sessionId)
        _state.value = _state.value.copy(listening = false)
    }

    private fun resetVoice() {
        micHeld = false
        voiceRecorder?.stop()
        voiceRecorder = null
        voiceSessionId = null
    }

    fun back() {
        pairing?.close(); pairing = null
        resetVoice()
        session?.disconnect(); session = null
        _state.value = _state.value.copy(
            step = Step.Discover, connecting = null, link = Link.Idle, error = null,
            textPrompt = null, listening = false
        )
    }

    fun forget() {
        val host = _state.value.connecting?.host
        if (host != null) prefs.edit().remove(certKey(host)).remove(protocolKey(host)).apply()
        certStore.reset()
        adbKeyStore.reset()
        back()
    }

    /** Surfaces a message from the UI layer on the same snackbar the session uses. */
    fun report(message: String) {
        _state.value = _state.value.copy(error = message)
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    override fun onCleared() {
        pairing?.close()
        resetVoice()
        session?.disconnect()
        super.onCleared()
    }

    // --- persistence -------------------------------------------------------

    private fun certKey(host: String) = "cert:$host"

    private fun protocolKey(host: String) = "protocol:$host"

    private fun rememberProtocol(host: String, protocol: Protocol) {
        prefs.edit().putString(protocolKey(host), protocol.name).apply()
    }

    private fun savedProtocol(host: String): Protocol =
        prefs.getString(protocolKey(host), null)
            ?.let { runCatching { Protocol.valueOf(it) }.getOrNull() }
            ?: Protocol.ANDROID_TV

    private fun rememberCert(host: String, cert: X509Certificate) {
        val encoded = Base64.encodeToString(cert.encoded, Base64.NO_WRAP)
        prefs.edit().putString(certKey(host), encoded).apply()
    }

    private fun savedCert(host: String): X509Certificate? {
        val encoded = prefs.getString(certKey(host), null) ?: return null
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
        }.getOrNull()
    }

    private fun lastTv(): Tv? {
        val host = prefs.getString(KEY_LAST_HOST, null) ?: return null
        return Tv(prefs.getString(KEY_LAST_NAME, host) ?: host, host)
    }

    private fun deviceName() = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

    private fun reason(e: Throwable): String = when (e) {
        is Secret.BadCode -> e.message ?: "That code didn't work"
        is PairingClient.Rejected -> e.message ?: "The TV refused to pair"
        is java.net.SocketTimeoutException -> "The TV didn't answer. Check it's awake and on this Wi-Fi."
        is java.net.ConnectException -> "Couldn't reach that address on this network."
        else -> e.message ?: "Something went wrong"
    }

    private companion object {
        const val KEY_LAST_HOST = "last_host"
        const val KEY_LAST_NAME = "last_name"
    }
}
