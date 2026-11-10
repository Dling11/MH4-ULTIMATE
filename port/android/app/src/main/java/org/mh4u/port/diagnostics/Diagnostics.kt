package org.mh4u.port.diagnostics

import android.content.Context
import android.os.Build
import org.mh4u.port.*
import java.io.File
import java.io.RandomAccessFile
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Diagnostics {
    private fun nativeLog(): File? = listOf(File(Storage.root, "log/mh4u_log.txt"), File(Storage.root, "mh4u_log.txt")).firstOrNull { it.isFile }
    private fun tail(file: File): String = RandomAccessFile(file, "r").use { input ->
        input.seek((input.length() - 128 * 1024).coerceAtLeast(0))
        val bytes = ByteArray((input.length() - input.filePointer).coerceAtMost(128 * 1024).toInt())
        input.readFully(bytes); ReportText.sanitize(String(bytes, Charsets.UTF_8))
    }
    fun retainPreviousLog(context: Context) = runCatching {
        nativeLog()?.let { File(context.cacheDir, "previous-mh4u-log.txt").writeText(tail(it)) }
    }
    private fun ownLog(): String {
        val executor = Executors.newSingleThreadExecutor()
        var process: Process? = null
        return try {
            val running = ProcessBuilder("logcat", "-d", "--pid=${android.os.Process.myPid()}", "-t", "500").redirectErrorStream(true).start()
            process = running
            val reader = executor.submit<String> { running.inputStream.bufferedReader().use { ReportText.sanitize(it.readText()) } }
            if (!running.waitFor(2, TimeUnit.SECONDS)) running.destroyForcibly()
            reader.get(1, TimeUnit.SECONDS)
        } catch (_: Exception) { "App log unavailable on this device." }
        finally { process?.destroy(); executor.shutdownNow() }
    }
    fun create(context: Context): File {
        val folder = File(context.cacheDir, "reports").also { check(it.isDirectory || it.mkdirs()) }
        val file = File(folder, "MH4U-Rowell-error-report.zip")
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        val info = buildString {
            appendLine("Monster Hunter 4 $version")
            appendLine("Created: ${Instant.now()}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Game running: ${Native.isRunning()}")
            for (key in listOf("screens", "resolution", "vsync", "fps_counter")) appendLine("Display/$key: ${Prefs.getApp("Display", key, "default")}")
            appendLine("Game and save files are excluded. Private log details are filtered.")
        }
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(name: String, text: String) { zip.putNextEntry(ZipEntry(name)); zip.write(ReportText.sanitize(text).toByteArray()); zip.closeEntry() }
            entry("report.txt", info)
            entry("app-log.txt", ownLog())
            nativeLog()?.let { entry("native-log.txt", runCatching { tail(it) }.getOrDefault("Native log unavailable.")) }
            File(context.cacheDir, "previous-mh4u-log.txt").takeIf { it.isFile }?.let { entry("previous-native-log.txt", tail(it)) }
        }
        return file
    }
}
