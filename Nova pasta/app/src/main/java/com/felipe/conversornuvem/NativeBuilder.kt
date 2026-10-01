package com.felipe.conversornuvem

import android.content.Context
import com.felipe.conversornuvem.engine.EngineInput
import com.felipe.conversornuvem.engine.HtmlEngine
import com.felipe.conversornuvem.engine.Site
import java.io.File
import java.io.FileInputStream

/** Converte HTML em APK direto no aparelho, sem GitHub e sem internet. */
object NativeBuilder {
    val LABELS = arrayOf("Ler", "Montar", "Assinar", "Instalar")

    fun run(ctx: Context, a: AppRec, b: BuildRec): File {
        BuildState.set("Preparando o motor nativo...", 1, 3f, 20f, "Tudo roda aqui no celular, sem internet.")
        BuildState.log("$ motor nativo: HTML para APK")

        val template = ctx.assets.open("engine/shell.apk").use { it.readBytes() }
        val codigo = Store.codigoFile(a)
        val fonte = Store.sourceFile(a)
        val site: Map<String, ByteArray> = when {
            codigo.exists() -> Site.fromHtml(codigo.readText())
            fonte.exists() && a.arquivoNome.lowercase().endsWith(".zip") -> FileInputStream(fonte).use { Site.fromZip(it) }
            fonte.exists() -> Site.fromHtmlBytes(fonte.readBytes())
            else -> throw IllegalStateException("Esse app não tem arquivo guardado.")
        }
        val icone = Store.iconFile(a).let { if (it.exists()) it.readBytes() else null }
        val signer = AndroidKeySigner()
        val versionCode = ((System.currentTimeMillis() / 60000L) % 2000000000L).toInt()

        val apk = HtmlEngine.build(
            template,
            EngineInput(a.nome, a.pacote, versionCode, site) { size -> IconMaker.renderPng(icone, a.nome, size) },
            signer,
            { linha ->
                BuildState.log(if (linha.startsWith("APK pronto")) "✓ $linha" else linha)
                try { Thread.sleep(220) } catch (e: InterruptedException) { }
            },
            { p ->
                val etapa = if (p < 25) 1 else if (p < 75) 2 else 3
                BuildState.set(
                    when (etapa) { 1 -> "Lendo o modelo do app..."; 2 -> "Montando o APK..."; else -> "Assinando o APK..." },
                    etapa, p.toFloat(), p.toFloat(), "Motor nativo, sem GitHub."
                )
            }
        )
        val saida = Store.apkFile(a, b)
        saida.writeBytes(apk)
        return saida
    }
}
