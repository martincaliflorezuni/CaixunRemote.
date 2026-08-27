package dev.ayaan.tvremote.pairing

import dev.ayaan.tvremote.discovery.TvDiscovery
import dev.ayaan.tvremote.net.CertStore
import dev.ayaan.tvremote.net.Secret
import dev.ayaan.tvremote.net.Tls
import dev.ayaan.tvremote.proto.PairingConfiguration
import dev.ayaan.tvremote.proto.PairingEncoding
import dev.ayaan.tvremote.proto.PairingMessage
import dev.ayaan.tvremote.proto.PairingOption
import dev.ayaan.tvremote.proto.PairingRequest
import dev.ayaan.tvremote.proto.PairingSecret
import dev.ayaan.tvremote.proto.RoleType
import com.google.protobuf.ByteString
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

/**
 * Runs the one-time handshake on port 6467 that gets our certificate onto the
 * TV's list of trusted remotes.
 *
 * Two phases, because a human sits between them: [begin] gets far enough that
 * the TV puts a code on screen, then [submitCode] finishes once that code has
 * been typed in.
 */
class PairingClient(
    private val host: String,
    private val certStore: CertStore
) : Closeable {

    class Rejected(message: String) : Exception(message)

    private var socket: SSLSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var serverCert: X509Certificate? = null

    /** Connects and drives the handshake up to the point where the TV shows a code. */
    fun begin(clientName: String) {
        val s = Tls.connect(host, TvDiscovery.PAIRING_PORT, certStore)
        socket = s
        input = s.inputStream
        output = s.outputStream
        serverCert = Tls.peerCertificate(s)

        send {
            pairingRequest = PairingRequest.newBuilder()
                .setServiceName(SERVICE_NAME)
                .setClientName(clientName)
                .build()
        }
        await { it.hasPairingRequestAck() }

        val hex = PairingEncoding.newBuilder()
            .setType(PairingEncoding.EncodingType.ENCODING_TYPE_HEXADECIMAL)
            .setSymbolLength(CODE_LENGTH)
            .build()

        send {
            pairingOption = PairingOption.newBuilder()
                .addInputEncodings(hex)
                .setPreferredRole(RoleType.ROLE_TYPE_INPUT)
                .build()
        }
        await { it.hasPairingOption() }

        send {
            pairingConfiguration = PairingConfiguration.newBuilder()
                .setEncoding(hex)
                .setClientRole(RoleType.ROLE_TYPE_INPUT)
                .build()
        }
        await { it.hasPairingConfigurationAck() }
        // The TV is now displaying a 6-character code.
    }

    /**
     * Sends the proof derived from [code]. Returns the TV's certificate so the
     * caller can pin it for the control session.
     */
    fun submitCode(code: String): X509Certificate {
        val server = serverCert ?: error("begin() must be called first")
        val digest = Secret.compute(
            client = certStore.certificate.publicKey as RSAPublicKey,
            server = server.publicKey as RSAPublicKey,
            code = code
        )

        send {
            pairingSecret = PairingSecret.newBuilder()
                .setSecret(ByteString.copyFrom(digest))
                .build()
        }
        await { it.hasPairingSecretAck() }
        return server
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null; input = null; output = null
    }

    private inline fun send(build: PairingMessage.Builder.() -> Unit) {
        val message = PairingMessage.newBuilder()
            .setProtocolVersion(PROTOCOL_VERSION)
            .setStatus(PairingMessage.Status.STATUS_OK)
            .apply(build)
            .build()
        val out = output ?: error("not connected")
        message.writeDelimitedTo(out)   // varint length prefix, then the message
        out.flush()
    }

    private fun await(matches: (PairingMessage) -> Boolean): PairingMessage {
        val stream = input ?: error("not connected")
        while (true) {
            val message = PairingMessage.parseDelimitedFrom(stream)
                ?: throw Rejected("The TV closed the connection")

            when (message.status) {
                PairingMessage.Status.STATUS_OK -> Unit
                PairingMessage.Status.STATUS_BAD_SECRET ->
                    throw Rejected("Wrong code -- check the screen and try again")
                PairingMessage.Status.STATUS_BAD_CONFIGURATION ->
                    throw Rejected("The TV rejected the pairing configuration")
                else -> throw Rejected("Pairing failed (${message.status})")
            }

            if (matches(message)) return message
        }
    }

    companion object {
        private const val PROTOCOL_VERSION = 2
        private const val SERVICE_NAME = "atvremote"
        private const val CODE_LENGTH = 6
    }
}
