package com.felipe.conversornuvem

import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Util {
    val RE_PACOTE = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
    private val RESERVADAS = ("abstract assert boolean break byte case catch char class const continue default do double else enum " +
        "extends final finally float for goto if implements import instanceof int interface long native new package private " +
        "protected public return short static strictfp super switch synchronized this throw throws transient try void volatile " +
        "while true false null").split(" ").toSet()

    /** com.<nome sem acentos e símbolos>.app */
    fun gerarPacote(nome: String): String {
        var s = Normalizer.normalize(nome, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        s = s.lowercase().replace(Regex("[^a-z0-9]+"), "")
        if (s.isEmpty()) s = "meuapp"
        if (s[0].isDigit()) s = "app$s"
        if (s.length > 30) s = s.substring(0, 30)
        if (s in RESERVADAS) s += "app"
        return "com.$s.app"
    }

    fun fmtData(ts: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(ts))

    fun tamanho(b: Long): String =
        if (b >= 1048576) String.format(Locale.US, "%.1f MB", b / 1048576.0).replace('.', ',')
        else "${maxOf(1L, b / 1024)} KB"

    fun novoId(prefixo: String): String = prefixo + java.lang.Long.toString(System.currentTimeMillis(), 36)
}
