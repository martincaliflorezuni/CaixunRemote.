package dev.ayaan.tvremote.remote

import android.os.Build
import android.util.Log
import com.google.protobuf.ByteString
import dev.ayaan.tvremote.discovery.TvDiscovery
import dev.ayaan.tvremote.net.CertStore
import dev.ayaan.tvremote.net.Tls
import dev.ayaan.tvremote.proto.RemoteAppLinkLaunchRequest
import dev.ayaan.tvremote.proto.RemoteConfigure
import dev.ayaan.tvremote.proto.RemoteDeviceInfo
import dev.ayaan.tvremote.proto.RemoteDirection
import dev.ayaan.tvremote.proto.RemoteEditInfo
import dev.ayaan.tvremote.proto.RemoteImeBatchEdit
import dev.ayaan.tvremote.proto.RemoteImeObject
import dev.ayaan.tvremote.proto.RemoteKeyCode
import dev.ayaan.tvremote.proto.RemoteKeyInject
import dev.ayaan.tvremote.proto.RemoteMessage
import dev.ayaan.tvremote.proto.RemotePingResponse
import dev.ayaan.tvremote.proto.RemoteSetActive
import dev.ayaan.tvremote.proto.RemoteVoiceBegin
import dev.ayaan.tvremote.proto.RemoteVoiceEnd
import dev.ayaan.tvremote.proto.RemoteVoicePayload
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.OutputStream
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSocket

sealed interface Link {
    data object Idle : Link
    data object Connecting : Link
    /** Waiting on an authorization step the user must complete on the TV itself. */
    data object Authorizing : Link
    data class Ready(val volume: Volume?) : Link
    data class Failed(val reason: String) : Link
}

data class Volume(val level: Int, val max: Int, val muted: Boolean)

/** A text field the TV just focused and wants the user to type into. */
data class TextPrompt(val value: String, val label: String)

/** Common surface both the Android TV and Fire TV adapters expose to the ViewModel. */
interface Session {
    val state: StateFlow<Link>
    val textPrompt: StateFlow<TextPrompt?>
    fun press(key: RemoteKeyCode, direction: RemoteDirection = RemoteDirection.SHORT)
    fun sendText(text: String)
    fun launch(deepLink: String)
    fun launchApp(app: KnownApp)
    fun disconnect()
}

/**
 * A live control session on port 6466.
 *
 * The TV drives the opening exchange: it asks us to configure, then to go
 * active, then keeps the socket warm with pings. Everything after that is
 * fire-and-forget key injection.
 */
