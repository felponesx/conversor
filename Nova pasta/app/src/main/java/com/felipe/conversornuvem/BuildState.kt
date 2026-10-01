package com.felipe.conversornuvem

import android.os.Handler
import android.os.Looper
import java.io.File

/** Estado da compilação atual. As telas só leem daqui, então trocar de aba não perde nada. */
object BuildState {
    const val C_NONE = 0
    const val C_OK = 1
    const val C_ERR = 2
    const val C_DIM = 3
    const val C_CMD = 4

    private val main = Handler(Looper.getMainLooper())

    var running = false
    var status = "Pronto."
    var detalhe = ""
    var etapa = 0
    var pct = -1f
    var teto = -1f
    var erro = false
    var ok = false
    var andamento = false
    var labels = arrayOf("Enviar", "Fila", "Compilar", "Baixar")
    var buildId: String? = null
    var lastApk: File? = null
    var lastNome: String = "app"
    var lastLink: String? = null
    var errLink: String? = null
    val lines = ArrayList<Pair<String, Int>>()

    var onChange: (() -> Unit)? = null
    var onLine: ((String, Int) -> Unit)? = null
    var onClear: (() -> Unit)? = null

    private val ticker = object : Runnable {
        override fun run() {
            if (running && pct >= 0f && pct < teto) {
                pct = minOf(teto, pct + 0.15f)
                onChange?.invoke()
            }
            if (running) main.postDelayed(this, 400)
        }
    }

    fun ui(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    fun begin(id: String, labels: Array<String>) = ui {
        this.buildId = id
        this.labels = labels
        running = true; ok = false; erro = false; andamento = true
        status = "Preparando..."; detalhe = ""; etapa = 0; pct = 0f; teto = 0f
        lastApk = null; lastLink = null; errLink = null
        lines.clear()
        onClear?.invoke()
        onChange?.invoke()
        main.removeCallbacks(ticker)
        main.postDelayed(ticker, 400)
    }

    fun set(texto: String, etapa: Int, pct: Float, teto: Float = pct, detalhe: String = "") = ui {
        status = texto; this.etapa = etapa; this.pct = maxOf(this.pct, pct); this.teto = maxOf(teto, this.pct)
        this.detalhe = detalhe
        onChange?.invoke()
    }

    fun finishOk(texto: String, apk: File?, nome: String, link: String?) = ui {
        status = texto; ok = true; erro = false; andamento = false; running = false
        etapa = 4; pct = 100f; teto = 100f; detalhe = ""
        lastApk = apk; lastNome = nome; lastLink = link
        onChange?.invoke()
    }

    fun finishErr(texto: String, link: String? = null) = ui {
        status = texto; erro = true; ok = false; andamento = false; running = false; detalhe = ""
        errLink = link
        onChange?.invoke()
    }

    fun classify(t: String): Int = when {
        Regex("^(BUILD SUCCESSFUL|✓|✔)").containsMatchIn(t) -> C_OK
        Regex("FAILED|FAILURE|^e: |^✗|erro", RegexOption.IGNORE_CASE).containsMatchIn(t) -> C_ERR
        Regex("^(\\$|npm |npx |flutter |▶)").containsMatchIn(t) -> C_CMD
        Regex("^(> Task|…|●|Aplicando)").containsMatchIn(t) -> C_DIM
        else -> C_NONE
    }

    fun log(text: String, cls: Int = classify(text)) = ui {
        lines.add(Pair(text, cls))
        while (lines.size > 200) lines.removeAt(0)
        onLine?.invoke(text, cls)
    }
}
