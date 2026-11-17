package com.makay.cleaner.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import java.io.File

/** Copies one user-selected file into a directory granted through the Storage Access Framework. */
object DocumentTreeCopy {
    fun copy(context: Context, source: File, treeUri: Uri): Boolean {
        if (!source.isFile || !source.canRead()) return false
        val resolver = context.contentResolver
        var target: Uri? = null
        return try {
            val documentId = DocumentsContract.getTreeDocumentId(treeUri)
            val directory = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
            val mime = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(source.extension.lowercase()) ?: "application/octet-stream"
            val created = DocumentsContract.createDocument(resolver, directory, mime, source.name)
                ?: return false
            target = created
            val copied = source.inputStream().use { input ->
                val output = resolver.openOutputStream(created, "w")
                    ?: throw IllegalStateException("Destination cannot be opened")
                output.use { input.copyTo(it) }
            }
            if (copied == source.length()) {
                true
            } else {
                runCatching { DocumentsContract.deleteDocument(resolver, created) }
                false
            }
        } catch (_: Exception) {
            target?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
            false
        }
    }
}
