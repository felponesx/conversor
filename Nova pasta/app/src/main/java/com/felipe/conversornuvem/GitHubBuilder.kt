package com.felipe.conversornuvem

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Compila pelo GitHub Actions (Kotlin, Flutter, React Native e, se você quiser, HTML). */
object GitHubBuilder {
    val LABELS = arrayOf("Enviar", "Fila", "Compilar", "Baixar")
    private const val WF_PATH = ".github/workflows/compilar.yml"

    class Resp(val code: Int, val body: String, val location: String?)

    private fun repoPath() = "/repos/${Prefs.user}/${Prefs.repo}"
    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)

    private fun api(method: String, path: String, body: String? = null, accept: String = "application/vnd.github+json"): Resp {
        val c = URL("https://api.github.com$path").openConnection() as HttpURLConnection
        c.requestMethod = method
        c.instanceFollowRedirects = false
        c.connectTimeout = 30000
        c.readTimeout = 180000
        c.setRequestProperty("Authorization", "Bearer ${Prefs.token}")
        c.setRequestProperty("Accept", accept)
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        if (body != null) {
            val bytes = body.toByteArray()
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setFixedLengthStreamingMode(bytes.size)
            c.outputStream.use { it.write(bytes) }
        }
        val code = c.responseCode
        val stream = if (code in 200..399) c.inputStream else c.errorStream
        val txt = if (code in 301..308) "" else (stream?.bufferedReader()?.use { it.readText() } ?: "")
        return Resp(code, txt, c.getHeaderField("Location"))
    }

    private fun msg(r: Resp): String {
        if (r.code == 401) return "Token inválido ou expirado."
        return try { JSONObject(r.body).optString("message", "Erro ${r.code}") } catch (e: Exception) { "Erro ${r.code}" }
    }

    /** Cria o repositório (se precisar) e instala o arquivo de compilação nele. */
    fun installWorkflow(ctx: Context, say: (String) -> Unit) {
        say("Verificando o repositório...")
        var r = api("GET", repoPath())
        if (r.code == 404) {
            say("Criando o repositório privado...")
            val c = api("POST", "/user/repos", JSONObject().put("name", Prefs.repo).put("private", true).put("auto_init", true).toString())
            if (c.code !in 200..299) throw Exception(msg(c))
            Thread.sleep(3000)
            r = api("GET", repoPath())
        }
        if (r.code !in 200..299) throw Exception(msg(r))
        val branch = JSONObject(r.body).getString("default_branch")
        val caminho = "${repoPath()}/contents/$WF_PATH"
        say("Instalando o compilador...")
        val yml = ctx.assets.open("compilar.yml").use { it.readBytes() }
        val corpo = JSONObject().put("message", "Instalar compilador").put("content", b64(yml)).put("branch", branch)
        val ex = api("GET", "$caminho?ref=$branch")
        if (ex.code == 200) corpo.put("sha", JSONObject(ex.body).getString("sha"))
        val p = api("PUT", caminho, corpo.toString())
        if (p.code !in 200..299) throw Exception(msg(p) + " (o token precisa dos escopos repo e workflow)")
    }

    fun run(ctx: Context, a: AppRec, b: BuildRec): File? {
        val tipo = a.tipo
        val codigo = Store.codigoFile(a)
        val fonte = Store.sourceFile(a)
        val ext = if (codigo.exists() || !a.arquivoNome.lowercase().endsWith(".zip")) "html" else "zip"
        val conteudo: ByteArray = when {
            codigo.exists() -> codigo.readBytes()
            fonte.exists() -> fonte.readBytes()
            else -> throw IllegalStateException("Esse app não tem arquivo guardado.")
        }
        val caminho = "envios/${b.id}.$ext"

        BuildState.set("Enviando o projeto para o GitHub...", 1, 8f, 12f, "Mandando o projeto para o GitHub.")
        BuildState.log("$ enviando $caminho")
        val rr = api("GET", repoPath())
        if (rr.code == 404) throw Exception("Repositório não encontrado. Abra as configurações e instale o compilador.")
        if (rr.code !in 200..299) throw Exception(msg(rr))
        val branch = JSONObject(rr.body).getString("default_branch")

        val put = api("PUT", "${repoPath()}/contents/$caminho",
            JSONObject().put("message", "Envio ${b.id}").put("content", b64(conteudo)).put("branch", branch).toString())
        if (put.code !in 200..299) throw Exception(msg(put))
        BuildState.log("✓ projeto enviado ao GitHub")

        var caminhoIcone = ""
        val icone = Store.iconFile(a)
        if (icone.exists()) {
            caminhoIcone = "envios/${b.id}-icone.png"
            BuildState.log("$ enviando o ícone")
            val pi = api("PUT", "${repoPath()}/contents/$caminhoIcone",
                JSONObject().put("message", "Ícone ${b.id}").put("content", b64(icone.readBytes())).put("branch", branch).toString())
            if (pi.code !in 200..299) throw Exception(msg(pi))
            BuildState.log("✓ ícone enviado")
        }

        BuildState.log("$ iniciando a compilação...")
        val disp = api("POST", "${repoPath()}/actions/workflows/compilar.yml/dispatches",
            JSONObject().put("ref", branch).put("inputs", JSONObject()
                .put("id", b.id).put("tipo", tipo).put("arquivo", caminho)
                .put("nome", a.nome).put("pacote", a.pacote).put("icone", caminhoIcone)).toString())
        if (disp.code != 204) throw Exception(
            when (disp.code) {
                404 -> "Compilador não instalado. Abra as configurações e toque em Salvar e instalar o compilador."
                422 -> "O compilador do repositório está desatualizado. Abra as configurações e toque em Salvar e instalar o compilador."
                else -> msg(disp)
            })
        Prefs.pendente = "${a.id}|${b.id}"
        return acompanhar(ctx, a, b)
    }

    private val PASSOS = mapOf(
        "Set up job" to "Preparando o ambiente", "Run actions/checkout@v4" to "Baixando o projeto",
        "Run actions/setup-java@v4" to "Instalando o Java", "Run actions/setup-node@v4" to "Instalando o Node",
        "Run subosito/flutter-action@v2" to "Instalando o Flutter", "Run gradle/actions/setup-gradle@v4" to "Instalando o Gradle",
        "Compilar" to "Compilando o app (nome, ícone e build)", "Run softprops/action-gh-release@v2" to "Publicando o APK"
    )

    private fun passos(runId: Long, vistos: HashMap<String, Int>) {
        try {
            val r = api("GET", "${repoPath()}/actions/runs/$runId/jobs")
            if (r.code !in 200..299) return
            val jobs = JSONObject(r.body).optJSONArray("jobs") ?: return
            if (jobs.length() == 0) return
            val steps = jobs.getJSONObject(0).optJSONArray("steps") ?: return
            for (i in 0 until steps.length()) {
                val s = steps.getJSONObject(i)
                val nome = PASSOS[s.optString("name")] ?: continue
                val chave = s.optString("name")
                val st = s.optString("status")
                if (st == "in_progress" && vistos[chave] == null) { vistos[chave] = 1; BuildState.log("▶ $nome...") }
                if (st == "completed" && vistos[chave] != 2) {
                    val concl = s.optString("conclusion")
                    if (concl == "skipped") { vistos[chave] = 2; continue }
                    if (vistos[chave] == null) BuildState.log("▶ $nome...")
                    vistos[chave] = 2
                    if (concl == "success") BuildState.log("✓ $nome", BuildState.C_OK) else BuildState.log("✗ $nome falhou", BuildState.C_ERR)
                }
            }
        } catch (e: Exception) { }
    }

    private fun logFinal(runId: Long) {
        try {
            val r = api("GET", "${repoPath()}/actions/runs/$runId/jobs")
            val jobs = JSONObject(r.body).optJSONArray("jobs") ?: return
            if (jobs.length() == 0) return
            val id = jobs.getJSONObject(0).getLong("id")
            val l = api("GET", "${repoPath()}/actions/jobs/$id/logs", null, "application/vnd.github+json")
            val url = l.location ?: return
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 30000; c.readTimeout = 60000
            val txt = c.inputStream.bufferedReader().use { it.readText() }
            val ls = txt.lines().map { it.replace(Regex("^\\d{4}-\\d\\d-\\d\\dT[\\d:.]+Z\\s?"), "").trim() }.filter { it.isNotEmpty() }.takeLast(14)
            for (x in ls) BuildState.log(x.take(160), if (Regex("error|fail|erro", RegexOption.IGNORE_CASE).containsMatchIn(x)) BuildState.C_ERR else BuildState.C_DIM)
        } catch (e: Exception) { }
    }

    fun acompanhar(ctx: Context, a: AppRec, b: BuildRec): File? {
        val ini = System.currentTimeMillis()
        val vistos = HashMap<String, Int>()
        var n = 0
        while (System.currentTimeMillis() - ini < 45 * 60 * 1000L) {
            Thread.sleep(9000)
            n++
            val min = (System.currentTimeMillis() - ini) / 60000
            val r = try { api("GET", "${repoPath()}/actions/workflows/compilar.yml/runs?per_page=15") } catch (e: Exception) { null }
            if (r == null || r.code !in 200..299) {
                BuildState.set("Conectando ao GitHub... ($min min)", 2, 14f, 20f); continue
            }
            val runs = JSONObject(r.body).getJSONArray("workflow_runs")
            var exec: JSONObject? = null
            for (k in 0 until runs.length()) {
                val o = runs.getJSONObject(k)
                if (o.optString("display_title") == "build ${b.id}") { exec = o; break }
            }
            if (exec == null) {
                BuildState.set("Na fila do GitHub... ($min min)", 2, 16f, 22f, "Esperando o GitHub começar.")
                if (n == 1) BuildState.log("● na fila: esperando um servidor do GitHub...")
                continue
            }
            val runId = exec.getLong("id")
            if (exec.getString("status") != "completed") {
                BuildState.set("Compilando... ($min min)", 3, 25f, 88f, "HTML leva uns 3 a 5 min. Flutter e React Native, uns 10. Pode sair do app.")
                passos(runId, vistos)
                if (n % 3 == 0) BuildState.log("… ainda compilando ($min min)")
                continue
            }
            passos(runId, vistos)
            if (exec.optString("conclusion") == "success") return baixar(ctx, a, b)
            logFinal(runId)
            Prefs.pendente = null
            b.ok = -1; b.link = exec.getString("html_url"); Store.save()
            throw GitHubFalha("A compilação falhou. Toque em Ver erro no GitHub.", exec.getString("html_url"))
        }
        Prefs.pendente = null
        throw Exception("Demorou demais. Veja o andamento no GitHub.")
    }

    class GitHubFalha(msg: String, val link: String) : Exception(msg)

    private fun baixar(ctx: Context, a: AppRec, b: BuildRec): File {
        BuildState.set("Baixando o APK...", 4, 92f, 92f, "Quase lá.")
        BuildState.log("$ baixando o APK...")
        val rel = api("GET", "${repoPath()}/releases/tags/build-${b.id}")
        if (rel.code !in 200..299) throw Exception(msg(rel))
        val relJson = JSONObject(rel.body)
        val assets = relJson.getJSONArray("assets")
        if (assets.length() == 0) throw Exception("A compilação terminou, mas não achei o APK.")
        val assetId = assets.getJSONObject(0).getLong("id")
        val r = api("GET", "${repoPath()}/releases/assets/$assetId", null, "application/octet-stream")
        val url = r.location ?: throw Exception("Não consegui o link de download (${r.code}).")
        val destino = Store.apkFile(a, b)
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 30000; c.readTimeout = 180000
        c.inputStream.use { i -> destino.outputStream().use { o -> i.copyTo(o) } }
        b.link = relJson.optString("html_url", "")
        Prefs.pendente = null
        return destino
    }
}
