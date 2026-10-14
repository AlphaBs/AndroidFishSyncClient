package io.github.alphabs.fishsync.core.syncer

internal class SyncRuleEvaluator(
    rules: List<SyncRule>,
    private val context: SyncContext,
    caseInsensitive: Boolean,
) {
    private val compiled: List<Pair<SyncRule, Regex>>

    init {
        require(rules.isNotEmpty()) { "Explicit rules are required" }
        compiled = rules.map { rule -> rule to glob(rule.pattern, caseInsensitive) }
    }

    fun forPath(path: String): SyncRule {
        for ((rule, pattern) in compiled) {
            if (isEnabled(rule) && pattern.matches(path)) {
                return rule
            }
        }

        error("No matching rule for $path")
    }

    private fun isEnabled(rule: SyncRule): Boolean =
        rule.condition == SyncCondition.Always || context.isNewVersion || context.isForced
}

internal fun glob(
    pattern: String,
    insensitive: Boolean = false,
): Regex {
    require(pattern.isNotEmpty() && !pattern.startsWith('/'))

    val expression = GlobExpressionBuilder(pattern).build()
    val options = if (insensitive) setOf(RegexOption.IGNORE_CASE) else emptySet()

    return Regex(expression, options)
}

private class GlobExpressionBuilder(
    pattern: String,
) {
    private val pattern = pattern.replace('\\', '/')
    private val expression = StringBuilder("^")
    private var index = 0

    fun build(): String {
        while (index < pattern.length) {
            val character = pattern[index]

            when (character) {
                '*' -> appendWildcard()
                '?' -> expression.append("[^/]")
                '[' -> appendCharacterClass()
                '{', '}' -> error("Brace glob syntax is not supported in this version")
                else -> expression.append(Regex.escape(character.toString()))
            }

            index++
        }

        expression.append('$')
        return expression.toString()
    }

    private fun appendWildcard() {
        if (index + 1 >= pattern.length || pattern[index + 1] != '*') {
            expression.append("[^/]*")
            return
        }

        index++

        if (index + 1 < pattern.length && pattern[index + 1] == '/') {
            index++
            expression.append("(?:.*/)?")
        } else {
            expression.append(".*")
        }
    }

    private fun appendCharacterClass() {
        val end = pattern.indexOf(']', index + 1)
        require(end > index + 1) { "Invalid glob character class" }

        val characters = pattern.substring(index + 1, end)
        require(characters.none { it == '/' || it == '[' || it == '\\' })
        expression.append("(?=[^/])[")

        if (characters.startsWith('!')) {
            expression.append('^')
            expression.append(characters.drop(1))
        } else {
            expression.append(characters)
        }

        expression.append(']')
        index = end
    }
}
