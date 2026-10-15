package io.github.alphabs.fishsync.core.internals

/** String.Trim이 사용하는 .NET 공백 집합을 JVM의 공백 판정과 구분한다. */
internal object DotNetWhitespace {
    private val singles =
        charArrayOf(
            '\u0020',
            '\u0085',
            '\u00A0',
            '\u1680',
            '\u2028',
            '\u2029',
            '\u202F',
            '\u205F',
            '\u3000',
        )

    fun trim(value: String): String =
        value.trim { character ->
            character in '\u0009'..'\u000D' || character in '\u2000'..'\u200A' ||
                character in singles
        }
}
