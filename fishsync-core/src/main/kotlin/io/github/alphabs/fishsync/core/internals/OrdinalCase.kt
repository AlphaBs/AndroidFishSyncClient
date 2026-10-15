package io.github.alphabs.fishsync.core.internals

/** .NET OrdinalIgnoreCase처럼 문화권 및 다중 문자 확장을 적용하지 않는다. */
internal object OrdinalCase {
    fun upper(codePoint: Int): Int =
        if (codePoint ==
            0x0131
        ) {
            codePoint
        } else {
            Character.toUpperCase(codePoint)
        }

    fun upper(character: Char): Char = upper(character.code).toChar()

    fun lower(character: Char): Char =
        if (character ==
            '\u0130'
        ) {
            character
        } else {
            character.lowercaseChar()
        }

    fun key(value: String): String {
        val result = StringBuilder()
        val points =
            value
                .codePoints()
                .iterator()
        while (points.hasNext()) result.appendCodePoint(upper(points.nextInt()))
        return result.toString()
    }

    fun regionMatches(
        subject: String,
        start: Int,
        literal: String,
        insensitive: Boolean,
    ): Boolean {
        if (start < 0 || start + literal.length > subject.length) return false
        if (!insensitive) return subject.regionMatches(start, literal, 0, literal.length)
        var offset = 0
        while (offset < literal.length) {
            val expected = literal.codePointAt(offset)
            val actual = subject.codePointAt(start + offset)
            if (upper(expected) != upper(actual)) return false
            offset += Character.charCount(expected)
        }
        return true
    }
}
