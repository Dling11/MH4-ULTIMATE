package org.mh4u.port.diagnostics

/** Reports are opt-in; keep logs bounded and omit paths, chat, invite details and identifiers. */
object ReportText {
    private val privateLine = Regex("(?i)password|passphrase|authorization|bearer|cookie|session[_ -]?id|nickname|chat|invite|token|ssid|serial|android.?id|bluetooth|keyboard|/sdcard/|/storage/|/data/user/|\\.(3ds|cci|cxi|cia)\\b|room[_ ]?(name|host)|[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}")
    private val address = Regex("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?::[0-9]+)?\\b|(?i)\\b(?:[0-9a-f]{1,4}:){3,}[0-9a-f:]+\\b|(?i)\\b[0-9a-f]{0,4}::[0-9a-f:]+\\b")
    private val link = Regex("(?i)\\b[a-z][a-z0-9+.-]*://\\S+")
    fun sanitize(text: String): String = text.lineSequence().takeLastBounded(500).joinToString("\n") { line ->
        if (privateLine.containsMatchIn(line)) "[Private detail omitted]"
        else address.replace(link.replace(line.take(4096), "[Link omitted]"), "[Address omitted]")
    }.takeLast(128 * 1024)
    private fun Sequence<String>.takeLastBounded(count: Int): List<String> {
        val tail = ArrayDeque<String>()
        for (line in this) { tail.addLast(line); if (tail.size > count) tail.removeFirst() }
        return tail.toList()
    }
}
