package io.github.alphabs.fishsync.core.internals

/** *·?는 경로 요소 안에서, 독립된 **는 디렉터리 0개 이상과 일치한다. */
internal class GlobPattern(
    pattern: String,
    private val ignoreCase: Boolean = false,
) {
    private val normalizedPattern = pattern.replace('\\', '/')
    private val segments = splitPath(normalizedPattern)

    fun matches(subject: String): Boolean {
        val normalizedSubject = subject.replace('\\', '/')
        if (normalizedPattern.startsWith('/') != normalizedSubject.startsWith('/')) return false
        val names = splitPath(normalizedSubject)
        var previous = BooleanArray(names.size + 1)
        var current = BooleanArray(names.size + 1)
        previous[0] = true

        for (segment in segments) {
            current.fill(false)
            if (segment == "**") {
                // 디렉터리를 소비하지 않거나 하나 더 소비하는 경우를 합친다.
                current[0] = previous[0]
                for (index in 1..names.size) {
                    current[index] = previous[index] || current[index - 1]
                }
            } else {
                for (index in 1..names.size) {
                    current[index] =
                        previous[index - 1] && matchesSegment(segment, names[index - 1])
                }
            }
            val completed = previous
            previous = current
            current = completed
        }
        return previous[names.size]
    }

    private fun matchesSegment(
        pattern: String,
        name: String,
    ): Boolean {
        if (pattern.all { it == '*' }) return true
        if (name.length < pattern.count { it != '*' }) return false
        if ('*' !in pattern) {
            return pattern.length == name.length &&
                pattern.indices.all {
                    pattern[it] == '?' || pattern[it].equals(name[it], ignoreCase)
                }
        }

        var previous = BooleanArray(name.length + 1)
        var current = BooleanArray(name.length + 1)
        previous[0] = true
        for (character in pattern) {
            current.fill(false)
            if (character == '*') {
                current[0] = previous[0]
                for (index in 1..name.length) {
                    current[index] = previous[index] || current[index - 1]
                }
            } else {
                for (index in 1..name.length) {
                    current[index] = previous[index - 1] &&
                        (character == '?' || character.equals(name[index - 1], ignoreCase))
                }
            }
            val completed = previous
            previous = current
            current = completed
        }
        return previous[name.length]
    }

    private fun splitPath(value: String): List<String> =
        value
            .split('/')
            .filter { it.isNotEmpty() }
}
