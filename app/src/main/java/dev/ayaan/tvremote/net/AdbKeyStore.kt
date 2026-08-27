package dev.ayaan.tvremote.net

import android.content.Context
import dadb.AdbKeyPair
import java.io.File

/**
 * Holds this phone's ADB identity: one RSA keypair, persisted as PEM files in
 * app-private storage. The Fire TV remembers this key after the user accepts
 * the on-device authorization prompt once, so deleting these files means
 * accepting that prompt again from scratch.
 */
class AdbKeyStore(context: Context) {

    private val privateKeyFile = File(context.filesDir, "adbkey")
    private val publicKeyFile = File(context.filesDir, "adbkey.pub")

    val keyPair: AdbKeyPair by lazy {
        if (!privateKeyFile.exists()) AdbKeyPair.generate(privateKeyFile, publicKeyFile)
        AdbKeyPair.read(privateKeyFile, publicKeyFile)
    }

    fun reset() {
        privateKeyFile.delete()
        publicKeyFile.delete()
    }
}
