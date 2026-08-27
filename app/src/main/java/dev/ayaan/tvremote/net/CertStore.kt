package dev.ayaan.tvremote.net

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Holds this phone's identity on the network: one self-signed RSA certificate,
 * persisted in a PKCS#12 file in app-private storage.
 *
 * The TV remembers this certificate after pairing, so deleting the file means
 * pairing again from scratch.
 */
class CertStore(context: Context) {

    private val file = File(context.filesDir, "tvremote.p12")
    val password: CharArray = "tvremote".toCharArray()

    val keyStore: KeyStore by lazy { loadOrCreate() }

    val certificate: X509Certificate
        get() = keyStore.getCertificate(ALIAS) as X509Certificate

    fun reset() {
        file.delete()
    }

    private fun loadOrCreate(): KeyStore {
        val ks = KeyStore.getInstance("PKCS12")
        if (file.exists()) {
            file.inputStream().use { ks.load(it, password) }
            if (ks.containsAlias(ALIAS)) return ks
        }

        ks.load(null, null)

        val keyPair = KeyPairGenerator.getInstance("RSA")
            .apply { initialize(2048) }
            .generateKeyPair()

        val now = System.currentTimeMillis()
        val name = X500Name("CN=atvremote, OU=Remote, O=TvRemote, C=IN")
        val holder = JcaX509v3CertificateBuilder(
            name,
            BigInteger(64, SecureRandom()),
            Date(now - ONE_DAY),
            Date(now + 3650L * ONE_DAY),
            name,
            keyPair.public
        ).build(JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private))

        val cert = JcaX509CertificateConverter().getCertificate(holder)
        ks.setKeyEntry(ALIAS, keyPair.private, password, arrayOf<java.security.cert.Certificate>(cert))
        file.outputStream().use { ks.store(it, password) }
        return ks
    }

    companion object {
        const val ALIAS = "atvremote"
        private const val ONE_DAY = 86_400_000L
    }
}
