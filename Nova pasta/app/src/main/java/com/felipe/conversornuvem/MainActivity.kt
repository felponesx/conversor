package com.felipe.conversornuvem

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Ícones da barra de atalhos, desenhados em uma grade de 24 x 24. */
class TabIcon(ctx: android.content.Context, private val kind: Int) : View(ctx) {
    var cor = Color.GRAY
        set(v) { field = v; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = 2f
    }

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height) / 24f
        c.save()
        c.translate((width - 24 * s) / 2f, (height - 24 * s) / 2f)
        c.scale(s, s)
        paint.color = cor
        when (kind) {
            0 -> {
                val roof = Path().apply { moveTo(4f, 11f); lineTo(12f, 4f); lineTo(20f, 11f) }
                val wall = Path().apply { moveTo(6f, 10f); lineTo(6f, 19f); quadTo(6f, 20f, 7f, 20f); lineTo(17f, 20f); quadTo(18f, 20f, 18f, 19f); lineTo(18f, 10f) }
                val door = Path().apply { moveTo(10f, 20f); lineTo(10f, 15f); lineTo(14f, 15f); lineTo(14f, 20f) }
                c.drawPath(roof, paint); c.drawPath(wall, paint); c.drawPath(door, paint)
            }
            1 -> {
                c.drawRoundRect(RectF(7f, 3f, 21f, 8f), 2.5f, 2.5f, paint)
                c.drawRoundRect(RectF(3f, 9.5f, 21f, 14.5f), 2.5f, 2.5f, paint)
                c.drawRoundRect(RectF(3f, 16f, 17f, 21f), 2.5f, 2.5f, paint)
                c.drawLine(10.5f, 5.5f, 16.5f, 5.5f, paint)
                c.drawLine(6.5f, 12f, 14.5f, 12f, paint)
                c.drawLine(6.5f, 18.5f, 11.5f, 18.5f, paint)
            }
            else -> {
                c.drawLine(4f, 7f, 13f, 7f, paint); c.drawLine(17f, 7f, 20f, 7f, paint)
                c.drawLine(4f, 12f, 7f, 12f, paint); c.drawLine(11f, 12f, 20f, 12f, paint)
                c.drawLine(4f, 17f, 15f, 17f, paint); c.drawLine(19f, 17f, 20f, 17f, paint)
                c.drawCircle(15f, 7f, 2f, paint); c.drawCircle(9f, 12f, 2f, paint); c.drawCircle(17f, 17f, 2f, paint)
            }
        }
        c.restore()
    }
}

class MainActivity : Activity() {
    lateinit var ui: Ui
    lateinit var home: HomeScreen
    lateinit var builds: BuildsScreen
    lateinit var settings: SettingsScreen
    private lateinit var telas: List<View>
    private val tabIcons = ArrayList<TabIcon>()
    private val tabPills = ArrayList<LinearLayout>()
    private val tabLabels = ArrayList<TextView>()
    private lateinit var dot: View
    private lateinit var topo: BarView
    private var aba = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val night = when (Prefs.tema) {
            "dark" -> true
            "light" -> false
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        val p = Pal(night)
        ui = Ui(this, p)
        window.statusBarColor = p.bg
        window.navigationBarColor = p.card
        @Suppress("DEPRECATION")
        if (!night) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        home = HomeScreen(this, ui)
        builds = BuildsScreen(this, ui)
        settings = SettingsScreen(this, ui)
        telas = listOf(home.view, builds.view, settings.view)

        val raiz = FrameLayout(this)
        raiz.setBackgroundColor(p.bg)
        val coluna = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val conteudo = FrameLayout(this)
        for (t in telas) conteudo.addView(t, FrameLayout.LayoutParams(-1, -1))
        coluna.addView(conteudo, LinearLayout.LayoutParams(-1, 0, 1f))
        coluna.addView(barraDeAtalhos(p))
        raiz.addView(coluna, FrameLayout.LayoutParams(-1, -1))
        topo = BarView(this, p.line, p.accent).apply { visibility = View.GONE }
        raiz.addView(topo, FrameLayout.LayoutParams(-1, ui.dp(4)))
        setContentView(raiz)
        irPara(0)
    }

