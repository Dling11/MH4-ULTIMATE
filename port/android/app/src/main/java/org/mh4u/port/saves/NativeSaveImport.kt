package org.mh4u.port.saves

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.UUID

/** Read-only access to a user-selected export; works with SAF providers and local test fixtures. */
interface SaveImportEntry {
    val name: String
    val directory: Boolean
    val size: Long
    val identity: String
    fun children(): List<SaveImportEntry>
    fun open(): InputStream
}

/** Converts a USA Native/Azahar export into our verified archive before any live save is touched. */
class NativeSaveImport {
    data class Prepared(val backup: SaveArchive.Backup, val source: String, val slots: List<Int>, val extraData: Boolean)
    companion object {
        private const val MAX_FILE = 64L * 1024 * 1024
        private const val MAX_TOTAL = 256L * 1024 * 1024
        private const val MAX_ENTRIES = 4096
        private val ID = Regex("[a-fA-F0-9]{32}")
    }

    private var entries = 0
    private var bytes = 0L
    private val visited = mutableSetOf<String>()

    private fun children(entry: SaveImportEntry): List<SaveImportEntry> {
        check(entry.directory) { "Choose the exported save folder, not a file." }
        val list = entry.children()
        check(list.size <= MAX_ENTRIES) { "This folder contains too many items. Choose one dated MH4U save folder." }
        check(list.map { it.name.lowercase() }.toSet().size == list.size) { "The export contains duplicate filenames." }
        check(list.all { validName(it.name) }) { "The export contains an unsupported filename." }
        return list
    }

    private fun validName(name: String) = name.isNotEmpty() && name !in setOf(".", "..") && name.length <= 255 &&
        name.none { it == '/' || it == '\\' || it == ':' || it.code < 32 }

    private fun child(entry: SaveImportEntry, name: String): SaveImportEntry? = children(entry).firstOrNull { it.name == name }
    private fun idFolders(entry: SaveImportEntry): List<SaveImportEntry> =
        children(entry).filter { it.directory && ID.matches(it.name) }.also {
            check(it.size <= 8) { "This folder contains too many profiles. Choose one exported MH4U save folder." }
        }
    private fun path(entry: SaveImportEntry, vararg names: String): SaveImportEntry? {
        var current = entry
        for (name in names) current = child(current, name) ?: return null
        return current
    }

    fun prepare(selected: SaveImportEntry, cache: File): Prepared {
        entries = 0; bytes = 0; visited.clear()
        check(cache.isDirectory || cache.mkdirs()) { "Could not prepare the save import." }
        val stage = Files.createTempDirectory(cache.toPath(), "native-save-").toFile()
        val archive = File(cache, "native-save-${UUID.randomUUID()}.zip")
        try {
            val sdmc = if (selected.name == "sdmc") selected else child(selected, "sdmc")
                ?: error("Choose the dated MH4U save folder containing sdmc, not its parent or the game files.")
            val nintendo = child(sdmc, "Nintendo 3DS") ?: error("This export has no Nintendo 3DS save data.")
            val candidates = mutableListOf<Pair<SaveImportEntry, SaveImportEntry>>()
            var european = false
            for (id0 in idFolders(nintendo)) {
                for (id1 in idFolders(id0)) {
                    if (path(id1, "title", "00040000", "00126100", "data") != null) european = true
                    val data = path(id1, "title", "00040000", "00126300", "data")
                    if (data != null) candidates += id1 to data
                }
            }
            check(candidates.size == 1) {
                if (candidates.isEmpty() && european) "This app needs an MH4U USA save. European saves are not imported automatically."
                else if (candidates.size > 1) "This export contains multiple MH4U saves. Choose one export at a time."
                else "No MH4U USA save was found in this folder."
            }
            val (id1, data) = candidates.single()
            val main = child(data, "00000001") ?: error("The exported character-save folder is missing.")
            val files = children(main)
            check(files.all { !it.directory && it.name in setOf("system", "user1", "user2", "user3") }) {
                "The character folder contains unsupported files. Choose an unmodified MH4U save export."
            }
            check(files.any { it.name == "system" }) { "The exported system save is missing." }
            val characters = files.filter { it.name in setOf("user1", "user2", "user3") }
            check(characters.isNotEmpty()) { "The export contains no saved character." }
            val saveTarget = File(stage, SaveArchive.SCOPES.getValue("save"))
            copy(main, File(saveTarget, "00000001"), 0)
            check(File(saveTarget, "00000001/system").length() == 512L) { "This does not look like a valid MH4U system save." }
            check(characters.all { File(saveTarget, "00000001/${it.name}").length() in 1 until 512L * 1024 }) {
                "The exported character save is empty or invalid."
            }
            child(data, "00000001.metadata")?.let {
                check(!it.directory) { "The exported save metadata is invalid." }
                copy(it, File(saveTarget, it.name), 0)
                check(File(saveTarget, it.name).length() == 16L) { "The exported save metadata is invalid." }
            }
            val extra = path(id1, "extdata", "00000000", "00001263")
            if (extra != null) copy(extra, File(stage, SaveArchive.SCOPES.getValue("extra")), 0)
            val repository = SaveArchive(stage)
            repository.export(archive)
            return Prepared(repository.validate(archive), selected.name.take(100), characters.map { it.name.last().digitToInt() }.sorted(), extra != null)
        } catch (error: Exception) {
            archive.delete()
            throw error
        } finally {
            stage.deleteRecursively()
        }
    }

    private fun copy(source: SaveImportEntry, target: File, depth: Int) {
        check(depth <= 12 && ++entries <= MAX_ENTRIES) { "The save export contains too many nested files." }
        check(visited.add(source.identity)) { "The export contains repeated or linked entries." }
        if (source.directory) {
            check(target.isDirectory || target.mkdirs()) { "Could not prepare the save files." }
            children(source).forEach { copy(it, File(target, it.name), depth + 1) }
            return
        }
        check(source.size <= MAX_FILE) { "A save file is too large. Choose the save export, not a game image." }
        checkNotNull(target.parentFile).mkdirs()
        var copied = 0L
        source.open().use { input -> target.outputStream().use { output ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer); if (count < 0) break
                copied += count; bytes += count
                check(copied <= MAX_FILE && bytes <= MAX_TOTAL) { "The save export is too large." }
                output.write(buffer, 0, count)
            }
            output.fd.sync()
        } }
        check(source.size < 0 || copied == source.size) { "A save file was not fully read. Export it again and retry." }
    }
}
