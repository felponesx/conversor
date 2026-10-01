package com.felipe.conversornuvem

import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class SettingsScreen(private val act: MainActivity, private val ui: Ui) {
    private val p = ui.p
    val view: ScrollView
    private lateinit var tvResumo: TextView
    private lateinit var tvStatus: TextView
    private lateinit var etToken: EditText
    private lateinit var etUser: EditText
    private lateinit var etRepo: EditText
    private val motor = ArrayList<TextView>()
    private val temas = LinkedHashMap<String, TextView>()
    private val apos = ArrayList<TextView>()
    private var ocupado = false

    init {
        val col = LinearLayout(act)
        col.addView(ui.txt("Configurações", 30f, p.ink, true))
        col.addView(ui.txt("Conecte o GitHub e ajuste o app.", 15f, p.muted).apply { setPadding(0, ui.dp(4), 0, 0) })

        val c1 = ui.card(18)
        c1.addView(ui.label("GitHub"))
        tvResumo = ui.txt("", 13f, p.muted).apply { setPadding(0, ui.dp(6), 0, 0) }
        c1.addView(tvResumo)
        c1.addView(ui.txt("O GitHub só é usado para Kotlin, Flutter e React Native. O HTML converte aqui no celular.", 13f, p.muted).apply { setPadding(0, ui.dp(6), 0, 0) })
        c1.addView(ui.label("Token do GitHub").apply { setPadding(0, ui.dp(14), 0, 0) })
        etToken = ui.field("ghp_...", Prefs.token, true); c1.addView(etToken)
        c1.addView(ui.label("Usuário").apply { setPadding(0, ui.dp(14), 0, 0) })
        etUser = ui.field("seu-usuario", Prefs.user); c1.addView(etUser)
        c1.addView(ui.label("Repositório").apply { setPadding(0, ui.dp(14), 0, 0) })
        etRepo = ui.field("compilador", Prefs.repo); c1.addView(etRepo)
        c1.addView(ui.btn("Salvar e instalar o compilador", true) { salvar() })
        tvStatus = ui.txt("", 14f, p.muted, true).apply { setPadding(ui.dp(2), ui.dp(10), 0, 0) }
        c1.addView(tvStatus)
        col.addView(c1)

        val c2 = ui.card()
        c2.addView(ui.label("Motor do HTML"))
        val m1 = ui.chip("Nativo (no celular)", !Prefs.htmlViaGithub) { escolherMotor(false) }
        val m2 = ui.chip("Pelo GitHub", Prefs.htmlViaGithub) { escolherMotor(true) }
        motor.add(m1); motor.add(m2)
        c2.addView(ui.row(m1, m2))
        c2.addView(ui.txt("Nativo: monta e assina o APK aqui, sem internet. Use o GitHub só se o nativo não instalar no seu aparelho.", 13f, p.muted).apply { setPadding(ui.dp(2), ui.dp(10), 0, 0) })
        col.addView(c2)

        val cA = ui.card()
        cA.addView(ui.label("Quando o APK ficar pronto"))
        val x1 = ui.chip("Janela de compartilhar", Prefs.aposCriar == "share") { escolherApos("share") }
        val x2 = ui.chip("Instalar direto", Prefs.aposCriar != "share") { escolherApos("install") }
        apos.add(x1); apos.add(x2)
        cA.addView(ui.row(x1, x2))
        cA.addView(ui.txt("Na janela de compartilhar, o seletor do sistema deixa você escolher o que fazer com o APK: enviar, salvar ou abrir com outro app.", 13f, p.muted).apply { setPadding(ui.dp(2), ui.dp(10), 0, 0) })
        col.addView(cA)

        val c3 = ui.card()
        c3.addView(ui.label("Tema"))
        val a = ui.chip("Automático", false) { tema("auto") }
        val l = ui.chip("Claro", false) { tema("light") }
        val d = ui.chip("Escuro", false) { tema("dark") }
        temas["auto"] = a; temas["light"] = l; temas["dark"] = d
        c3.addView(ui.row(a, l, d))
        for ((k, c) in temas) ui.mark(c, k == Prefs.tema)
        col.addView(c3)

        val c4 = ui.card()
        c4.addView(ui.label("Assinatura dos apps"))
        c4.addView(ui.txt("Os APKs do motor nativo são assinados com uma chave guardada no cofre do Android deste aparelho. Por isso um app gerado aqui pode ser atualizado por outra compilação do mesmo ID. Se você desinstalar o Conversor Nuvem, a chave se perde e os apps antigos precisam ser desinstalados antes de instalar os novos.", 13f, p.muted).apply { setPadding(0, ui.dp(8), 0, 0) })
        col.addView(c4)

        view = ui.screen(col)
        resumo()
    }

    private fun resumo() {
        tvResumo.text = if (Prefs.githubOk()) "Conectado a ${Prefs.user}/${Prefs.repo}" else "Toque para configurar"
    }

    private fun escolherMotor(git: Boolean) {
        Prefs.htmlViaGithub = git
        ui.mark(motor[0], !git); ui.mark(motor[1], git)
    }

    private fun escolherApos(v: String) {
        Prefs.aposCriar = v
        ui.mark(apos[0], v == "share"); ui.mark(apos[1], v != "share")
    }

    private fun tema(t: String) {
        if (Prefs.tema == t) return
        Prefs.tema = t
        act.recreate()
    }

    private fun salvar() {
        if (ocupado) return
        Prefs.token = etToken.text.toString().trim()
        Prefs.user = etUser.text.toString().trim()
        Prefs.repo = etRepo.text.toString().trim()
        resumo()
        if (!Prefs.githubOk()) { say("Preencha o token, o usuário e o repositório.", p.err); return }
        ocupado = true
        say("Verificando o repositório...", p.muted)
        Thread {
            try {
                GitHubBuilder.installWorkflow(act) { m -> BuildState.ui { say(m, p.muted) } }
                BuildState.ui { say("Compilador instalado. Já pode compilar.", p.ok) }
            } catch (e: Exception) {
                BuildState.ui { say("Erro: ${e.message}", p.err) }
            } finally { ocupado = false }
        }.start()
    }

    private fun say(t: String, cor: Int) { tvStatus.text = t; tvStatus.setTextColor(cor); tvStatus.visibility = View.VISIBLE }
}
