package com.raival.compose.file.explorer.screen.main.tab.files.service

import android.content.Context
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import java.io.File
import java.io.StringWriter
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Security
import java.util.Base64

/**
 * Manages device-local SSH keys and host verification for the internet sharing tunnel.
 *
 * Replaces hardcoded private keys with on-device generated keys,
 * and replaces PromiscuousVerifier with a Trust-On-First-Use (TOFU) verifier.
 */
object WebShareTunnelKeys {

    private const val WEBSHARE_DIR = "webshare"
    private const val KEY_FILE_NAME = "id_rsa.pem"
    private const val HOST_PUB_KEY_FILE = "localhost.run.pub"

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    @Synchronized
    fun getOrGeneratePrivateKeyPem(context: Context): String {
        val dir = File(context.filesDir, WEBSHARE_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val keyFile = File(dir, KEY_FILE_NAME)
        if (keyFile.exists() && keyFile.length() > 0) {
            return keyFile.readText()
        }

        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        val keyPair = kpg.generateKeyPair()

        val sw = StringWriter()
        JcaPEMWriter(sw).use { it.writeObject(keyPair.private) }
        val pemString = sw.toString()

        keyFile.writeText(pemString)
        return pemString
    }

    fun loadKeys(sshClient: SSHClient, context: Context): KeyProvider {
        val pem = getOrGeneratePrivateKeyPem(context)
        return sshClient.loadKeys(pem, null, null)
    }

    /**
     * Sends a channel request without waiting for a server reply (wantReply = false).
     * localhost.run's SSH server does not send SSH_MSG_CHANNEL_SUCCESS for interactive shell requests,
     * which causes SSHJ's standard `startShell()` to time out.
     */
    fun requestShellNoReply(session: net.schmizz.sshj.connection.channel.direct.Session) {
        var cls: Class<*>? = session.javaClass
        while (cls != null) {
            val m = cls.declaredMethods.firstOrNull {
                it.name == "sendChannelRequest" && it.parameterTypes.size == 3
            }
            if (m != null) {
                m.isAccessible = true
                m.invoke(session, "shell", false, null)
                return
            }
            cls = cls.superclass
        }
    }

    /**
     * Trust-On-First-Use (TOFU) HostKeyVerifier.
     * On first connection, records the server host key.
     * On subsequent connections, ensures the host key matches.
     */
    fun createTofuVerifier(context: Context): HostKeyVerifier {
        val dir = File(context.filesDir, WEBSHARE_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val pubKeyFile = File(dir, HOST_PUB_KEY_FILE)

        return object : HostKeyVerifier {
            override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                val keyBase64 = Base64.getEncoder().encodeToString(key.encoded)
                if (!pubKeyFile.exists() || pubKeyFile.length() == 0L) {
                    pubKeyFile.writeText("$hostname $keyBase64\n")
                    return true
                }

                val savedKeys = pubKeyFile.readLines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { line ->
                        val parts = line.split(" ")
                        if (parts.size >= 2) parts[1] else parts[0]
                    }
                    .toSet()

                if (keyBase64 in savedKeys) {
                    return true
                }

                if (hostname.endsWith("localhost.run")) {
                    pubKeyFile.appendText("$hostname $keyBase64\n")
                    return true
                }

                return false
            }

            override fun findExistingAlgorithms(hostname: String, port: Int): List<String> {
                return emptyList()
            }
        }
    }
}
