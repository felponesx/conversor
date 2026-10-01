package com.felipe.conversornuvem.engine

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Quem assina: no celular usa o AndroidKeyStore; nos testes, uma chave comum. */
interface KeySigner {
    fun certificate(): ByteArray   // certificado X.509 em DER
    fun publicKey(): ByteArray     // chave pública em DER (SubjectPublicKeyInfo)
    fun sign(data: ByteArray): ByteArray   // SHA256withRSA (PKCS#1 v1.5)
}

/** Assinatura "APK Signature Scheme v2" (exigida pelo Android 7+ para apps novos). */
object ApkSigner {
    private const val CHUNK = 1024 * 1024
    private const val ALGO_RSA_PKCS1_SHA256 = 0x0103
    private const val ID_V2 = 0x7109871a

    private fun le(v: Long, n: Int): ByteArray {
        val b = ByteArray(n)
        for (i in 0 until n) b[i] = ((v ushr (8 * i)) and 0xff).toByte()
        return b
    }
    private fun lp(d: ByteArray): ByteArray = le(d.size.toLong(), 4) + d

    private fun topDigest(parts: List<ByteArray>): ByteArray {
        var count = 0
        for (p in parts) count += (p.size + CHUNK - 1) / CHUNK
        val top = MessageDigest.getInstance("SHA-256")
        top.update(0x5a.toByte()); top.update(le(count.toLong(), 4))
        val md = MessageDigest.getInstance("SHA-256")
        for (p in parts) {
            var off = 0
            while (off < p.size) {
                val n = minOf(CHUNK, p.size - off)
                md.reset()
                md.update(0xa5.toByte()); md.update(le(n.toLong(), 4)); md.update(p, off, n)
                top.update(md.digest())
                off += n
            }
        }
        return top.digest()
    }

    fun sign(zip: ByteArray, signer: KeySigner): ByteArray {
        val b = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN)
        val eocd = zip.size - 22
        require(eocd >= 0 && b.getInt(eocd) == 0x06054b50) { "Pacote ZIP inválido (sem fim de diretório)." }
        val cdOffset = b.getInt(eocd + 16)

        val s1 = zip.copyOfRange(0, cdOffset)
        val s2 = zip.copyOfRange(cdOffset, eocd)
        val s3 = zip.copyOfRange(eocd, zip.size)
        val digest = topDigest(listOf(s1, s2, s3))

        val digests = lp(le(ALGO_RSA_PKCS1_SHA256.toLong(), 4) + lp(digest))
        val signedData = lp(digests) + lp(lp(signer.certificate())) + lp(ByteArray(0))
        val signature = signer.sign(signedData)
        val signatures = lp(le(ALGO_RSA_PKCS1_SHA256.toLong(), 4) + lp(signature))
        val signerBlock = lp(signedData) + lp(signatures) + lp(signer.publicKey())
        val value = lp(lp(signerBlock))

        val pair = le((4 + value.size).toLong(), 8) + le(ID_V2.toLong(), 4) + value
        val blockSize = pair.size + 8 + 16
        val block = ByteArrayOutputStream()
        block.write(le(blockSize.toLong(), 8)); block.write(pair)
        block.write(le(blockSize.toLong(), 8)); block.write("APK Sig Block 42".toByteArray(Charsets.US_ASCII))
        val blockBytes = block.toByteArray()

        val newEocd = s3.copyOf()
        ByteBuffer.wrap(newEocd).order(ByteOrder.LITTLE_ENDIAN).putInt(16, cdOffset + blockBytes.size)

        val out = ByteArrayOutputStream(zip.size + blockBytes.size)
        out.write(s1); out.write(blockBytes); out.write(s2); out.write(newEocd)
        return out.toByteArray()
    }
}
