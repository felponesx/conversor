package com.felipe.conversornuvem

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import java.io.ByteArrayOutputStream

object IconMaker {
    private fun png(b: Bitmap): ByteArray {
        val o = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.PNG, 100, o)
        return o.toByteArray()
    }

    /** Lê uma imagem escolhida, corta em quadrado e devolve um PNG de 512 x 512 (ou null se não abrir). */
    fun fromUri(ctx: Context, uri: Uri): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val s = minOf(bmp.width, bmp.height)
        val sq = Bitmap.createBitmap(bmp, (bmp.width - s) / 2, (bmp.height - s) / 2, s, s)
        return png(Bitmap.createScaledBitmap(sq, 512, 512, true))
    }

    /** PNG do ícone no tamanho pedido. Sem imagem do usuário, desenha um quadrado colorido com a inicial do nome. */
    fun renderPng(icon512: ByteArray?, nome: String, size: Int): ByteArray {
        if (icon512 != null) {
            val src = BitmapFactory.decodeByteArray(icon512, 0, icon512.size)
            if (src != null) return png(Bitmap.createScaledBitmap(src, size, size, true))
        }
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val hue = (Math.abs(nome.hashCode()) % 360).toFloat()
        c.drawColor(Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.55f)))
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.WHITE
        p.textAlign = Paint.Align.CENTER
        p.isFakeBoldText = true
        p.textSize = size * 0.55f
        val letra = (nome.trim().firstOrNull() ?: 'A').uppercaseChar().toString()
        c.drawText(letra, size / 2f, size / 2f - (p.ascent() + p.descent()) / 2f, p)
        return png(bmp)
    }
}
