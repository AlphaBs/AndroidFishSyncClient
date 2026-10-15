package io.github.alphabs.fishsync.core.internals

/** DotNet.Glob 3.1.3의 토큰화와 평가 순서를 유지한다. THIRD_PARTY_NOTICES.md 참조. */
internal class GlobPattern(
    pattern: String,
    private val insensitive: Boolean,
) {
    private data class Token(
        val kind: Char,
        val text: String = "",
        val negated: Boolean = false,
        val leading: Boolean = false,
        val trailing: Boolean = false,
    )

    private val tokens = tokenize(pattern)
    private val minimum = IntArray(tokens.size + 1)
    private val variable = BooleanArray(tokens.size + 1)

    init {
        variable[tokens.size] = true
        for (index in tokens.indices
            .reversed()) {
            val token = tokens[index]
            minimum[index] = minimum[index + 1] +
                when (token.kind) {
                    '*', 'D' -> 0
                    'L' -> token.text.length
                    else -> 1
                }
            variable[index] = token.kind in "*D" || (index + 1 < tokens.size && variable[index + 1])
        }
    }

    fun matches(subject: String): Boolean = evaluate(subject, 0, 0) != null

    private fun evaluate(
        subject: String,
        from: Int,
        position: Int,
    ): Int? {
        val remaining = subject.length - position
        if (variable[from]) {
            if (remaining < minimum[from]) return null
        } else if (remaining != minimum[from]) {
            return null
        }
        var current = position
        for (index in from until tokens.size) {
            val token = tokens[index]
            when (token.kind) {
                '*' -> return finish(subject, wildcard(subject, index + 1, current))
                'D' -> return finish(subject, directory(subject, index, current))
                'L' -> {
                    if (!OrdinalCase.regionMatches(subject, current, token.text, insensitive)) {
                        return null
                    }
                    current += token.text.length
                }
                '/' -> {
                    if (!separator(subject[current])) return null
                    current++
                }
                '?' -> {
                    if (separator(subject[current])) return null
                    current++
                }
                else -> {
                    val character = subject[current]
                    val found =
                        when (token.kind) {
                            'R', 'N' -> rangeContains(token, character)
                            else ->
                                token.text
                                    .any { sameCharacter(it, character) }
                        }
                    if (found == token.negated) return null
                    current++
                }
            }
        }
        return finish(subject, current)
    }

    private fun finish(
        subject: String,
        position: Int?,
    ): Int? {
        if (position == null || position < subject.length - 1) return null
        return position
    }

    private fun wildcard(
        subject: String,
        rest: Int,
        position: Int,
    ): Int? {
        if (rest == tokens.size) {
            if (position >= subject.length) return position
            if (subject
                    .substring(position)
                    .any { separator(it) }
            ) {
                return null
            }
            return position + subject.length
        }
        if (!variable[rest]) {
            val required = subject.length - minimum[rest]
            for (index in position until required) {
                if (separator(subject[index])) return null
            }
            return evaluate(subject, rest, required)
        }
        val maximum = subject.length - if (minimum[rest] > 0) minimum[rest] else 1
        var last = position
        for (index in position..maximum) {
            val result = evaluate(subject, rest, index)
            if (result != null) return result
            last = index
            if (separator(subject[index])) return null
        }
        return if (minimum[rest] == 0) last else null
    }

    private fun directory(
        subject: String,
        index: Int,
        position: Int,
    ): Int? {
        if (position !in subject.indices) return null
        val token = tokens[index]
        val rest = index + 1
        var current = position
        if (token.leading) {
            if (!separator(subject[current])) return null
            current++
        } else if (separator(subject[current])) {
            current++
        }
        if (minimum[rest] == 0) return subject.length
        val maximum = subject.length - minimum[rest]
        if (current > maximum) return null
        if (!variable[rest]) {
            if (maximum > 0 && !separator(subject[maximum - 1])) return null
            return evaluate(subject, rest, maximum)
        }
        if (token.trailing && separator(subject[current])) current++
        if (current == maximum) return evaluate(subject, rest, current)
        var matchedSeparator = false
        while (current <= maximum) {
            if (current == maximum && !matchedSeparator) return null
            val result = evaluate(subject, rest, current)
            if (result != null) return result
            if (current == maximum) return null
            matchedSeparator = false
            while (current < maximum) {
                current++
                if (separator(subject[current])) {
                    matchedSeparator = true
                    current++
                    break
                }
            }
        }
        return null
    }

    private fun rangeContains(
        token: Token,
        character: Char,
    ): Boolean {
        val start = token.text[0]
        val end = token.text[1]
        if (!insensitive || token.kind == 'N') return character in start..end
        return character in OrdinalCase.upper(start)..OrdinalCase.upper(end) ||
            character in OrdinalCase.lower(start)..OrdinalCase.lower(end)
    }

    private fun sameCharacter(
        first: Char,
        second: Char,
    ): Boolean =
        if (insensitive) OrdinalCase.upper(first) == OrdinalCase.upper(second) else first == second

    private fun tokenize(pattern: String): List<Token> {
        require(pattern.isNotEmpty()) { "Empty glob pattern" }
        val result = mutableListOf<Token>()
        var index = 0
        while (index < pattern.length) {
            when (val character = pattern[index]) {
                '[' -> {
                    var cursor = index + 1
                    val negated = pattern.getOrNull(cursor) == '!'
                    if (negated) cursor++
                    val first = pattern.getOrNull(cursor) ?: pattern[cursor - 1]
                    if (cursor < pattern.length) cursor++
                    val range = first.isLetterOrDigit() && pattern.getOrNull(cursor) == '-'
                    if (range) cursor++
                    val contents = StringBuilder().append(first)
                    while (cursor < pattern.length) {
                        val item = pattern[cursor++]
                        if (item == ']' && pattern.getOrNull(cursor) != ']') break
                        contents.append(item)
                    }
                    val kind =
                        if (range) {
                            if (first.isLetter()) 'R' else 'N'
                        } else {
                            'C'
                        }
                    if (range) require(contents.length >= 2) { "Incomplete glob range" }
                    result.add(Token(kind, contents.toString(), negated))
                    index = cursor
                }
                '*' -> {
                    if (pattern.getOrNull(index + 1) == '*') {
                        val leading =
                            result
                                .lastOrNull()
                                ?.kind == '/'
                        if (leading) result.removeAt(result.lastIndex)
                        index += 2
                        val trailing =
                            pattern
                                .getOrNull(index)
                                ?.let { separator(it) } == true
                        if (trailing) index++
                        result.add(Token('D', leading = leading, trailing = trailing))
                    } else {
                        result.add(Token('*'))
                        index++
                    }
                }
                '?' -> {
                    result.add(Token('?'))
                    index++
                }
                '/', '\\' -> {
                    result.add(Token('/'))
                    index++
                }
                else -> {
                    val start = index++
                    while (index < pattern.length && pattern[index] !in "*[?/\\") index++
                    result.add(Token('L', pattern.substring(start, index)))
                }
            }
        }
        return result
    }

    private fun separator(character: Char): Boolean = character == '/' || character == '\\'
}
