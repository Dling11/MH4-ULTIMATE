package org.mh4u.port.saves

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.InputStream
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.ZipFile

/** Writes a new portable folder using a user-selected tree; the source app is never accessed. */
object DocumentSaveExport {
    fun export(resolver: ContentResolver, tree: Uri, archive: java.io.File, repository: SaveArchive): String {
        // Native0.1.8 only scans six levels from storage root. Keep the compact title path discoverable.
        val treeId = DocumentsContract.getTreeDocumentId(tree)
        val relative = treeId.substringAfter(':', "")
        check(tree.authority == "com.android.externalstorage.documents" && relative.isNotBlank() && '/' !in relative) {
            "Choose a folder directly in Internal storage, such as MH4U_Saves. This lets Native find the export."
        }
        repository.validate(archive)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
        val name = "MH4U save " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + " " + UUID.randomUUID().toString().take(4)
        val root = DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, name)
            ?: error("The selected destination could not create a save folder.")
        val directories = mutableMapOf("" to root)
        try {
            ZipFile(archive).use { zip ->
                zip.entries().asSequence().filter { it.name != "manifest.json" }.forEach { entry ->
                    for (path in SaveArchive.transferPaths(entry.name)) {
                        val parts = path.split('/')
                        var key = ""
                        for (part in parts.dropLast(1)) {
                            val parentKey = key
                            key = if (key.isEmpty()) part else "$key/$part"
                            if (key !in directories) directories[key] = DocumentsContract.createDocument(resolver,
                                directories.getValue(parentKey), DocumentsContract.Document.MIME_TYPE_DIR, part)
                                ?: error("Could not create the save subfolder.")
                        }
                        val file = DocumentsContract.createDocument(resolver, directories.getValue(key), "application/octet-stream", parts.last())
                            ?: error("Could not create the exported save file.")
                        val expected = MessageDigest.getInstance("SHA-256")
                        var copied = 0L
                        zip.getInputStream(entry).use { input ->
                            val output = resolver.openOutputStream(file, "wt") ?: error("The exported save file could not be written.")
                            output.use { out ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val count = input.read(buffer); if (count < 0) break
                                    copied += count; expected.update(buffer, 0, count); out.write(buffer, 0, count)
                                }
                            }
                        }
                        check(copied == entry.size) { "An exported save file was incomplete." }
                        val actual = resolver.openInputStream(file)?.use(::sha) ?: error("The export could not be verified.")
                        check(expected.digest().contentEquals(actual)) { "Export verification failed. Your saved progress was not changed." }
                    }
                }
            }
            return name
        } catch (error: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, root) }
            throw error
        }
    }

    private fun sha(input: InputStream): ByteArray {
        val hash = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while (true) { val count = input.read(buffer); if (count < 0) break; hash.update(buffer, 0, count) }
        return hash.digest()
    }
}