    private fun barraDeAtalhos(p: Pal): View {
        val barra = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val linha1 = View(this).apply { setBackgroundColor(p.line) }
        barra.addView(linha1, LinearLayout.LayoutParams(-1, 1))
        val itens = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(p.card)
            setPadding(ui.dp(8), ui.dp(6), ui.dp(8), ui.dp(6))
        }
        val nomes = listOf("Início", "Compilações", "Configurações")
        for (i in 0 until 3) {
            val item = FrameLayout(this)
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; minimumHeight = ui.dp(60) }
            val pill = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, ui.dp(3), 0, ui.dp(3)) }
            val icon = TabIcon(this, i)
            pill.addView(icon, LinearLayout.LayoutParams(ui.dp(58), ui.dp(24)))
            val label = ui.txt(nomes[i], 12f, p.muted)
            col.addView(pill, LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(4) })
            col.addView(label, LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(2) })
            item.addView(col, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
            if (i == 1) {
                dot = View(this).apply { background = ui.shape(p.accent, 6, p.card, 2); visibility = View.GONE }
                item.addView(dot, FrameLayout.LayoutParams(ui.dp(12), ui.dp(12), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { leftMargin = ui.dp(26); topMargin = ui.dp(6) })
            }
            item.isClickable = true
            item.setOnClickListener { if (i == 1 && aba == 1) builds.fechar(); irPara(i) }
            tabIcons.add(icon); tabPills.add(pill); tabLabels.add(label)
            itens.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
        }
        barra.addView(itens)
        return barra
    }

    fun irPara(i: Int) {
        aba = i
        for ((k, t) in telas.withIndex()) t.visibility = if (k == i) View.VISIBLE else View.GONE
        val p = ui.p
        for (k in 0 until 3) {
            val sel = k == i
            tabIcons[k].cor = if (sel) p.accent else p.muted
            tabLabels[k].setTextColor(if (sel) p.accent else p.muted)
            tabLabels[k].typeface = android.graphics.Typeface.create("sans-serif-medium", if (sel) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            tabPills[k].background = if (sel) ui.shape(p.soft, 15) else null
        }
        if (i == 0) home.view.post { home.view.smoothScrollTo(0, 0) }
        if (i == 1) builds.redraw()
    }

    private fun atualizarChrome() {
        dot.visibility = if (BuildState.running) View.VISIBLE else View.GONE
        topo.visibility = if (BuildState.running && BuildState.pct >= 0f) View.VISIBLE else View.GONE
        topo.setValue(BuildState.pct)
    }

    override fun onResume() {
        super.onResume()
        BuildState.onChange = { home.refresh(); builds.onBuildChange(); atualizarChrome() }
        BuildState.onLine = { t, c -> home.onLine(t, c) }
        BuildState.onClear = { home.onClear() }
        home.refresh(); atualizarChrome()
        Runner.resume(this)
    }

    override fun onPause() {
        BuildState.onChange = null; BuildState.onLine = null; BuildState.onClear = null
        super.onPause()
    }

    // ---------- arquivos e links ----------

    fun escolherArquivo() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 10)
    }

    fun escolherImagem(codigo: Int) {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), codigo)
    }

    fun abrirUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { Toast.makeText(this, "Não consegui abrir o link.", Toast.LENGTH_SHORT).show() }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            10 -> home.onFile(uri)
            11 -> home.onIcon(uri)
            12 -> builds.onIcon(uri)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (aba == 1 && builds.detalheAberto) builds.fechar()
        else if (aba != 0) irPara(0)
        else super.onBackPressed()
    }
}
