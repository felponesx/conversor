package com.felipe.conversornuvem

import android.content.Context

/** Decide o motor (nativo ou GitHub), roda em segundo plano e registra o resultado. */
object Runner {
    fun usaGitHub(tipo: String) = tipo != "html" || Prefs.htmlViaGithub

    fun start(ctx: Context, a: AppRec) {
        val c = ctx.applicationContext
        val git = usaGitHub(a.tipo)
        val b = BuildRec(Util.novoId("b"), System.currentTimeMillis())
        b.via = if (git) "github" else "nativo"
        a.builds.add(0, b)
        a.atualizado = b.ts
        Store.save()
        BuildState.begin(b.id, if (git) GitHubBuilder.LABELS else NativeBuilder.LABELS)
        BuildState.lastNome = a.nome
        Thread { executar(c, a, b, false) }.start()
    }

    /** Retoma o acompanhamento de uma compilação do GitHub que ficou pela metade. */
    fun resume(ctx: Context): Boolean {
        val p = Prefs.pendente ?: return false
        if (BuildState.running) return true
        val partes = p.split("|")
        if (partes.size != 2) { Prefs.pendente = null; return false }
        val par = Store.findBuild(partes[1])
        if (par == null) { Prefs.pendente = null; return false }
        val c = ctx.applicationContext
        BuildState.begin(par.second.id, GitHubBuilder.LABELS)
        BuildState.lastNome = par.first.nome
        BuildState.log("● retomando o acompanhamento no GitHub...")
        Thread { executar(c, par.first, par.second, true) }.start()
        return true
    }

    private fun executar(ctx: Context, a: AppRec, b: BuildRec, retomando: Boolean) {
        try {
            val apk = if (b.via == "github") {
                if (retomando) GitHubBuilder.acompanhar(ctx, a, b) else GitHubBuilder.run(ctx, a, b)
            } else NativeBuilder.run(ctx, a, b)
            b.ok = 1
            Store.save()
            BuildState.log("✓ APK pronto", BuildState.C_OK)
            BuildState.finishOk(
                if (b.via == "nativo") "APK pronto. Toque em Instalar." else "APK pronto. Se o instalador não abrir, toque em Instalar.",
                apk, a.nome, b.link
            )
            if (apk != null) BuildState.ui {
                if (Prefs.aposCriar == "share") Installer.share(ctx, apk, a.nome) else Installer.install(ctx, apk)
            }
        } catch (e: GitHubBuilder.GitHubFalha) {
            b.ok = -1; Store.save()
            BuildState.log("✗ ${e.message}", BuildState.C_ERR)
            BuildState.finishErr("Erro: ${e.message}", e.link)
        } catch (e: Exception) {
            b.ok = -1; Store.save()
            BuildState.log("✗ ${e.message ?: e.javaClass.simpleName}", BuildState.C_ERR)
            BuildState.finishErr("Erro: ${e.message ?: e.javaClass.simpleName}")
        } catch (e: OutOfMemoryError) {
            b.ok = -1; Store.save()
            BuildState.finishErr("Erro: faltou memória. Use um site menor.")
        }
    }
}
