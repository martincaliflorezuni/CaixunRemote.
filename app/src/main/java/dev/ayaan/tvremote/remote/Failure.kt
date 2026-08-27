package dev.ayaan.tvremote.remote

import java.security.cert.CertificateException
import javax.net.ssl.SSLException

/** Shared logcat tag. The app is otherwise silent, so failures log through here. */
const val TAG = "TvRemote"

/**
 * Turns a connection failure into a sentence that tells the user what to do
 * next.
 *
 * TLS failures need translating more than most. BoringSSL reports a rejected
 * client certificate as `Read error: ssl=0x...: Failure in SSL library, usually
 * a protocol error` -- true, unreadable, and identical to half a dozen
 * unrelated faults. Two cases matter here and both mean the stored pairing is
 * dead:
 *
 * - The TV forgot us. It answers 6466 with a `certificate_unknown` alert
 *   (alert 46) because the client certificate we present is no longer in its
 *   list of paired remotes -- a factory reset or a firmware update will do
 *   this. Port 6467 still pairs, so the way out is to pair again.
 * - The TV's own certificate changed, which [dev.ayaan.tvremote.net.Tls]'s
 *   pinned trust manager catches and reports as a [CertificateException]
 *   wrapped inside an [SSLException].
 *
 * Both end at the same instruction, so they share wording apart from the cause.
 */
fun describe(e: Throwable): String = when {
    e.hasCause<CertificateException>() ->
        "This TV is presenting a different certificate than the one saved. " +
            "Tap Forget this TV, then pair again."

    e is SSLException ->
        "The TV no longer recognises this phone -- its pairing was most likely " +
            "cleared by a reset or an update. Tap Forget this TV, then pair again."

    e is java.net.SocketTimeoutException ->
        "The TV didn't answer. Check it's awake and on this Wi-Fi."

    e is java.net.ConnectException ->
        "Couldn't reach that address on this network."

    else -> e.message ?: "Something went wrong"
}

/** Walks the cause chain, since JSSE wraps trust-manager failures. */
private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is T) return true
        current = current.cause
    }
    return false
}
