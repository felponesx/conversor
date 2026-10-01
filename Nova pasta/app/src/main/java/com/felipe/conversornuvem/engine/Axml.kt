package com.felipe.conversornuvem.engine

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Edita o AndroidManifest.xml binário (AXML): troca textos da tabela de strings e o versionCode. */
object Axml {
    private fun bb(d: ByteArray) = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)

    fun patch(data: ByteArray, replace: Map<String, String>, versionCode: Int?): ByteArray {
        val b = bb(data)
        require(b.getShort(0).toInt() and 0xffff == 0x0003) { "Manifesto inválido (cabeçalho)." }
        val pos = 8
        require(b.getShort(pos).toInt() and 0xffff == 0x0001) { "Manifesto inválido (tabela de strings)." }
        val headerSize = b.getShort(pos + 2).toInt() and 0xffff
        val poolSize = b.getInt(pos + 4)
        val count = b.getInt(pos + 8)
        val styleCount = b.getInt(pos + 12)
        val flags = b.getInt(pos + 16)
        val stringsStart = b.getInt(pos + 20)
        require(styleCount == 0) { "Manifesto com estilos não é suportado." }
        val utf8 = (flags and 0x100) != 0
        val base = pos + stringsStart

        val strings = ArrayList<String>(count)
        for (i in 0 until count) {
            val off = base + b.getInt(pos + headerSize + 4 * i)
            strings.add(if (utf8) readUtf8(data, off) else readUtf16(b, off))
        }
        val patched = ArrayList<String>(strings)
        for ((k, v) in replace) {
            val idx = strings.indexOf(k)
            require(idx >= 0) { "Texto do modelo não encontrado no manifesto: $k" }
            patched[idx] = v
        }

        // monta a nova tabela de strings
        val blob = ByteArrayOutputStream()
        val offsets = IntArray(count)
        for (i in 0 until count) {
            offsets[i] = blob.size()
            blob.write(if (utf8) encUtf8(patched[i]) else encUtf16(patched[i]))
        }
        while (blob.size() % 4 != 0) blob.write(0)
        val newStringsStart = headerSize + 4 * count
        val newPoolSize = newStringsStart + blob.size()

        val rest = data.copyOfRange(pos + poolSize, data.size)
        if (versionCode != null) patchVersion(rest, strings, versionCode)

        val out = ByteBuffer.allocate(8 + newPoolSize + rest.size).order(ByteOrder.LITTLE_ENDIAN)
        out.put(data, 0, 8)
        out.put(data, pos, headerSize)          // cabeçalho da tabela (depois ajustamos tamanhos)
        out.putInt(8 + 4, newPoolSize)
        out.putInt(8 + 20, newStringsStart)
        out.position(8 + headerSize)
        for (o in offsets) out.putInt(o)
        out.put(blob.toByteArray())
        out.put(rest)
        out.putInt(4, out.capacity())
        return out.array()
    }

    private fun patchVersion(rest: ByteArray, strings: List<String>, version: Int) {
        val b = bb(rest)
        var r = 0
        while (r + 8 <= rest.size) {
            val type = b.getShort(r).toInt() and 0xffff
            val size = b.getInt(r + 4)
            if (size <= 0) return
            if (type == 0x0102) {
                val name = strings.getOrNull(b.getInt(r + 20))
                if (name == "manifest") {
                    val attrStart = b.getShort(r + 24).toInt() and 0xffff
                    val attrSize = b.getShort(r + 26).toInt() and 0xffff
                    val attrCount = b.getShort(r + 28).toInt() and 0xffff
                    for (i in 0 until attrCount) {
                        val a = r + 16 + attrStart + i * attrSize
                        if (strings.getOrNull(b.getInt(a + 4)) == "versionCode") {
                            b.putInt(a + 8, -1)
                            rest[a + 15] = 0x10
                            b.putInt(a + 16, version)
                        }
                    }
                    return
                }
            }
            r += size
        }
    }

    private fun readUtf16(b: ByteBuffer, off: Int): String {
        var len = b.getShort(off).toInt() and 0xffff
        var p = off + 2
        if (len and 0x8000 != 0) { len = ((len and 0x7fff) shl 16) or (b.getShort(p).toInt() and 0xffff); p += 2 }
        val sb = StringBuilder(len)
        for (i in 0 until len) sb.append((b.getShort(p + 2 * i).toInt() and 0xffff).toChar())
        return sb.toString()
    }

    private fun readUtf8(d: ByteArray, off: Int): String {
        var p = off
        if ((d[p++].toInt() and 0x80) != 0) p++   // tamanho em caracteres (não usado)
        var u8 = d[p++].toInt() and 0xff
        if (u8 and 0x80 != 0) { u8 = ((u8 and 0x7f) shl 8) or (d[p++].toInt() and 0xff) }
        return String(d, p, u8, Charsets.UTF_8)
    }

    private fun encUtf16(s: String): ByteArray {
        val o = ByteArrayOutputStream()
        val n = s.length
        if (n > 0x7fff) {
            val hi = (n ushr 16) or 0x8000
            o.write(hi and 0xff); o.write((hi ushr 8) and 0xff)
            o.write(n and 0xff); o.write((n ushr 8) and 0xff)
        } else { o.write(n and 0xff); o.write((n ushr 8) and 0xff) }
        for (c in s) { o.write(c.code and 0xff); o.write((c.code ushr 8) and 0xff) }
        o.write(0); o.write(0)
        return o.toByteArray()
    }

    private fun encUtf8(s: String): ByteArray {
        val bytes = s.toByteArray(Charsets.UTF_8)
        val o = ByteArrayOutputStream()
        fun len(n: Int) { if (n > 0x7f) { o.write((n ushr 8) or 0x80); o.write(n and 0xff) } else o.write(n) }
        len(s.length); len(bytes.size)
        o.write(bytes); o.write(0)
        return o.toByteArray()
    }
}
