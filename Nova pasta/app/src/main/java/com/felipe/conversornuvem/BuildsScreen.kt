package com.felipe.conversornuvem

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class BuildsScreen(private val act: MainActivity, private val ui: Ui) {
    private val p = ui.p
    val view = FrameLayout(act)
    private val nomesTipo = mapOf("kotlin" to "Kotlin/Java", "html" to "HTML", "flutter" to "Flutter", "reactnative" to "React Native")

    // ---------- lista ----------
    private val listaScroll: android.widget.ScrollView
    private val listBox = LinearLayout(act)
    private lateinit var etBusca: EditText
    private lateinit var btnFiltros: TextView
    private lateinit var filtrosBox: LinearLayout
    private lateinit var tvContagem: TextView
    private var fOrdem = "recentes"
    private var fSit = "todos"
    private var fLing = "todas"
    private val chipsOrdem = LinkedHashMap<String, TextView>()
    private val chipsSit = LinkedHashMap<String, TextView>()
    private val chipsLing = LinkedHashMap<String, TextView>()

    // ---------- detalhe ----------
    private val detalheScroll: android.widget.ScrollView
    private var atual: AppRec? = null
    private val salvarH = Handler(Looper.getMainLooper())
    private val salvarRun = Runnable { salvarAtual() }
    private lateinit var tvTitulo: TextView
    private lateinit var tvSub: TextView
    private lateinit var tile: TileView
    private lateinit var tvPrevNome: TextView
    private lateinit var btnRemover: TextView
    private lateinit var etNome: EditText
    private lateinit var etPacote: EditText
    private lateinit var tvPacoteDica: TextView
    private lateinit var btnPacoteAuto: TextView
    private lateinit var tvSalvo: TextView
    private lateinit var tvArq: TextView
    private lateinit var etCodigo: EditText
    private lateinit var boxCodigo: LinearLayout
    private lateinit var buildsBox: LinearLayout

    val detalheAberto get() = atual != null

    init {
        val col = LinearLayout(act)
        col.addView(ui.txt("Compilações", 30f, p.ink, true))
        col.addView(ui.txt("Seus apps e as últimas compilações.", 15f, p.muted).apply { setPadding(0, ui.dp(4), 0, 0) })

        val busca = ui.card(18)
        busca.addView(ui.label("Pesquisar"))
        etBusca = ui.field("Nome do app", "")
        etBusca.isSingleLine = true
        busca.addView(etBusca)
        btnFiltros = ui.btn("Filtros", false, true, 12) { alternarFiltros() }
        busca.addView(btnFiltros)
        filtrosBox = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }

        filtrosBox.addView(ui.label("Ordem").apply { setPadding(0, ui.dp(16), 0, 0) })
        val o1 = grupo(chipsOrdem, "recentes" to "Do último para o primeiro", "antigos" to "Do primeiro para o último") { fOrdem = it }
        filtrosBox.addView(ui.row(o1[0], o1[1]))
        filtrosBox.addView(ui.label("Situação").apply { setPadding(0, ui.dp(16), 0, 0) })
        val s1 = grupo(chipsSit, "todos" to "Todos", "ok" to "Prontos", "erro" to "Com erro") { fSit = it }
        filtrosBox.addView(ui.row(s1[0], s1[1], s1[2]))
        filtrosBox.addView(ui.label("Linguagem").apply { setPadding(0, ui.dp(16), 0, 0) })
        val l1 = grupo(chipsLing, "todas" to "Todas", "kotlin" to "Kotlin / Java", "html" to "HTML", "flutter" to "Flutter", "reactnative" to "React Native") { fLing = it }
        filtrosBox.addView(ui.row(l1[0], l1[1], l1[2]))
        filtrosBox.addView(ui.row(l1[3], l1[4]))
        filtrosBox.addView(ui.btn("Redefinir filtros", false, true, 12) {
            fOrdem = "recentes"; fSit = "todos"; fLing = "todas"; marcarGrupos(); redraw()
        })
        busca.addView(filtrosBox)
        col.addView(busca)

        tvContagem = ui.txt("0 apps", 13f, p.muted).apply { setPadding(ui.dp(2), ui.dp(16), 0, 0) }
        col.addView(tvContagem)
        listBox.orientation = LinearLayout.VERTICAL
        col.addView(listBox)
        listaScroll = ui.screen(col)
        etBusca.onChange { redraw() }

        detalheScroll = ui.screen(montarDetalhe())
        detalheScroll.visibility = View.GONE
        view.addView(listaScroll, FrameLayout.LayoutParams(-1, -1))
        view.addView(detalheScroll, FrameLayout.LayoutParams(-1, -1))
        redraw()
    }

    private fun grupo(mapa: LinkedHashMap<String, TextView>, vararg itens: Pair<String, String>, set: (String) -> Unit): List<TextView> {
        val out = ArrayList<TextView>()
        for ((k, t) in itens) {
            val c = ui.chip(t, false) { set(k); marcarGrupos(); redraw() }
            c.textSize = 14f; c.minHeight = ui.dp(44)
            mapa[k] = c; out.add(c)
        }
        return out
    }

    private fun marcarGrupos() {
        for ((k, c) in chipsOrdem) ui.mark(c, k == fOrdem)
        for ((k, c) in chipsSit) ui.mark(c, k == fSit)
        for ((k, c) in chipsLing) ui.mark(c, k == fLing)
    }

    private fun alternarFiltros() {
        filtrosBox.visibility = if (filtrosBox.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun ultimaTs(a: AppRec) = a.builds.firstOrNull()?.ts ?: a.criado

    private fun situacao(a: AppRec): Pair<String, Int> {
        val b = a.builds.firstOrNull() ?: return Pair("Sem compilação", p.muted)
        return when {
            b.ok == 1 -> Pair("Pronto", p.ok)
            b.ok == -1 -> Pair("Falhou", p.err)
            BuildState.running && BuildState.buildId == b.id -> Pair("Compilando...", p.accent)
            else -> Pair("Interrompida", p.muted)
        }
    }

    fun redraw() {
        marcarGrupos()
        val q = etBusca.text.toString().trim().lowercase()
        val lista = Store.apps.filter { a ->
            if (q.isNotEmpty() && "${a.nome} ${a.pacote} ${nomesTipo[a.tipo]}".lowercase().indexOf(q) < 0) return@filter false
            if (fLing != "todas" && a.tipo != fLing) return@filter false
            val ok = a.builds.firstOrNull()?.ok
            if (fSit == "ok" && ok != 1) return@filter false
            if (fSit == "erro" && ok != -1) return@filter false
            true
        }.sortedBy { if (fOrdem == "recentes") -ultimaTs(it) else ultimaTs(it) }

        val nf = (if (fOrdem != "recentes") 1 else 0) + (if (fSit != "todos") 1 else 0) + (if (fLing != "todas") 1 else 0)
        btnFiltros.text = if (nf > 0) "Filtros ($nf)" else "Filtros"
        listBox.removeAllViews()
        if (Store.apps.isEmpty()) {
            listBox.addView(vazio("Nenhum app ainda. Compile um pelo Início e ele fica guardado aqui."))
            tvContagem.text = "0 apps"; return
        }
        if (lista.isEmpty()) {
            listBox.addView(vazio("Nenhum app encontrado com essa pesquisa ou esses filtros."))
            tvContagem.text = "0 de ${Store.apps.size} apps"; return
        }
        for (a in lista) listBox.addView(item(a))
        tvContagem.text = (if (lista.size == Store.apps.size) "${lista.size}" else "${lista.size} de ${Store.apps.size}") + (if (Store.apps.size == 1) " app" else " apps")
    }

    private fun vazio(t: String) = ui.txt(t, 14f, p.muted).apply {
        gravity = Gravity.CENTER
        setPadding(ui.dp(16), ui.dp(22), ui.dp(16), ui.dp(22))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.TRANSPARENT); cornerRadius = ui.dp(18).toFloat(); setStroke(ui.dp(2), p.line, ui.dp(6).toFloat(), ui.dp(5).toFloat())
        }
        layoutParams = ui.lp(8)
    }

    private fun item(a: AppRec): View {
        val row = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
            background = ui.ripple(ui.shape(p.card, 18, p.line, 1))
            isClickable = true
            layoutParams = ui.lp(10)
            setOnClickListener { abrir(a) }
        }
        val t = TileView(act, ui, 52, 15)
        t.set(Store.iconFile(a).let { if (it.exists()) it.readBytes() else null }, a.nome)
        row.addView(t)
        val info = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        info.addView(ui.txt(a.nome, 16f, p.ink, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        info.addView(ui.txt("${nomesTipo[a.tipo]} · ${a.pacote}", 13f, p.muted).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        val (st, cor) = situacao(a)
        val n = a.builds.size
        info.addView(ui.txt("$st · ${Util.fmtData(ultimaTs(a))} · $n ${if (n == 1) "compilação" else "compilações"}", 13f, cor).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        })
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = ui.dp(14) })
        row.addView(ui.txt("›", 26f, p.muted))
        return row
    }

    // ---------- tela do app ----------

    private fun montarDetalhe(): LinearLayout {
        val col = LinearLayout(act)
        val voltar = ui.txt("‹  Compilações", 15f, p.accent, true).apply {
            setPadding(0, ui.dp(4), ui.dp(16), ui.dp(12)); isClickable = true; setOnClickListener { fechar() }
        }
        col.addView(voltar)
        tvTitulo = ui.txt("App", 30f, p.ink, true)
        tvSub = ui.txt("", 15f, p.muted).apply { setPadding(0, ui.dp(4), 0, 0) }
        col.addView(tvTitulo); col.addView(tvSub)

        val c1 = ui.card(18)
        c1.addView(ui.label("Ícone e nome"))
        val linha = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutParams = ui.lp(14) }
        val prev = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        tile = TileView(act, ui, 76, 22)
        tvPrevNome = ui.txt("", 13f, p.ink).apply { gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, ui.dp(8), 0, 0) }
        prev.addView(tile); prev.addView(tvPrevNome, LinearLayout.LayoutParams(ui.dp(96), -2))
        linha.addView(prev, LinearLayout.LayoutParams(ui.dp(96), -2))
        val ctl = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        ctl.addView(ui.btn("Trocar imagem", false, true, 0) { act.escolherImagem(12) })
        btnRemover = ui.btn("Remover imagem", false, true, 8) {
            val a = atual
            if (a != null) { Store.iconFile(a).delete(); atualizarIcone(); avisarSalvo() }
        }
        ctl.addView(btnRemover)
        ctl.addView(ui.txt("Imagem quadrada, com o desenho no centro.", 13f, p.muted).apply { setPadding(0, ui.dp(8), 0, 0) })
        linha.addView(ctl, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = ui.dp(16) })
        c1.addView(linha)
        c1.addView(ui.label("Nome do app").apply { setPadding(0, ui.dp(18), 0, 0) })
        etNome = ui.field("", "")
        c1.addView(etNome)
        c1.addView(ui.label("ID do pacote (só na aba HTML)").apply { setPadding(0, ui.dp(14), 0, 0) })
        etPacote = ui.field("", "")
        c1.addView(etPacote)
        tvPacoteDica = ui.txt("", 13f, p.muted).apply { setPadding(ui.dp(2), ui.dp(8), 0, 0) }
        c1.addView(tvPacoteDica)
        btnPacoteAuto = ui.btn("Gerar de novo a partir do nome", false, true, 12) {
            val a = atual
            if (a != null) { a.pacoteManual = false; sincPacote(); agendar() }
        }
        c1.addView(btnPacoteAuto)
        tvSalvo = ui.txt("", 13f, p.muted).apply { setPadding(ui.dp(2), ui.dp(12), 0, 0) }
        c1.addView(tvSalvo)
        col.addView(c1)

        val c2 = ui.card()
        c2.addView(ui.label("Arquivo"))
        tvArq = ui.txt("", 13f, p.muted).apply { setPadding(0, ui.dp(6), 0, 0) }
        c2.addView(tvArq)
        boxCodigo = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; layoutParams = ui.lp() }
        boxCodigo.addView(ui.label("Código HTML (você pode editar)").apply { setPadding(0, ui.dp(14), 0, 0) })
        etCodigo = EditText(act).apply {
            setTextColor(p.ink); textSize = 12f; typeface = android.graphics.Typeface.MONOSPACE
            background = ui.shape(p.bg, 12, p.line, 1)
            setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
            minLines = 12; gravity = Gravity.TOP or Gravity.START
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            layoutParams = ui.lp(8)
        }
        boxCodigo.addView(etCodigo)
        c2.addView(boxCodigo)
        col.addView(c2)

        val c3 = ui.card()
        c3.addView(ui.label("Compilações deste app"))
        buildsBox = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; layoutParams = ui.lp(6) }
        c3.addView(buildsBox)
        col.addView(c3)
        col.addView(ui.btn("Compilar de novo com essas alterações", true, false, 16) { recompilar() })

        etNome.onChange { n ->
            if (atual != null && etNome.hasFocus()) { tvPrevNome.text = n.trim().ifBlank { "Meu App" }; atualizarIcone(); sincPacote(); agendar() }
        }
        etPacote.onChange { v ->
            val a = atual
            if (a != null && etPacote.hasFocus()) {
                val t = v.trim()
                if (t.isEmpty()) { a.pacoteManual = false; sincPacote() }
                else { a.pacoteManual = t != Util.gerarPacote(etNome.text.toString()); dicaPacote() }
                agendar()
            }
        }
        etCodigo.onChange { if (atual != null && etCodigo.hasFocus()) agendar() }
        return col
    }

    private fun dicaPacote() {
        val a = atual ?: return
        tvPacoteDica.text = if (a.pacoteManual) "Você editou o ID. O nome não muda mais o ID."
        else "Gerado automaticamente a partir do nome. Mudar o ID faz o Android tratar o app como outro, instalado ao lado do antigo."
        btnPacoteAuto.visibility = if (a.pacoteManual) View.VISIBLE else View.GONE
    }

    private fun sincPacote() {
        val a = atual ?: return
        if (a.pacoteManual) return
        val novo = Util.gerarPacote(etNome.text.toString())
        if (etPacote.text.toString() != novo) etPacote.setText(novo)
        dicaPacote()
    }

    private fun atualizarIcone() {
        val a = atual ?: return
        val f = Store.iconFile(a)
        tile.set(if (f.exists()) f.readBytes() else null, etNome.text.toString())
        btnRemover.visibility = if (f.exists()) View.VISIBLE else View.GONE
    }

    private fun agendar() { salvarH.removeCallbacks(salvarRun); salvarH.postDelayed(salvarRun, 600) }

    private fun avisarSalvo() { tvSalvo.text = "Alterações salvas neste aparelho às ${java.text.SimpleDateFormat("HH:mm").format(java.util.Date())}." }

    private fun salvarAtual() {
        val a = atual ?: return
        a.nome = etNome.text.toString().trim().ifBlank { "Meu App" }
        a.pacote = etPacote.text.toString().trim().ifBlank { "com.meuapp.app" }
        a.atualizado = System.currentTimeMillis()
        if (boxCodigo.visibility == View.VISIBLE) Store.codigoFile(a).writeText(etCodigo.text.toString())
        tvTitulo.text = a.nome
        Store.save()
        avisarSalvo()
    }

    private fun desenharBuilds() {
        val a = atual ?: return
        buildsBox.removeAllViews()
        if (a.builds.isEmpty()) { buildsBox.addView(ui.txt("Nenhuma compilação ainda.", 14f, p.muted)); return }
        for (b in a.builds) {
            val linha = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, ui.dp(10), 0, ui.dp(10))
            }
            val (txt, cor) = when {
                b.ok == 1 -> Pair("Pronto", p.ok)
                b.ok == -1 -> Pair("Falhou", p.err)
                BuildState.running && BuildState.buildId == b.id -> Pair("Compilando...", p.accent)
                else -> Pair("Interrompida", p.muted)
            }
            val info = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
            info.addView(ui.txt(txt, 15f, cor, true))
            info.addView(ui.txt("${Util.fmtData(b.ts)} · ${if (b.via == "nativo") "motor nativo" else "GitHub"}", 12f, p.muted))
            linha.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            val apk = Store.apkFile(a, b)
            if (b.ok == 1 && apk.exists()) {
                linha.addView(ui.txt("Instalar", 14f, p.accent, true).apply {
                    setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8)); isClickable = true
                    setOnClickListener { Installer.install(act, apk) }
                })
                linha.addView(ui.txt("Enviar", 14f, p.accent, true).apply {
                    setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8)); isClickable = true
                    setOnClickListener { Installer.share(act, apk, a.nome) }
                })
                linha.addView(ui.txt("Baixar", 14f, p.accent, true).apply {
                    setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8)); isClickable = true
                    setOnClickListener { Installer.saveToDownloads(act, apk, a.nome) }
                })
            }
            val link = b.link
            if (!link.isNullOrEmpty()) linha.addView(ui.txt("GitHub", 14f, p.accent, true).apply {
                setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8)); isClickable = true
                setOnClickListener { act.abrirUrl(link) }
            })
            buildsBox.addView(linha)
        }
    }

    fun abrir(a: AppRec) {
        atual = a
        tvTitulo.text = a.nome
        tvSub.text = "${nomesTipo[a.tipo]} · criado em ${Util.fmtData(a.criado)}"
        etNome.setText(a.nome); etPacote.setText(a.pacote)
        tvPrevNome.text = a.nome
        dicaPacote()
        atualizarIcone()
        val cod = Store.codigoFile(a)
        boxCodigo.visibility = if (cod.exists()) View.VISIBLE else View.GONE
        etCodigo.setText(if (cod.exists()) cod.readText() else "")
        tvArq.text = (if (a.arquivoNome.isNotEmpty()) a.arquivoNome else "sem arquivo") +
            (if (a.arquivoTam > 0) " · ${Util.tamanho(a.arquivoTam)}" else "") +
            (if (cod.exists()) " · o código está abaixo e pode ser editado." else " · projeto compactado: não dá para editar aqui. Para mudar o código, escolha o novo .zip no Início.")
        tvSalvo.text = ""
        desenharBuilds()
        listaScroll.visibility = View.GONE
        detalheScroll.visibility = View.VISIBLE
        detalheScroll.scrollTo(0, 0)
    }

    fun fechar() {
        if (atual == null) return
        salvarH.removeCallbacks(salvarRun)
        salvarAtual()
        atual = null
        detalheScroll.visibility = View.GONE
        listaScroll.visibility = View.VISIBLE
        redraw()
    }

    fun onIcon(uri: Uri) {
        val a = atual ?: return
        val png = IconMaker.fromUri(act, uri)
        if (png == null) { Toast.makeText(act, "Não consegui abrir essa imagem.", Toast.LENGTH_LONG).show(); return }
        Store.iconFile(a).writeBytes(png)
        atualizarIcone(); avisarSalvo()
    }

    private fun recompilar() {
        val a = atual ?: return
        if (BuildState.running) { Toast.makeText(act, "Aguarde a compilação atual terminar.", Toast.LENGTH_SHORT).show(); return }
        salvarH.removeCallbacks(salvarRun); salvarAtual()
        if (!Util.RE_PACOTE.matches(a.pacote)) { Toast.makeText(act, "ID do pacote inválido. Use algo como com.meuapp.app", Toast.LENGTH_LONG).show(); return }
        if (!Store.codigoFile(a).exists() && !Store.sourceFile(a).exists()) {
            Toast.makeText(act, "Esse app não tem arquivo guardado. Compile de novo pelo Início.", Toast.LENGTH_LONG).show(); return
        }
        if (Runner.usaGitHub(a.tipo) && !Prefs.githubOk()) {
            Toast.makeText(act, "Esse tipo compila pelo GitHub. Preencha as configurações.", Toast.LENGTH_LONG).show()
            act.irPara(2); return
        }
        Runner.start(act, a)
        act.irPara(0)
    }

    fun onBuildChange() { if (atual != null) desenharBuilds() else if (view.visibility == View.VISIBLE) redraw() }
}
