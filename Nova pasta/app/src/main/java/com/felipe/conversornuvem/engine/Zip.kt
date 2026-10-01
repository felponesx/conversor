package com.felipe.conversornuvem.engine

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipInputStream

class ZipItem(val name: String, val data: ByteArray)

object ZipTools {
    /** Lê todas as entradas (arquivos) de um .zip. Pastas são ignoradas. */
    fun readAll(input: InputStream, maxTotal: Long = 300L * 1024 * 1024): List<ZipItem> {
        val out = ArrayList<ZipItem>()
        var total = 0L
        ZipInputStream(input).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                if (e.isDirectory) continue
                val buf = ByteArrayOutputStream()
                val chunk = ByteArray(64 * 1024)
                while (true) {
                    val n = zin.read(chunk)
                    if (n < 0) break
                    buf.write(chunk, 0, n)
                    total += n
                    if (total > maxTotal) throw IllegalStateException("O projeto é grande demais (limite de ${maxTotal / 1048576} MB).")
                }
                out.add(ZipItem(e.name, buf.toByteArray()))
            }
        }
        return out
    }
}

/** Escritor de .zip simples (sem zip64) com suporte a alinhamento, como o zipalign faz. */
class ZipWriter {
    private val out = ByteArrayOutputStream()
    private class Cd(val name: ByteArray, val method: Int, val crc: Long, val csize: Long, val usize: Long, val offset: Long)
    private val cds = ArrayList<Cd>()
    private val names = HashSet<String>()

    private fun u16(v: Int) { out.write(v and 0xff); out.write((v ushr 8) and 0xff) }
    private fun u32(v: Long) { u16((v and 0xffff).toInt()); u16(((v ushr 16) and 0xffff).toInt()) }

    fun add(name: String, data: ByteArray, stored: Boolean, align: Int = 4) {
        require(names.add(name)) { "Arquivo repetido no pacote: $name" }
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val crc = CRC32().apply { update(data) }.value
        val body: ByteArray
        val method: Int
        if (stored || data.isEmpty()) {
            body = data; method = 0
        } else {
            val d = Deflater(Deflater.BEST_COMPRESSION, true)
            d.setInput(data); d.finish()
            val bo = ByteArrayOutputStream(data.size / 2 + 64)
            val tmp = ByteArray(64 * 1024)
            while (!d.finished()) { val n = d.deflate(tmp); bo.write(tmp, 0, n) }
            d.end()
            val c = bo.toByteArray()
            if (c.size >= data.size) { body = data; method = 0 } else { body = c; method = 8 }
        }
        val offset = out.size().toLong()
        var extra = 0
        if (method == 0 && align > 1) {
            var pad = ((align - ((offset + 30 + nameBytes.size) % align)) % align).toInt()
            if (pad in 1..3) pad += align
            extra = pad
        }
        u32(0x04034b50L); u16(20); u16(0x0800); u16(method); u16(0); u16(0x5821)
        u32(crc); u32(body.size.toLong()); u32(data.size.toLong())
        u16(nameBytes.size); u16(extra)
        out.write(nameBytes)
        if (extra > 0) {
            u16(0xD935); u16(extra - 4)
            out.write(ByteArray(extra - 4))
        }
        out.write(body)
        cds.add(Cd(nameBytes, method, crc, body.size.toLong(), data.size.toLong(), offset))
    }

    fun finish(): ByteArray {
        val cdStart = out.size().toLong()
        for (c in cds) {
            u32(0x02014b50L); u16(20); u16(20); u16(0x0800); u16(c.method); u16(0); u16(0x5821)
            u32(c.crc); u32(c.csize); u32(c.usize)
            u16(c.name.size); u16(0); u16(0); u16(0); u16(0); u32(0); u32(c.offset)
            out.write(c.name)
        }
        val cdSize = out.size().toLong() - cdStart
        u32(0x06054b50L); u16(0); u16(0); u16(cds.size); u16(cds.size)
        u32(cdSize); u32(cdStart); u16(0)
        return out.toByteArray()
    }
}
