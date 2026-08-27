package dev.ayaan.tvremote.net

import java.math.BigInteger
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey

/**
 * The pairing proof.
 *
 * The TV shows a 6-character hex code. The first byte is a checksum; the
 * remaining two are a nonce. Both sides hash their own and the other side's RSA
 * modulus and exponent together with that nonce. If the first byte of the
 * resulting digest matches the checksum byte, whoever typed the code must have
 * been looking at the screen -- which is the whole point.
 */
object Secret {

    class BadCode(message: String) : Exception(message)

    fun compute(client: RSAPublicKey, server: RSAPublicKey, code: String): ByteArray {
        val bytes = decodeHex(code)
        if (bytes.size < 2) throw BadCode("Code must be 6 hex characters")

        val nonce = bytes.copyOfRange(1, bytes.size)
        val digest = MessageDigest.getInstance("SHA-256").apply {
            update(client.modulus.unsigned())
            update(client.publicExponent.unsigned())
            update(server.modulus.unsigned())
            update(server.publicExponent.unsigned())
            update(nonce)
        }.digest()

        if (digest[0] != bytes[0]) throw BadCode("That code doesn't match what the TV is showing")
        return digest
    }

    /** BigInteger.toByteArray() prepends a zero sign byte; the protocol wants the raw magnitude. */
    private fun BigInteger.unsigned(): ByteArray {
        val raw = toByteArray()
        return if (raw.size > 1 && raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
    }

    private fun decodeHex(code: String): ByteArray {
        val clean = code.trim().replace(" ", "")
        if (clean.length % 2 != 0) throw BadCode("Code must have an even number of characters")
        return ByteArray(clean.length / 2) { i ->
            val hi = Character.digit(clean[i * 2], 16)
            val lo = Character.digit(clean[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) throw BadCode("Code must be hexadecimal (0-9, A-F)")
            ((hi shl 4) or lo).toByte()
        }
    }
}