class RemoteClient(
    private val host: String,
    private val certStore: CertStore,
    private val pinnedCert: X509Certificate?,
    private val scope: CoroutineScope
) : Session {

    private val _state = MutableStateFlow<Link>(Link.Idle)
    override val state: StateFlow<Link> = _state.asStateFlow()

    private val _textPrompt = MutableStateFlow<TextPrompt?>(null)
    override val textPrompt: StateFlow<TextPrompt?> = _textPrompt.asStateFlow()

    private var socket: SSLSocket? = null
    private var output: OutputStream? = null
    private var pump: Job? = null
    private val writeLock = Mutex()
    private var volume: Volume? = null

    private var imeCounter = 0
    private var imeFieldCounter = 0
    private var voiceBegin: CompletableDeferred<Int>? = null

    fun connect() {
        if (pump?.isActive == true) return
        _state.value = Link.Connecting

        pump = scope.launch(Dispatchers.IO) {
            try {
                val s = Tls.connect(
                    host = host,
                    port = TvDiscovery.REMOTE_PORT,
                    certStore = certStore,
                    pinned = pinnedCert,
                    readTimeoutMs = 0            // the TV pings; block until it speaks
                )
                socket = s
                output = s.outputStream
                read(s)
            } catch (e: Exception) {
                Log.w(TAG, "Control connection to $host failed", e)
                _state.value = Link.Failed(describe(e))
            } finally {
                closeSocket()
            }
        }
    }

    private suspend fun read(s: SSLSocket) {
        val stream = s.inputStream
        while (true) {
            val message = RemoteMessage.parseDelimitedFrom(stream) ?: break

            when {
                message.hasRemoteConfigure() -> {
                    write {
                        remoteConfigure = RemoteConfigure.newBuilder()
                            .setCode1(HANDSHAKE_CODE)
                            .setDeviceInfo(deviceInfo())
                            .build()
                    }
                }

                message.hasRemoteSetActive() -> {
                    write { remoteSetActive = RemoteSetActive.newBuilder().setActive(HANDSHAKE_CODE).build() }
                    _state.value = Link.Ready(volume)
                }

                message.hasRemotePingRequest() -> {
                    val val1 = message.remotePingRequest.val1
                    write { remotePingResponse = RemotePingResponse.newBuilder().setVal1(val1).build() }
                }

                message.hasRemoteSetVolumeLevel() -> {
                    val v = message.remoteSetVolumeLevel
                    volume = Volume(v.volumeLevel, v.volumeMax, v.volumeMuted)
                    if (_state.value is Link.Ready) _state.value = Link.Ready(volume)
                }

                message.hasRemoteError() -> {
                    _state.value = Link.Failed("The TV reported an error")
                }

                message.hasRemoteImeBatchEdit() -> {
                    imeCounter = message.remoteImeBatchEdit.imeCounter
                    imeFieldCounter = message.remoteImeBatchEdit.fieldCounter
                }

                message.hasRemoteImeShowRequest() -> {
                    val status = message.remoteImeShowRequest.remoteTextFieldStatus
                    _textPrompt.value = TextPrompt(status.value, status.label)
                }

                message.hasRemoteVoiceBegin() -> {
                    voiceBegin?.complete(message.remoteVoiceBegin.sessionId)
                }
            }
        }
        _state.value = Link.Failed("Disconnected")
    }

    override fun press(key: RemoteKeyCode, direction: RemoteDirection) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                write {
                    remoteKeyInject = RemoteKeyInject.newBuilder()
                        .setKeyCode(key)
                        .setDirection(direction)
                        .build()
                }
            }
        }
    }

    /** Opens an app by deep link, e.g. "https://www.youtube.com". */
    override fun launch(deepLink: String) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                write {
                    remoteAppLinkLaunchRequest = RemoteAppLinkLaunchRequest.newBuilder()
                        .setAppLink(deepLink)
                        .build()
                }
            }
        }
    }

    override fun launchApp(app: KnownApp) = launch(app.androidTvLink)

    /**
     * Replaces the focused text field's contents in one shot. The TV tracks
     * edits with a counter pair it pushes in `remote_ime_batch_edit`; every
     * edit we send has to echo the latest values back or the TV ignores it.
     */
    override fun sendText(text: String) {
        if (text.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val cursor = text.length - 1
                write {
                    remoteImeBatchEdit = RemoteImeBatchEdit.newBuilder()
                        .setImeCounter(imeCounter)
                        .setFieldCounter(imeFieldCounter)
                        .addEditInfo(
                            RemoteEditInfo.newBuilder()
                                .setInsert(1)
                                .setTextFieldStatus(
                                    RemoteImeObject.newBuilder()
                                        .setStart(cursor)
                                        .setEnd(cursor)
                                        .setValue(text)
                                )
                        )
                        .build()
                }
            }
        }
    }

    /**
     * Opens a voice session: presses SEARCH, waits for the TV to hand back a
     * session id in `remote_voice_begin`, then echoes that message back --
     * undocumented, but the TV won't accept audio without it. Returns the
     * session id to pass to [sendVoiceChunk] / [endVoice].
     */
    suspend fun beginVoice(timeoutMs: Long = 2000): Int {
        val pending = CompletableDeferred<Int>()
        voiceBegin = pending
        try {
            write {
                remoteKeyInject = RemoteKeyInject.newBuilder()
                    .setKeyCode(RemoteKeyCode.KEYCODE_SEARCH)
                    .setDirection(RemoteDirection.SHORT)
                    .build()
            }
            val sessionId = withTimeoutOrNull(timeoutMs) { pending.await() }
                ?: throw java.io.IOException("The TV didn't open a voice session")
            write { remoteVoiceBegin = RemoteVoiceBegin.newBuilder().setSessionId(sessionId).build() }
            return sessionId
        } finally {
            voiceBegin = null
        }
    }

    /** Streams one chunk of 16-bit PCM/8 kHz/mono audio, split to what the TV accepts. */
    fun sendVoiceChunk(sessionId: Int, chunk: ByteArray) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val padded = if (chunk.size < MIN_VOICE_CHUNK_BYTES) {
                    chunk + ByteArray(MIN_VOICE_CHUNK_BYTES - chunk.size)
                } else {
                    chunk
                }
                var offset = 0
                while (offset < padded.size) {
                    val end = minOf(offset + MAX_VOICE_CHUNK_BYTES, padded.size)
                    write {
                        remoteVoicePayload = RemoteVoicePayload.newBuilder()
                            .setSessionId(sessionId)
                            .setSamples(ByteString.copyFrom(padded, offset, end - offset))
                            .build()
                    }
                    offset = end
                }
            }
        }
    }

    fun endVoice(sessionId: Int) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                write { remoteVoiceEnd = RemoteVoiceEnd.newBuilder().setSessionId(sessionId).build() }
            }
        }
    }

    override fun disconnect() {
        pump?.cancel()
        closeSocket()
        _state.value = Link.Idle
    }

    private suspend inline fun write(build: RemoteMessage.Builder.() -> Unit) {
        val message = RemoteMessage.newBuilder().apply(build).build()
        writeLock.withLock {
            val out = output ?: return
            message.writeDelimitedTo(out)
            out.flush()
        }
    }

    private fun closeSocket() {
        runCatching { socket?.close() }
        socket = null
        output = null
    }

    private fun deviceInfo(): RemoteDeviceInfo = RemoteDeviceInfo.newBuilder()
        .setModel(Build.MODEL ?: "Android")
        .setVendor(Build.MANUFACTURER ?: "Android")
        .setUnknown1(1)
        .setUnknown2("1")
        .setPackageName("dev.ayaan.tvremote")
        .setAppVersion("1.0.0")
        .build()

    private companion object {
        /**
         * The TV expects this exact constant in the configure/set-active exchange.
         * It isn't documented anywhere; it comes from observing the official app.
         */
        const val HANDSHAKE_CODE = 622

        /** The TV drops the connection on chunks bigger than this. */
        const val MAX_VOICE_CHUNK_BYTES = 20 * 1024
        /** Smaller chunks get zero-padded; some TVs (Shield) reject anything under this. */
        const val MIN_VOICE_CHUNK_BYTES = 8 * 1024
    }
}
