package org.mh4u.port.overlay

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Named copies of the active landscape layout. Switching does not change game progress. */
class LayoutProfiles(private val folder: File) {
    data class Profile(val id: String, val name: String, val createdAt: Long)
    private fun file(id: String): File {
        require(id.matches(Regex("[a-f0-9-]{36}"))) { "Invalid layout profile." }
        return File(folder, "$id.json")
    }
    fun list(): List<Profile> = folder.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull { candidate ->
        runCatching { val o = JSONObject(file(candidate.nameWithoutExtension).readText()); Profile(candidate.nameWithoutExtension, o.getString("name"), o.getLong("createdAt")) }.getOrNull()
    }.sortedBy { it.name.lowercase() }

    fun save(name: String, layout: OverlayLayout): Profile {
        val clean = name.trim()
        require(clean.length in 1..32 && clean.none { it.code < 32 }) { "Use a layout name of 1–32 characters." }
        require(list().none { it.name.equals(clean, true) }) { "That name already exists. Choose another name." }
        check(folder.isDirectory || folder.mkdirs())
        val profile = Profile(UUID.randomUUID().toString(), clean, System.currentTimeMillis())
        val temporary = File.createTempFile("profile-", ".partial", folder)
        try {
            val json = JSONObject().put("version", 1).put("name", clean).put("createdAt", profile.createdAt).put("layout", layout.toJson())
            FileOutputStream(temporary).use { it.write(json.toString().toByteArray()); it.fd.sync() }
            Files.move(temporary.toPath(), file(profile.id).toPath())
        } finally { temporary.delete() }
        return profile
    }
    fun load(id: String): OverlayLayout {
        val o = JSONObject(file(id).readText())
        require(o.getInt("version") == 1) { "This layout profile is incompatible." }
        return OverlayLayout.fromJson(o.getJSONObject("layout")).ensureUtilityButtons(false)
    }
    fun delete(id: String) { check(file(id).delete()) { "The layout profile could not be removed." } }
}
