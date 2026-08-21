package studio.airpen.app.type

import studio.airpen.app.data.TypeSettings

class AirTypeEngine {
    var settings: TypeSettings = TypeSettings()
    var shift: Boolean = false
    var capsLock: Boolean = false
    val buffer = StringBuilder()
    val suggestions = ArrayList<String>(6)

    fun consumeLetter(raw: String): String {
        if (raw == "⇧") {
            shift = !shift
            return ""
        }
        if (raw == "⌫" || raw == " ") return raw
        if (raw == "\n") {
            buffer.clear()
            suggestions.clear()
            return raw
        }
        var ch = raw
        if (ch.length == 1 && ch[0].isLetter()) {
            ch = if (capsLock || shift) ch.uppercase() else ch.lowercase()
            if (shift && !capsLock) shift = false
        }
        buffer.append(ch)
        refreshSuggestions()
        val out = if (settings.autoSpace && ch.length == 1 && ch[0].isLetter()) ch else ch
        return out
    }

    fun pickSuggestion(index: Int): String? {
        val word = suggestions.getOrNull(index) ?: return null
        val typed = buffer.toString()
        val extra = if (typed.isEmpty()) word else word.removePrefix(typed.lowercase()).ifBlank { word }
        buffer.clear()
        buffer.append(word)
        return if (settings.autoSpace) "$extra " else extra
    }

    fun backspace() {
        if (buffer.isNotEmpty()) buffer.deleteCharAt(buffer.lastIndex)
        refreshSuggestions()
    }

    private fun refreshSuggestions() {
        suggestions.clear()
        if (!settings.suggestions) return
        val prefix = buffer.toString().lowercase()
        if (prefix.length < 2) return
        DICT.asSequence()
            .filter { it.startsWith(prefix) && it != prefix }
            .take(6)
            .forEach { suggestions += it }
    }

    companion object {
        val DICT = listOf(
            "the", "and", "you", "that", "for", "with", "this", "have", "from", "they",
            "please", "thanks", "thank", "hello", "yes", "no", "okay", "because", "would",
            "could", "should", "about", "after", "before", "where", "when", "what", "which",
            "people", "time", "good", "great", "love", "like", "just", "know", "think",
            "make", "made", "want", "need", "going", "today", "tomorrow", "yesterday",
            "meeting", "message", "email", "phone", "samsung", "android", "gallery",
            "camera", "settings", "volume", "brightness", "screenshot", "notification",
            "calendar", "reminder", "alarm", "music", "video", "photo", "download",
            "upload", "search", "google", "youtube", "chrome", "maps", "drive",
            "document", "folder", "password", "username", "address", "number",
            "morning", "evening", "night", "sorry", "welcome", "congratulations",
        )
    }
}
