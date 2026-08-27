package dev.ayaan.tvremote.net

import java.net.InetSocketAddress
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * Opens a mutually-authenticated TLS socket to the TV.
 *
 * The TV presents a self-signed certificate, so there is nothing to validate it
 * against on first contact -- the pairing handshake is what establishes trust.
 * Once paired you should pin [PinnedTrustManager] to the certificate you saw
 * during pairing; accepting anything forever leaves you open to a spoofed TV on
 * the same network.
 */
object Tls {

    fun connect(
        host: String,
        port: Int,
        certStore: CertStore,
        pinned: X509Certificate? = null,
        connectTimeoutMs: Int = 8_000,
        readTimeoutMs: Int = 20_000
    ): SSLSocket {
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(certStore.keyStore, certStore.password)

        val ctx = SSLContext.getInstance("TLSv1.2")
        ctx.init(kmf.keyManagers, arrayOf(PinnedTrustManager(pinned)), SecureRandom())

        val socket = ctx.socketFactory.createSocket() as SSLSocket
        socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
        socket.soTimeout = readTimeoutMs
        socket.startHandshake()
        return socket
    }

    fun peerCertificate(socket: SSLSocket): X509Certificate =
        socket.session.peerCertificates.first() as X509Certificate

    private class PinnedTrustManager(private val pinned: X509Certificate?) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val expected = pinned ?: return          // first contact: nothing to pin against yet
            val actual = chain?.firstOrNull() ?: throw java.security.cert.CertificateException("no cert")
            if (expected != actual) {
                throw java.security.cert.CertificateException("TV certificate changed -- pair again")
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
