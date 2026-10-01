package com.felipe.conversornuvem

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class HomeScreen(private val act: MainActivity, private val ui: Ui) {
    private val p = ui.p
    val view: ScrollView

    private val tipos = listOf(
        Triple("kotlin", "Kotlin / Java", "Envie o .zip do projeto Android completo, com o settings.gradle. Compila pelo GitHub."),
        Triple("html", "HTML", "Envie um .zip com o index.html (e pastas) ou um único .html. Também pode colar o código abaixo. Converte aqui no celular, sem GitHub."),
        Triple("flutter", "Flutter", "Envie o .zip do projeto, com o pubspec.yaml e a pasta lib. Compila pelo GitHub."),
        Triple("reactnative", "React Native", "Envie o .zip com o package.json (projeto Expo). Compila pelo GitHub.")
    )
    private var tipoSel = 1
    private var fonteUri: Uri? = null
    private var fonteNome = ""
    private var fonteTam = 0L
    private var iconeBytes: ByteArray? = null
    private var pacoteManual = false

    private lateinit var tvStatus: TextView
    private lateinit var tvDetalhe: TextView
    private lateinit var tvPct: TextView
    private lateinit var bar: BarView
    private lateinit var indet: BarView
    private lateinit var passos: List<View>
    private lateinit var rotulos: List<TextView>
    private lateinit var term: TerminalView
    private lateinit var btnInstalar: TextView
    private lateinit var btnSalvar: TextView
    private lateinit var btnShare: TextView
    private lateinit var btnGit: TextView
    private lateinit var btnErro: TextView
    private lateinit var chips: List<TextView>
    private lateinit var tvDica: TextView
    private lateinit var tvArqTitulo: TextView
    private lateinit var tvArqSub: TextView
    private lateinit var boxCodigo: LinearLayout
    private lateinit var etCodigo: EditText
    private lateinit var etNome: EditText
    private lateinit var etPacote: EditText
    private lateinit var tvPacoteDica: TextView
    private lateinit var btnPacoteAuto: TextView
    private lateinit var tile: TileView
    private lateinit var tvPrevNome: TextView
    private lateinit var btnRemover: TextView
    private lateinit var tvPublic: TextView

    init {
        val col = LinearLayout(act)

        col.addView(ui.txt("Conversor Nuvem", 30f, p.ink, true))
        col.addView(ui.txt("Escolha o arquivo e receba o APK pronto para instalar.", 15f, p.muted).apply { setPadding(0, ui.dp(4), 0, 0) })

        // ---------- andamento ----------
        val c0 = ui.card(18)
        val topo = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = ui.lp() }
        topo.addView(ui.label("Andamento"), LinearLayout.LayoutParams(0, -2, 1f))
        tvPct = ui.txt("", 20f, p.accent, true)
        topo.addView(tvPct)
        c0.addView(topo)
        tvStatus = ui.txt("Pronto.", 18f, p.ink, true).apply { setPadding(0, ui.dp(8), 0, 0) }
        c0.addView(tvStatus)
        tvDetalhe = ui.txt("", 13f, p.muted).apply { setPadding(0, ui.dp(2), 0, 0); visibility = View.GONE }
        c0.addView(tvDetalhe)
        bar = BarView(act, p.line, p.accent).apply { layoutParams = ui.lp(14, -1, ui.dp(10)); visibility = View.GONE }
        c0.addView(bar)
        indet = BarView(act, p.line, p.accent).apply { layoutParams = ui.lp(14, -1, ui.dp(6)); visibility = View.GONE }
        c0.addView(indet)
        val trilha = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = ui.lp(12, -1, ui.dp(5)) }
        val ps = ArrayList<View>()
        for (i in 0 until 4) {
            val v = View(act).apply { background = ui.shape(p.line, 3) }
            trilha.addView(v, LinearLayout.LayoutParams(0, -1, 1f).apply { if (i > 0) leftMargin = ui.dp(4) })
            ps.add(v)
        }
        passos = ps
        c0.addView(trilha)
        val labs = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = ui.lp(6) }
        val rs = ArrayList<TextView>()
        for (i in 0 until 4) {
            val t = ui.txt(BuildState.labels[i], 12f, p.muted)
            labs.addView(t, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) leftMargin = ui.dp(4) })
            rs.add(t)
        }
        rotulos = rs
        c0.addView(labs)
        term = TerminalView(act, ui).apply { visibility = View.GONE }
        c0.addView(term)
        btnInstalar = ui.btn("Instalar APK", true) {
            val f = BuildState.lastApk
            if (f != null && f.exists()) Installer.install(act, f) else Toast.makeText(act, "Ainda não há APK.", Toast.LENGTH_SHORT).show()
        }
        btnShare = ui.btn("Compartilhar / abrir com...", false, true) {
            val f = BuildState.lastApk
            if (f != null && f.exists()) Installer.share(act, f, BuildState.lastNome)
        }
        btnSalvar = ui.btn("Salvar em Downloads", false, true) {
            val f = BuildState.lastApk
            if (f != null && f.exists()) Installer.saveToDownloads(act, f, BuildState.lastNome)
        }
        btnGit = ui.btn("Abrir no GitHub", false, true) { BuildState.lastLink?.let { act.abrirUrl(it) } }
        btnErro = ui.btn("Ver erro no GitHub", false, true) { BuildState.errLink?.let { act.abrirUrl(it) } }
        for (b in listOf(btnInstalar, btnShare, btnSalvar, btnGit, btnErro)) { b.visibility = View.GONE; c0.addView(b) }
        col.addView(c0)

        // ---------- linguagem e arquivo ----------
        val c1 = ui.card()
        c1.addView(ui.label("Linguagem"))
        val cs = ArrayList<TextView>()
        for ((i, t) in tipos.withIndex()) cs.add(ui.chip(t.second, i == tipoSel) { escolherTipo(i) })
        chips = cs
        c1.addView(ui.row(cs[0], cs[1]))
        c1.addView(ui.row(cs[2], cs[3]))
        tvDica = ui.txt("", 13f, p.muted).apply { setPadding(ui.dp(2), ui.dp(10), 0, 0) }
        c1.addView(tvDica)

        val caixa = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(ui.dp(16), ui.dp(22), ui.dp(16), ui.dp(22))
            background = ui.ripple(android.graphics.drawable.GradientDrawable().apply {
                setColor(p.bg); cornerRadius = ui.dp(14).toFloat(); setStroke(ui.dp(2), p.line, ui.dp(6).toFloat(), ui.dp(5).toFloat())
            })
            isClickable = true
            setOnClickListener { act.escolherArquivo() }
            layoutParams = ui.lp(16)
        }
        tvArqTitulo = ui.txt("Toque para escolher o arquivo", 16f, p.accent, true).apply { gravity = Gravity.CENTER }
        tvArqSub = ui.txt("Nenhum arquivo escolhido", 13f, p.muted).apply { gravity = Gravity.CENTER; setPadding(0, ui.dp(4), 0, 0) }
        caixa.addView(tvArqTitulo); caixa.addView(tvArqSub)
        c1.addView(caixa)

        boxCodigo = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; layoutParams = ui.lp() }
        boxCodigo.addView(ui.label("Ou cole o código HTML").apply { setPadding(0, ui.dp(18), 0, 0) })
        etCodigo = EditText(act).apply {
            hint = "<!DOCTYPE html>..."
            setHintTextColor(p.muted); setTextColor(p.ink); textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            background = ui.shape(p.bg, 12, p.line, 1)
            setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
            minLines = 5; gravity = Gravity.TOP or Gravity.START
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            layoutParams = ui.lp(8)
        }
        boxCodigo.addView(etCodigo)
        c1.addView(boxCodigo)
        col.addView(c1)

        // ---------- ícone, nome e pacote ----------
        val c2 = ui.card()
        c2.addView(ui.label("Ícone e nome do app"))
        val linha = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutParams = ui.lp(14) }
        val prev = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        tile = TileView(act, ui, 76, 22)
        tvPrevNome = ui.txt("Meu App", 13f, p.ink).apply {
            gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, ui.dp(8), 0, 0)
        }
        prev.addView(tile); prev.addView(tvPrevNome, LinearLayout.LayoutParams(ui.dp(96), -2))
        linha.addView(prev, LinearLayout.LayoutParams(ui.dp(96), -2))
        val ctl = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        ctl.addView(ui.btn("Escolher imagem", false, true, 0) { act.escolherImagem(11) })
        btnRemover = ui.btn("Remover imagem", false, true, 8) { iconeBytes = null; atualizarIcone() }
        btnRemover.visibility = View.GONE
        ctl.addView(btnRemover)
        ctl.addView(ui.txt("Imagem quadrada, com o desenho no centro. Sem imagem, o app usa a inicial do nome.", 13f, p.muted).apply { setPadding(0, ui.dp(8), 0, 0) })
        linha.addView(ctl, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = ui.dp(16) })
        c2.addView(linha)

        c2.addView(ui.label("Nome do app").apply { setPadding(0, ui.dp(18), 0, 0) })
        etNome = ui.field("Meu App", "Meu App")
        c2.addView(etNome)
        c2.addView(ui.txt("O nome e a imagem entram no APK automaticamente.", 13f, p.muted).apply { setPadding(ui.dp(2), ui.dp(8), 0, 0) })
        c2.addView(ui.label("ID do pacote (só na aba HTML)").apply { setPadding(0, ui.dp(14), 0, 0) })
        etPacote = ui.field("com.meuapp.app", "com.meuapp.app")
        etPacote.setTextColor(p.ink)
        c2.addView(etPacote)
        tvPacoteDica = ui.txt("Gerado automaticamente a partir do nome.", 13f, p.muted).apply { setPadding(ui.dp(2), ui.dp(8), 0, 0) }
        c2.addView(tvPacoteDica)
        btnPacoteAuto = ui.btn("Gerar de novo a partir do nome", false, true, 12) { pacoteManual = false; sincPacote() }
        btnPacoteAuto.visibility = View.GONE
        c2.addView(btnPacoteAuto)
        c2.addView(ui.btn("Compilar e instalar", true) { compilar() })
        tvPublic = ui.txt("", 13f, p.muted).apply { setPadding(ui.dp(2), ui.dp(10), 0, 0) }
        c2.addView(tvPublic)
        col.addView(c2)

        view = ui.screen(col)

        etNome.onChange { n -> tvPrevNome.text = n.trim().ifBlank { "Meu App" }; atualizarIcone(); sincPacote() }
        etPacote.onChange { v ->
            if (etPacote.hasFocus()) {
                val t = v.trim()
                if (t.isEmpty()) { pacoteManual = false; sincPacote() }
                else { pacoteManual = t != Util.gerarPacote(etNome.text.toString()); dicaPacote() }
            }
        }
        escolherTipo(1)
        atualizarIcone()
        sincPacote()
        refresh()
        restaurarTerminal()
    }

    // ---------- estado da tela ----------

    private fun escolherTipo(i: Int) {
        tipoSel = i
        for ((k, c) in chips.withIndex()) ui.mark(c, k == i)
        tvDica.text = tipos[i].third
        boxCodigo.visibility = if (tipos[i].first == "html") View.VISIBLE else View.GONE
        fonteUri = null; fonteNome = ""; fonteTam = 0
        tvArqTitulo.text = "Toque para escolher o arquivo"; tvArqSub.text = "Nenhum arquivo escolhido"
    }

    private fun atualizarIcone() {
        tile.set(iconeBytes, etNome.text.toString())
        btnRemover.visibility = if (iconeBytes != null) View.VISIBLE else View.GONE
    }

    private fun dicaPacote() {
        tvPacoteDica.text = if (pacoteManual) "Você editou o ID. O nome não muda mais o ID." else "Gerado automaticamente a partir do nome."
        btnPacoteAuto.visibility = if (pacoteManual) View.VISIBLE else View.GONE
    }

    private fun sincPacote() {
        if (pacoteManual) return
        val novo = Util.gerarPacote(etNome.text.toString())
        if (etPacote.text.toString() != novo) etPacote.setText(novo)
        dicaPacote()
    }

    fun onFile(uri: Uri) {
        var nome = "projeto"; var tam = 0L
        act.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); val s = c.getColumnIndex(OpenableColumns.SIZE)
            if (c.moveToFirst()) { if (i >= 0) nome = c.getString(i); if (s >= 0 && !c.isNull(s)) tam = c.getLong(s) }
        }
        fonteUri = uri; fonteNome = nome; fonteTam = tam
        tvArqTitulo.text = nome
        tvArqSub.text = (if (tam > 0) Util.tamanho(tam) + " · " else "") + "toque para trocar"
    }

    fun onIcon(uri: Uri) {
        val png = IconMaker.fromUri(act, uri)
        if (png == null) { Toast.makeText(act, "Não consegui abrir essa imagem.", Toast.LENGTH_LONG).show(); return }
        iconeBytes = png
        atualizarIcone()
    }

    // ---------- compilar ----------

    private fun compilar() {
        if (BuildState.running) { Toast.makeText(act, "Aguarde a compilação atual terminar.", Toast.LENGTH_SHORT).show(); return }
        val tipo = tipos[tipoSel].first
        val colado = if (tipo == "html") etCodigo.text.toString().trim() else ""
        if (fonteUri == null && colado.isEmpty()) {
            Toast.makeText(act, if (tipo == "html") "Escolha um arquivo ou cole o código." else "Escolha um arquivo primeiro.", Toast.LENGTH_LONG).show(); return
        }
        val nome = etNome.text.toString().trim().ifBlank { "Meu App" }
        val pacote = etPacote.text.toString().trim().ifBlank { "com.meuapp.app" }
        if (!Util.RE_PACOTE.matches(pacote)) { Toast.makeText(act, "ID do pacote inválido. Use algo como com.meuapp.app", Toast.LENGTH_LONG).show(); return }
        if (Runner.usaGitHub(tipo) && !Prefs.githubOk()) {
            Toast.makeText(act, "Esse tipo compila pelo GitHub. Preencha as configurações.", Toast.LENGTH_LONG).show()
            act.irPara(2); return
        }
        val a = AppRec(Util.novoId("a"), tipo, System.currentTimeMillis())
        a.nome = nome; a.pacote = pacote; a.pacoteManual = pacoteManual
        a.arquivoNome = if (fonteUri != null) fonteNome else "colado.html"
        a.arquivoTam = if (fonteUri != null) fonteTam else colado.length.toLong()
        try {
            val uri = fonteUri
            if (uri != null) {
                act.contentResolver.openInputStream(uri)?.use { i -> Store.sourceFile(a).outputStream().use { o -> i.copyTo(o) } }
                val f = Store.sourceFile(a)
                if (tipo == "html" && !fonteNome.lowercase().endsWith(".zip") && f.length() <= 400 * 1024) {
                    Store.codigoFile(a).writeText(f.readText())
                }
            } else Store.codigoFile(a).writeText(colado)
            iconeBytes?.let { Store.iconFile(a).writeBytes(it) }
        } catch (e: Exception) {
            Toast.makeText(act, "Não consegui guardar o arquivo: ${e.message}", Toast.LENGTH_LONG).show(); return
        }
        Store.apps.add(0, a)
        Runner.start(act, a)
        view.post { view.smoothScrollTo(0, 0) }
    }

    // ---------- andamento ----------

    private fun restaurarTerminal() {
        if (BuildState.lines.isNotEmpty()) { term.visibility = View.VISIBLE; term.restore(BuildState.lines) }
    }

    fun onClear() { term.clear(); term.visibility = View.VISIBLE }
    fun onLine(t: String, c: Int) { term.visibility = View.VISIBLE; term.add(t, c) }

    fun refresh() {
        val s = BuildState
        tvStatus.text = s.status
        tvStatus.setTextColor(if (s.erro) p.err else if (s.ok) p.ok else p.ink)
        tvDetalhe.text = s.detalhe
        tvDetalhe.visibility = if (s.detalhe.isEmpty() || !s.running) View.GONE else View.VISIBLE
        val temPct = s.pct >= 0f && (s.running || s.ok || s.erro)
        tvPct.text = if (temPct) "${s.pct.toInt()}%" else ""
        tvPct.setTextColor(if (s.erro) p.err else if (s.ok) p.ok else p.accent)
        bar.visibility = if (temPct) View.VISIBLE else View.GONE
        if (temPct) bar.setValue(s.pct)
        indet.visibility = View.GONE
        val cor = if (s.erro) p.err else if (s.ok) p.ok else p.accent
        val fim = s.ok || s.erro
        for ((i, v) in passos.withIndex()) {
            val ativo = fim || i < s.etapa
            (v.background as android.graphics.drawable.GradientDrawable).setColor(if (ativo) cor else p.line)
            rotulos[i].text = s.labels[i]
            rotulos[i].setTextColor(if (!fim && i == s.etapa - 1) p.accent else p.muted)
        }
        btnInstalar.visibility = if (s.ok && s.lastApk != null) View.VISIBLE else View.GONE
        btnSalvar.visibility = btnInstalar.visibility
        btnShare.visibility = btnInstalar.visibility
        btnGit.visibility = if (s.ok && !s.lastLink.isNullOrEmpty()) View.VISIBLE else View.GONE
        btnErro.visibility = if (s.erro && s.errLink != null) View.VISIBLE else View.GONE
        tvPublic.text = if (tipos[tipoSel].first == "html" && !Prefs.htmlViaGithub)
            "HTML: motor nativo (sem GitHub e sem internet)." else "Esse tipo compila pelo GitHub."
    }
}
