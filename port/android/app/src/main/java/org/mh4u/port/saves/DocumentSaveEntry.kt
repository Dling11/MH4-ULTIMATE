package org.mh4u.port.saves

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.InputStream

/** Uses only the temporary read grant from Android's folder picker; never writes to the source. */
class DocumentSaveEntry private constructor(
    private val resolver: ContentResolver, private val tree: Uri, private val document: Uri,
    override val name: String, override val directory: Boolean, override val size: Long,
) : SaveImportEntry {
    override val identity: String get() = document.toString()

    override fun open(): InputStream = resolver.openInputStream(document) ?: error("The selected save file could not be opened.")

    override fun children(): List<SaveImportEntry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(document))
        val result = mutableListOf<SaveImportEntry>()
        val fields = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE)
        val cursor = resolver.query(uri, fields, null, null, null) ?: error("The selected folder could not be read.")
        cursor.use { c -> while (c.moveToNext()) {
            check(result.size < 4096) { "This folder has too many items. Choose the dated save folder." }
            result += DocumentSaveEntry(resolver, tree, DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)),
                c.getString(1) ?: error("A save file has no name."), c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                if (c.isNull(3)) -1 else c.getLong(3))
        } }
        return result
    }

    companion object {
        fun selected(resolver: ContentResolver, tree: Uri): DocumentSaveEntry {
            val document = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            resolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                check(c.moveToFirst()) { "The selected folder could not be opened." }
                return DocumentSaveEntry(resolver, tree, document, c.getString(0) ?: "MH4U save", true, -1)
            }
            error("The selected folder could not be opened.")
        }
    }
}
