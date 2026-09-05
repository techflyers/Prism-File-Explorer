package com.raival.compose.file.explorer.screen.main.tab.files.task

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Security
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

/**
 * Manages an on-device, app-private RSA keypair and self-signed X.509 certificate
 * used to sign merged APK bundles.
 *
 * This replaces shipped public AOSP testkeys (testkey.pk8 / testkey.x509.pem)
 * so that merged APKs are signed with a unique per-installation key.
 */
object LocalApkSigningKey {

    private const val KEY_DIR_NAME = "apk-signing"
    private const val KEY_FILE_NAME = "prism-merge.pk8"
    private const val CERT_FILE_NAME = "prism-merge.der"

    @Volatile
    private var cachedPair: Pair<PrivateKey, X509Certificate>? = null

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    @Synchronized
    fun load(context: Context): Pair<PrivateKey, X509Certificate> {
        cachedPair?.let { return it }

        val dir = File(context.filesDir, KEY_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val keyFile = File(dir, KEY_FILE_NAME)
        val certFile = File(dir, CERT_FILE_NAME)

        val pair = if (keyFile.exists() && certFile.exists()) {
            try {
                loadExisting(keyFile, certFile)
            } catch (_: Exception) {
                generateAndSave(keyFile, certFile)
            }
        } else {
            generateAndSave(keyFile, certFile)
        }

        cachedPair = pair
        return pair
    }

    private fun loadExisting(keyFile: File, certFile: File): Pair<PrivateKey, X509Certificate> {
        val keyBytes = keyFile.readBytes()
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val privateKey = keyFactory.generatePrivate(keySpec)

        val certFactory = CertificateFactory.getInstance("X.509")
        val certificate = certFile.inputStream().use { inputStream ->
            certFactory.generateCertificate(inputStream) as X509Certificate
        }

        return privateKey to certificate
    }

    private fun generateAndSave(keyFile: File, certFile: File): Pair<PrivateKey, X509Certificate> {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048, SecureRandom())
        val keyPair = keyPairGenerator.generateKeyPair()

        val issuer = X500Name("CN=Prism APK Merge, O=Prism File Explorer")
        val serial = BigInteger(64, SecureRandom())
        val notBefore = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000L) // Yesterday
        val notAfter = Date(System.currentTimeMillis() + 30L * 365 * 24 * 60 * 60 * 1000L) // ~30 years

        val certBuilder = JcaX509v3CertificateBuilder(
            issuer,
            serial,
            notBefore,
            notAfter,
            issuer,
            keyPair.public
        )

        val contentSigner = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(keyPair.private)

        val certificateHolder = certBuilder.build(contentSigner)
        val certificate = JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .getCertificate(certificateHolder)

        // Save in private internal storage
        keyFile.writeBytes(keyPair.private.encoded)
        certFile.writeBytes(certificate.encoded)

        return keyPair.private to certificate
    }
}
