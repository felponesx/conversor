package com.felipe.conversornuvem

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** Entrega os APKs guardados para a janela de compartilhar do sistema (sem androidx). */
class ApkProvider : ContentProvider() {
    companion object { const val AUTH = "com.felipe.conversornuvem.apk" }

    private fun resolve(uri: Uri): File {
        val base = File(context!!.filesDir, "apps").canonicalFile
        val f = File(context!!.filesDir, uri.path ?: "").canonicalFile
        if (!f.path.startsWith(base.path + File.separator) || !f.name.endsWith(".apk")) throw FileNotFoundException(uri.toString())
        return f
    }

    override fun onCreate() = true
    override fun getType(uri: Uri) = "application/vnd.android.package-archive"
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY)

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, order: String?): Cursor {
        val f = resolve(uri)
        val c = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
        c.addRow(arrayOf<Any>(shareName ?: f.name, f.length()))
        return c
    }

    private val shareName: String? get() = Installer.nomeCompartilhado
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
}
