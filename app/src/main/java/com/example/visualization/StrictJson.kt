package com.example.visualization

/** A bounded JSON grammar: no coercion, duplicate keys, executable values or parser extensions. */
internal object StrictJson {
    private val reserved = setOf("__proto__", "prototype", "constructor", "toString", "valueOf", "hasOwnProperty", "__defineGetter__", "__defineSetter__", "__lookupGetter__", "__lookupSetter__", "isPrototypeOf", "propertyIsEnumerable", "toLocaleString")
    fun safeName(value: String) = value !in reserved
    fun validDate(value: String): Boolean {
        if (!value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) return false
        return validCalendar(value, "yyyy-MM-dd")
    }
    fun validPublicationTime(value: String): Boolean {
        if (!value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,6})?(Z|\\+00:00)"))) return false
        return validCalendar(value.take(19), "yyyy-MM-dd'T'HH:mm:ss")
    }
    private fun validCalendar(value: String, pattern: String): Boolean {
        val parser = java.text.SimpleDateFormat(pattern, java.util.Locale.US).apply { isLenient = false; timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val position = java.text.ParsePosition(0)
        return parser.parse(value, position) != null && position.index == value.length
    }
    fun parse(raw: String, maxBytes: Int, maxDepth: Int = 8): Any? {
        require(raw.toByteArray(Charsets.UTF_8).size <= maxBytes) { "JSON exceeds size limit" }
        return Parser(raw, maxDepth).parse()
    }
    private class Parser(val text: String, val maxDepth: Int) {
        var index = 0
        fun parse(): Any? { val result = value(0); whitespace(); require(index == text.length) { "Trailing JSON content" }; return result }
        fun whitespace() { while (index < text.length && text[index] in " \t\r\n") index++ }
        fun value(depth: Int): Any? {
            whitespace(); require(index < text.length) { "Missing JSON value" }
            return when (text[index]) {
                '{' -> obj(depth + 1)
                '[' -> array(depth + 1)
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> number()
            }
        }
        fun obj(depth: Int): Map<String, Any?> {
            require(depth <= maxDepth) { "JSON nesting exceeds limit" }; index++
            val result = linkedMapOf<String, Any?>(); whitespace()
            if (take('}')) return result
            while (true) {
                whitespace(); require(index < text.length && text[index] == '"') { "Object key must be a string" }
                val key = string(); require(safeName(key) && !result.containsKey(key)) { "Duplicate or reserved key" }
                whitespace(); require(take(':')) { "Missing colon" }; result[key] = value(depth)
                whitespace(); if (take('}')) return result; require(take(',')) { "Missing comma" }
            }
        }
        fun array(depth: Int): List<Any?> {
            require(depth <= maxDepth) { "JSON nesting exceeds limit" }; index++
            val result = mutableListOf<Any?>(); whitespace(); if (take(']')) return result
            while (true) { result += value(depth); whitespace(); if (take(']')) return result; require(take(',')) { "Missing comma" } }
        }
        fun take(c: Char): Boolean = if (index < text.length && text[index] == c) { index++; true } else false
        fun literal(token: String, result: Any?): Any? { require(text.startsWith(token, index)) { "Invalid literal" }; index += token.length; return result }
        fun string(): String {
            require(take('"')); val out = StringBuilder()
            while (index < text.length) {
                val c = text[index++]; if (c == '"') return out.toString().also { requireValidUnicode(it) }
                require(c.code >= 32) { "Control character in string" }
                if (c != '\\') out.append(c) else {
                    require(index < text.length); when (val escaped = text[index++]) {
                        '"', '\\', '/' -> out.append(escaped)
                        'b' -> out.append('\b'); 'f' -> out.append('\u000C'); 'n' -> out.append('\n'); 'r' -> out.append('\r'); 't' -> out.append('\t')
                        'u' -> { require(index + 4 <= text.length); val hex = text.substring(index, index + 4); require(hex.all { it in "0123456789abcdefABCDEF" }); out.append(hex.toInt(16).toChar()); index += 4 }
                        else -> error("Invalid JSON escape")
                    }
                }
            }
            error("Unterminated string")
        }
        fun number(): Number {
            val start = index; take('-'); require(index < text.length)
            if (!take('0')) { require(index < text.length && text[index] in '1'..'9'); while (index < text.length && text[index] in '0'..'9') index++ }
            var integer = true
            if (take('.')) { integer = false; val digits = index; while (index < text.length && text[index] in '0'..'9') index++; require(index > digits) }
            if (index < text.length && text[index] in "eE") { integer = false; index++; if (index < text.length && text[index] in "+-") index++; val digits = index; while (index < text.length && text[index] in '0'..'9') index++; require(index > digits) }
            val raw = text.substring(start, index)
            return if (integer) raw.toLongOrNull() ?: error("Integer overflow") else raw.toDouble().also { require(it.isFinite()) }
        }
        fun requireValidUnicode(value: String) {
            var i = 0
            while (i < value.length) { val c = value[i++]; if (c.isHighSurrogate()) { require(i < value.length && value[i].isLowSurrogate()); i++ } else require(!c.isLowSurrogate()) }
        }
    }
}
