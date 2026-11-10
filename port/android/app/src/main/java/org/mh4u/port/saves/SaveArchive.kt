package org.mh4u.port.saves

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** MH4U USA's saved progress and extra data, independent of Android and the running core. */
class SaveArchive(private val root: File, private val now: () -> Long = System::currentTimeMillis) {
    data class Backup(val file: File, val createdAt: Long, val files: Int, val bytes: Long, val reason: String, val order: Long)
    private data class Record(val path: String, val bytes: Long, val hash: String)
    private data class Contents(val createdAt: Long, val reason: String, val present: Set<String>, val records: List<Record>, val order: Long)
    private class NoCharacter : IllegalStateException("No character save found. Save your progress inside MH4U first.")
    private val folder get() = File(root, "save-backups").also { check(it.isDirectory || it.mkdirs()) }
    private val transaction get() = File(root, ".save-restore")

    companion object {
        const val KEEP = 3
        private const val MAX_FILES = 4096
        private const val MAX_FILE = 64L * 1024 * 1024
        private const val MAX_TOTAL = 256L * 1024 * 1024
        private const val BASE = "sdmc/Nintendo 3DS/00000000000000000000000000000000/00000000000000000000000000000000"
        val SCOPES = linkedMapOf("save" to "$BASE/title/00040000/00126300/data", "extra" to "$BASE/extdata/00000000/00001263")
        /** Standard Azahar layout and the shorter layout Native0.1.8 can discover with its depth-limited scan. */
        fun transferPaths(entry: String): List<String> {
            check(validPath(entry)) { "Unsupported save archive path." }
            val scope = entry.substringBefore('/')
            val tail = entry.substringAfter('/')
            val compact = if (scope == "save") "00040000/00126300/data/$tail" else "extdata/00000000/00001263/$tail"
            return listOf("${SCOPES.getValue(scope)}/$tail", compact)
        }
        private val managedName = Regex("backup-[0-9]+-[a-f0-9-]{36}\\.zip")
        private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        private fun validPath(path: String): Boolean = path.length <= 512 && '/' in path && '\\' !in path && ':' !in path &&
            path.substringBefore('/') in SCOPES && path.split('/').all { it.isNotEmpty() && it != "." && it != ".." && it.none { c -> c.code < 32 } }
        private fun bounded(input: InputStream, limit: Int): ByteArray {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (output.size() <= limit) {
                val read = input.read(buffer, 0, minOf(buffer.size, limit - output.size() + 1))
                if (read < 0) break
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }
    }

    private fun target(scope: String): File = File(root, SCOPES.getValue(scope)).also { file ->
        check(file.canonicalPath.startsWith(root.canonicalPath + File.separator)) { "Save folder is outside this app." }
        var ancestor: File? = file
        while (ancestor != null && ancestor != root) {
            check(!Files.isSymbolicLink(ancestor.toPath())) { "Linked save folders are not supported." }
            ancestor = ancestor.parentFile
        }
    }

    private fun sourceFiles(): Pair<Set<String>, List<Pair<String, File>>> {
        val present = linkedSetOf<String>()
        val files = mutableListOf<Pair<String, File>>()
        var bytes = 0L
        for (scope in SCOPES.keys) {
            val base = target(scope)
            if (!base.exists()) continue
            check(base.isDirectory) { "The save folder is not a directory." }
            present += scope
            for (file in base.walkTopDown()) {
                check(!Files.isSymbolicLink(file.toPath())) { "Linked save files are not supported." }
                if (file.isDirectory) continue
                check(file.isFile) { "Unsupported save file." }
                val path = "$scope/${file.relativeTo(base).invariantSeparatorsPath}"
                check(validPath(path)) { "Unsupported save filename." }
                bytes += file.length()
                check(file.length() <= MAX_FILE && bytes <= MAX_TOTAL && files.size < MAX_FILES) { "The save archive is too large." }
                files += path to file
            }
        }
        if (files.none { (path, file) -> path.startsWith("save/") && file.length() > 0 && path.substringAfterLast('/') in setOf("user1", "user2", "user3") }) throw NoCharacter()
        return present to files.sortedBy { it.first }
    }

    @Synchronized fun list(): List<Backup> = folder.listFiles().orEmpty().filter { managedName.matches(it.name) }.mapNotNull {
        runCatching { info(it, inspect(it, false)) }.getOrNull()
    }.sortedByDescending { it.order }

    private fun info(file: File, data: Contents) = Backup(file, data.createdAt, data.records.size, data.records.sumOf { it.bytes }, data.reason, data.order)
    @Synchronized fun validate(file: File): Backup = info(file, inspect(file, true))

    /** A completed archive is published before any older backup is removed. Identical saves reuse the newest valid copy. */
    @Synchronized fun create(reason: String = "Manual backup"): Backup {
        check(!transaction.exists()) { "Finish save recovery before making a backup." }
        val candidate = File(folder, ".backup-${UUID.randomUUID()}.partial")
        try {
            write(candidate, reason, maxOf(now(), (list().firstOrNull()?.order ?: 0L) + 1))
            val data = inspect(candidate, true)
            val previous = list().firstOrNull()
            if (previous != null && runCatching { inspect(previous.file, true) }.getOrNull()?.let {
                it.present == data.present && it.records == data.records
            } == true) return previous
            val destination = File(folder, "backup-${data.createdAt}-${UUID.randomUUID()}.zip")
            Files.move(candidate.toPath(), destination.toPath())
            folder.listFiles().orEmpty().filter { managedName.matches(it.name) }
                .sortedByDescending { runCatching { inspect(it, false).order }.getOrDefault(it.lastModified()) }
                .drop(KEEP).forEach { check(it.delete()) { "An old backup could not be removed." } }
            return info(destination, data)
        } finally { candidate.delete() }
    }

    @Synchronized fun export(destination: File) {
        check(!transaction.exists()) { "Finish save recovery before exporting." }
        write(destination, "Exported save")
        inspect(destination, true)
    }

    private fun write(destination: File, reason: String, order: Long = now()) {
        val (present, files) = sourceFiles()
        destination.parentFile?.mkdirs()
        val temporary = File.createTempFile("save-", ".partial", destination.parentFile)
        try {
            FileOutputStream(temporary).use { stream ->
                ZipOutputStream(stream).use { zip ->
                val records = JSONArray()
                files.forEach { (path, file) ->
                    val bytes = file.inputStream().use { bounded(it, MAX_FILE.toInt()) }
                    check(bytes.size.toLong() <= MAX_FILE) { "Save file changed during backup." }
                    zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry()
                    records.put(JSONObject().put("path", path).put("bytes", bytes.size).put("sha256", sha(bytes)))
                }
                val manifest = JSONObject().put("format", "mh4u-rowell-save").put("version", 1)
                    .put("titleId", "0004000000126300").put("createdAt", now()).put("order", order).put("reason", reason)
                    .put("present", JSONArray(present.toList())).put("files", records)
                zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toString().toByteArray()); zip.closeEntry()
                zip.finish(); stream.fd.sync()
                }
            }
            inspect(temporary, true)
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
    }

    private fun inspect(archive: File, verify: Boolean, extract: File? = null): Contents = ZipFile(archive).use { zip ->
        val entries = zip.entries().asSequence().take(MAX_FILES + 2).toList()
        check(entries.size in 2..MAX_FILES + 1 && entries.none { it.isDirectory }) { "Choose a backup ZIP exported by this app. For another app's save, choose From another MH4U app." }
        check(entries.map { it.name.lowercase() }.toSet().size == entries.size) { "The archive contains duplicate files." }
        val manifestEntry = zip.getEntry("manifest.json") ?: error("Choose a backup ZIP exported by this app. For another app's save, choose From another MH4U app.")
        val manifestBytes = zip.getInputStream(manifestEntry).use { bounded(it, 1024 * 1024) }
        check(manifestBytes.size <= 1024 * 1024) { "The backup manifest is too large." }
        val manifest = JSONObject(String(manifestBytes, Charsets.UTF_8))
        check(manifest.getString("format") == "mh4u-rowell-save" && manifest.getInt("version") == 1 &&
            manifest.getString("titleId") == "0004000000126300") { "This is not a compatible MH4U USA backup." }
        val present = manifest.getJSONArray("present").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
        check("save" in present && SCOPES.keys.containsAll(present)) { "Invalid save folders." }
        val records = manifest.getJSONArray("files").let { a ->
            check(a.length() in 1..MAX_FILES) { "Invalid save file count." }
            (0 until a.length()).map { index -> a.getJSONObject(index).let {
                Record(it.getString("path"), it.getLong("bytes"), it.getString("sha256"))
            } }
        }
        check(records.all { validPath(it.path) && it.path.substringBefore('/') in present && it.bytes in 0..MAX_FILE && it.hash.matches(Regex("[a-f0-9]{64}")) } &&
            records.sumOf { it.bytes } <= MAX_TOTAL && records.map { it.path }.toSet().size == records.size &&
            entries.map { it.name }.toSet() == records.map { it.path }.toSet() + "manifest.json" &&
            records.any { it.path.startsWith("save/") && it.bytes > 0 && it.path.substringAfterLast('/') in setOf("user1", "user2", "user3") }) { "Invalid or incomplete save archive." }
        if (extract != null) present.forEach { check(File(extract, it).mkdirs()) }
        if (verify || extract != null) records.forEach { record ->
            val bytes = zip.getInputStream(zip.getEntry(record.path)).use { bounded(it, record.bytes.toInt()) }
            check(bytes.size.toLong() == record.bytes && sha(bytes) == record.hash) { "Backup verification failed; your current progress was not replaced." }
            if (extract != null) File(extract, record.path).also { checkNotNull(it.parentFile).mkdirs() }.let { file ->
                FileOutputStream(file).use { it.write(bytes); it.fd.sync() }
            }
        }
        Contents(manifest.getLong("createdAt"), manifest.optString("reason", "Backup").take(40), present, records.sortedBy { it.path }, manifest.optLong("order", manifest.getLong("createdAt")))
    }

    /** Extraction and hashes are checked before creating a rollback journal or touching saved progress. */
    @Synchronized fun restore(archive: File, afterMove: (String) -> Unit = {}) {
        recover()
        // Stage outside the journal so a safety-backup operation cannot see an unfinished transaction.
        val staging = File(root, ".save-stage-${UUID.randomUUID()}")
        check(staging.mkdirs())
        try {
            val data = inspect(archive, true, staging)
            try { sourceFiles(); create("Before restore") } catch (_: NoCharacter) { /* First import has no existing character to back up. */ }
            check(transaction.mkdirs())
            Files.move(staging.toPath(), File(transaction, "incoming").toPath())
            val had = JSONObject().also { o -> SCOPES.keys.forEach { o.put(it, target(it).exists()) } }
            durableText(File(transaction, "journal.json"), had.toString())
            val previous = File(transaction, "previous").also { check(it.mkdirs()) }
            try {
                for (scope in SCOPES.keys) {
                    val destination = target(scope)
                    if (destination.exists()) Files.move(destination.toPath(), File(previous, scope).toPath())
                    afterMove("old-$scope")
                    if (scope in data.present) {
                        val parent = checkNotNull(destination.parentFile)
                        check(parent.isDirectory || parent.mkdirs())
                        Files.move(File(transaction, "incoming/$scope").toPath(), destination.toPath())
                    }
                    afterMove("new-$scope")
                }
                durableText(File(transaction, "committed"), "complete")
            } catch (error: Exception) { recover(); throw error }
            check(transaction.deleteRecursively()) { "Restore completed; temporary recovery files will be cleaned on next start." }
        } finally {
            staging.deleteRecursively()
            // If preparation failed before any saved-file move, no rollback is needed.
            if (transaction.exists() && !File(transaction, "journal.json").exists()) transaction.deleteRecursively()
        }
    }

    @Synchronized fun recover() {
        if (!transaction.exists()) { cleanPreparations(); return }
        check(!Files.isSymbolicLink(transaction.toPath())) { "Invalid save recovery folder." }
        val journal = File(transaction, "journal.json")
        if (journal.exists() && !File(transaction, "committed").exists()) {
            val had = JSONObject(journal.readText())
            for (scope in SCOPES.keys) {
                val old = File(transaction, "previous/$scope")
                val destination = target(scope)
                if (old.exists()) {
                    check(!Files.isSymbolicLink(old.toPath()))
                    check(!destination.exists() || destination.deleteRecursively())
                    val parent = checkNotNull(destination.parentFile)
                    check(parent.isDirectory || parent.mkdirs())
                    Files.move(old.toPath(), destination.toPath())
                } else if (!had.getBoolean(scope)) check(!destination.exists() || destination.deleteRecursively())
            }
        }
        check(transaction.deleteRecursively()) { "Save recovery needs another attempt. Keep the app installed." }
        cleanPreparations()
    }

    private fun cleanPreparations() {
        root.listFiles().orEmpty().filter { it.name.matches(Regex("\\.save-stage-[a-f0-9-]{36}")) }.forEach {
            check(!Files.isSymbolicLink(it.toPath()))
            check(it.deleteRecursively()) { "An unfinished save import could not be cleaned up." }
        }
        File(root, "save-backups").listFiles().orEmpty().filter { it.name.matches(Regex("\\.backup-[a-f0-9-]{36}\\.partial|save-[0-9-]+\\.partial")) }.forEach { it.delete() }
    }

    private fun durableText(file: File, text: String) {
        val temporary = File(file.parentFile, file.name + ".partial")
        FileOutputStream(temporary).use { it.write(text.toByteArray()); it.fd.sync() }
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
