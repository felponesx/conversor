package com.felipe.conversornuvem.engine

import java.io.InputStream

class EngineInput(
    val nome: String,
    val pacote: String,
    val versionCode: Int,
    val site: Map<String, ByteArray>,
    /** Devolve o PNG do ícone no tamanho pedido (em pixels) ou null para manter o ícone do modelo. */
    val icon: (Int) -> ByteArray?
)

object Site {
    private val SKIP = Regex("(^|/)(__MACOSX|\\.DS_Store|Thumbs\\.db)(/|$)")

    fun fromHtml(text: String): Map<String, ByteArray> = mapOf("index.html" to text.toByteArray(Charsets.UTF_8))
    fun fromHtmlBytes(bytes: ByteArray): Map<String, ByteArray> = mapOf("index.html" to bytes)

    /** Lê um .zip de site. Acha o index.html (mesmo dentro de uma pasta) e usa a pasta dele como raiz. */
    fun fromZip(input: InputStream): Map<String, ByteArray> {
        val items = ZipTools.readAll(input)
        val clean = ArrayList<ZipItem>()
        for (it in items) {
            val n = it.name.replace('\\', '/').trimStart('/')
            if (n.isEmpty() || SKIP.containsMatchIn(n)) continue
            require(n.split('/').none { s -> s == ".." }) { "Caminho inválido no zip: ${it.name}" }
            clean.add(ZipItem(n, it.data))
        }
        require(clean.isNotEmpty()) { "O zip está vazio." }
        var index = clean.filter { it.name.equals("index.html", true) || it.name.endsWith("/index.html", true) }
            .minByOrNull { it.name.length }
        if (index == null) {
            val htmls = clean.filter { it.name.endsWith(".html", true) || it.name.endsWith(".htm", true) }
            require(htmls.size == 1) { "Não achei o index.html no zip." }
            index = htmls[0]
        }
        val slash = index.name.lastIndexOf('/')
        val root = if (slash >= 0) index.name.substring(0, slash + 1) else ""
        val out = LinkedHashMap<String, ByteArray>()
        for (it in clean) {
            if (!it.name.startsWith(root)) continue
            var rel = it.name.substring(root.length)
            if (it === index) rel = "index.html"
            out[rel] = it.data
        }
        return out
    }
}

object HtmlEngine {
    const val TEMPLATE_PACKAGE = "com.conversor.template"
    const val TEMPLATE_LABEL = "Template App Label"
    private val RE_PACOTE = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
    private val RE_ICON = Regex("^res/mipmap-[a-z0-9-]+/ic_launcher\\.png$")
    private val NO_COMPRESS = setOf("png", "jpg", "jpeg", "gif", "webp", "mp3", "mp4", "m4a", "ogg", "webm", "zip", "woff2", "woff", "gz", "br", "7z", "rar")

    private fun pngSize(d: ByteArray): Int =
        if (d.size > 24) ((d[16].toInt() and 0xff) shl 24) or ((d[17].toInt() and 0xff) shl 16) or ((d[18].toInt() and 0xff) shl 8) or (d[19].toInt() and 0xff) else 0

    fun build(
        template: ByteArray,
        input: EngineInput,
        signer: KeySigner,
        log: (String) -> Unit,
        progress: (Int) -> Unit
    ): ByteArray {
        require(RE_PACOTE.matches(input.pacote)) { "ID do pacote inválido: ${input.pacote}" }
        require(input.nome.isNotBlank()) { "Dê um nome ao app." }
        require(input.site.containsKey("index.html")) { "O site precisa ter um index.html." }

        progress(5); log("Lendo o modelo do app...")
        val items = ZipTools.readAll(template.inputStream())
        val byName = items.associateBy { it.name }
        val manifest = byName["AndroidManifest.xml"] ?: throw IllegalStateException("Modelo sem manifesto.")
        val dex = byName["classes.dex"] ?: throw IllegalStateException("Modelo sem classes.dex.")
        val arsc = byName["resources.arsc"] ?: throw IllegalStateException("Modelo sem resources.arsc.")

        progress(15); log("Aplicando o nome \"${input.nome}\" e o ID ${input.pacote}")
        val nome = input.nome.trim().replace(Regex("[\\r\\n]+"), " ")
        val manifestPatched = Axml.patch(
            manifest.data,
            mapOf(TEMPLATE_PACKAGE to input.pacote, TEMPLATE_LABEL to nome),
            input.versionCode
        )

        progress(25); log("Gerando os ícones...")
        val zip = ZipWriter()
        zip.add("AndroidManifest.xml", manifestPatched, stored = false)
        zip.add("classes.dex", dex.data, stored = true, align = 4)
        zip.add("resources.arsc", arsc.data, stored = true, align = 4)
        var icons = 0
        for (it in items) {
            if (!RE_ICON.matches(it.name)) continue
            val size = pngSize(it.data)
            val novo = if (size > 0) input.icon(size) else null
            zip.add(it.name, novo ?: it.data, stored = true, align = 4)
            if (novo != null) icons++
        }
        log(if (icons > 0) "Ícones gerados: $icons tamanhos" else "Mantendo o ícone padrão")

        progress(40); log("Copiando ${input.site.size} arquivos do site...")
        var bytes = 0L
        val total = input.site.size
        var i = 0
        for ((path, data) in input.site) {
            val ext = path.substringAfterLast('.', "").lowercase()
            zip.add("assets/www/$path", data, stored = ext in NO_COMPRESS)
            bytes += data.size
            i++
            if (i % 20 == 0) progress(40 + (30 * i / total))
        }
        log("Site: ${bytes / 1024} KB")

        progress(75); log("Empacotando e alinhando (zipalign)...")
        val unsigned = zip.finish()

        progress(85); log("Assinando o APK (esquema v2)...")
        val signed = ApkSigner.sign(unsigned, signer)
        progress(100); log("APK pronto: ${signed.size / 1024} KB")
        return signed
    }
}
