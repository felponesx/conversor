package com.felipe.conversornuvem

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.felipe.conversornuvem.engine.KeySigner
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.util.Date
import javax.security.auth.x500.X500Principal

/**
 * Assina os APKs com uma chave que fica guardada no AndroidKeyStore do aparelho.
 * Como a chave é sempre a mesma, um app gerado aqui pode ser atualizado por outra compilação.
 */
class AndroidKeySigner : KeySigner {
    private val alias = "conversor-nuvem-assinatura"
    private val ks: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    init {
        if (!ks.containsAlias(alias)) {
            val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            kpg.initialize(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setKeySize(2048)
                    .setCertificateSubject(X500Principal("CN=Conversor Nuvem"))
                    .setCertificateSerialNumber(BigInteger.valueOf(System.currentTimeMillis()))
                    .setCertificateNotBefore(Date(System.currentTimeMillis() - 86400000L))
                    .setCertificateNotAfter(Date(4102444800000L))
                    .build()
            )
            kpg.generateKeyPair()
        }
    }

    override fun certificate(): ByteArray = ks.getCertificate(alias).encoded
    override fun publicKey(): ByteArray = ks.getCertificate(alias).publicKey.encoded
    override fun sign(data: ByteArray): ByteArray {
        val s = Signature.getInstance("SHA256withRSA")
        s.initSign(ks.getKey(alias, null) as PrivateKey)
        s.update(data)
        return s.sign()
    }
}
