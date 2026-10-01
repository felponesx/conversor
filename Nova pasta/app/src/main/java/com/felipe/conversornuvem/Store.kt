package com.felipe.conversornuvem

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class BuildRec(val id: String, val ts: Long) {
    var ok = 0               // 0 = em andamento/interrompida, 1 = pronto, -1 = falhou
    var link: String? = null
    var via = "nativo"       // nativo | github
}

class AppRec(val id: String, val tipo: String, val criado: Long) {
    var nome = ""
    var pacote = ""
    var pacoteManual = false
    var arquivoNome = ""
    var arquivoTam = 0L
    var atualizado = criado
    val builds = ArrayList<BuildRec>()
}

object Prefs {
    private lateinit var ctx: Context
    fun init(c: Context) { ctx = c.applicationContext }
    private fun sp() = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)
    var token: String get() = sp().getString("tk", "") ?: ""; set(v) { sp().edit().putString("tk", v).apply() }
    var user: String get() = sp().getString("user", "") ?: ""; set(v) { sp().edit().putString("user", v).apply() }
    var repo: String get() = sp().getString("repo", "") ?: ""; set(v) { sp().edit().putString("repo", v).apply() }
    var tema: String get() = sp().getString("tema", "auto") ?: "auto"; set(v) { sp().edit().putString("tema", v).apply() }
    var htmlViaGithub: Boolean get() = sp().getBoolean("htmlGit", false); set(v) { sp().edit().putBoolean("htmlGit", v).apply() }
    var aposCriar: String get() = sp().getString("apos", "share") ?: "share"; set(v) { sp().edit().putString("apos", v).apply() }
    var pendente: String? get() = sp().getString("pendente", null); set(v) { sp().edit().putString("pendente", v).apply() }
    fun githubOk() = token.isNotBlank() && user.isNotBlank() && repo.isNotBlank()
}

object Store {
    private lateinit var ctx: Context
    val apps = ArrayList<AppRec>()

    fun init(c: Context) {
        ctx = c.applicationContext
        Prefs.init(c)
        load()
    }

    fun dir(a: AppRec): File = File(ctx.filesDir, "apps/${a.id}").apply { mkdirs() }
    fun iconFile(a: AppRec) = File(dir(a), "icon.png")
    fun sourceFile(a: AppRec) = File(dir(a), "fonte.bin")
    fun codigoFile(a: AppRec) = File(dir(a), "codigo.html")
    fun apkFile(a: AppRec, b: BuildRec): File = File(File(dir(a), "builds").apply { mkdirs() }, "${b.id}.apk")
    fun find(id: String): AppRec? = apps.firstOrNull { it.id == id }
    fun findBuild(id: String): Pair<AppRec, BuildRec>? {
        for (a in apps) for (b in a.builds) if (b.id == id) return Pair(a, b)
        return null
    }

    private fun file() = File(ctx.filesDir, "apps.json")

    @Synchronized
    fun load() {
        apps.clear()
        val f = file()
        if (!f.exists()) return
        try {
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val a = AppRec(o.getString("id"), o.getString("tipo"), o.getLong("criado"))
                a.nome = o.optString("nome"); a.pacote = o.optString("pacote")
                a.pacoteManual = o.optBoolean("pacoteManual")
                a.arquivoNome = o.optString("arquivoNome"); a.arquivoTam = o.optLong("arquivoTam")
                a.atualizado = o.optLong("atualizado", a.criado)
                val bs = o.optJSONArray("builds") ?: JSONArray()
                for (j in 0 until bs.length()) {
                    val bo = bs.getJSONObject(j)
                    val b = BuildRec(bo.getString("id"), bo.getLong("ts"))
                    b.ok = bo.optInt("ok"); b.via = bo.optString("via", "nativo")
                    b.link = if (bo.has("link") && !bo.isNull("link")) bo.getString("link") else null
                    a.builds.add(b)
                }
                apps.add(a)
            }
        } catch (e: Exception) {
            // arquivo corrompido: começa vazio
        }
    }

    @Synchronized
    fun save() {
        val arr = JSONArray()
        for (a in apps) {
            val o = JSONObject()
            o.put("id", a.id); o.put("tipo", a.tipo); o.put("criado", a.criado)
            o.put("nome", a.nome); o.put("pacote", a.pacote); o.put("pacoteManual", a.pacoteManual)
            o.put("arquivoNome", a.arquivoNome); o.put("arquivoTam", a.arquivoTam); o.put("atualizado", a.atualizado)
            val bs = JSONArray()
            for (b in a.builds) {
                val bo = JSONObject()
                bo.put("id", b.id); bo.put("ts", b.ts); bo.put("ok", b.ok); bo.put("via", b.via)
                if (b.link != null) bo.put("link", b.link)
                bs.put(bo)
            }
            o.put("builds", bs)
            arr.put(o)
        }
        val tmp = File(ctx.filesDir, "apps.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file())
    }
}
