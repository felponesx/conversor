package com.felipe.conversornuvem

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import java.io.File

object Installer {
    const val ACTION = "com.felipe.conversornuvem.INSTALL_RESULT"
    var nomeCompartilhado: String? = null

    /** Abre a janela de compartilhar do sistema com o APK; de lá você escolhe para onde mandar ou com o que abrir. */
    fun share(ctx: Context, apk: File, nome: String) {
        try {
            val rel = apk.canonicalFile.relativeTo(ctx.filesDir.canonicalFile).path
            val uri = Uri.parse("content://${ApkProvider.AUTH}/$rel")
            nomeCompartilhado = nome.replace(Regex("[^A-Za-z0-9._-]+"), "_").ifBlank { "app" } + ".apk"
            val send = Intent(Intent.ACTION_SEND)
                .setType("application/vnd.android.package-archive")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(Intent.createChooser(send, "Enviar APK").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Não consegui abrir o compartilhar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /** Abre o instalador do Android para o APK. Devolve false se faltar a permissão de "fontes desconhecidas". */
    fun install(ctx: Context, apk: File): Boolean {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(ctx, "Permita instalar apps deste aplicativo e toque em Instalar de novo.", Toast.LENGTH_LONG).show()
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        try {
            val pi = ctx.packageManager.packageInstaller
            val id = pi.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
            pi.openSession(id).use { s ->
                apk.inputStream().use { input ->
                    s.openWrite("app.apk", 0, apk.length()).use { out ->
                        input.copyTo(out)
                        s.fsync(out)
                    }
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallReceiver::class.java).setAction(ACTION), flags)
                s.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            Toast.makeText(ctx, "Não consegui abrir o instalador: ${e.message}", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    /** Copia o APK para a pasta Downloads (Android 10 ou mais novo). */
    fun saveToDownloads(ctx: Context, apk: File, nome: String): Boolean {
        if (Build.VERSION.SDK_INT < 29) {
            Toast.makeText(ctx, "Salvar em Downloads precisa do Android 10 ou mais novo.", Toast.LENGTH_LONG).show()
            return false
        }
        val safe = nome.replace(Regex("[^A-Za-z0-9._-]+"), "_").ifBlank { "app" } + ".apk"
        val v = ContentValues()
        v.put(MediaStore.Downloads.DISPLAY_NAME, safe)
        v.put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
        v.put(MediaStore.Downloads.RELATIVE_PATH, "Download")
        try {
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: return false
            ctx.contentResolver.openOutputStream(uri)?.use { o -> apk.inputStream().use { it.copyTo(o) } }
        } catch (e: Exception) {
            Toast.makeText(ctx, "Não consegui salvar: ${e.message}", Toast.LENGTH_LONG).show()
            return false
        }
        Toast.makeText(ctx, "Salvo em Downloads: $safe", Toast.LENGTH_LONG).show()
        return true
    }
}

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                if (confirm != null) ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS ->
                Toast.makeText(ctx, "App instalado.", Toast.LENGTH_LONG).show()
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Não foi possível instalar."
                Toast.makeText(ctx, "Instalação: $msg", Toast.LENGTH_LONG).show()
            }
        }
    }
}
