package io.github.alphabs.fishsync.core.syncer

import io.github.alphabs.fishsync.core.PathHelper
import io.github.alphabs.fishsync.core.PathOptions

internal class SyncRuleEvaluator(
    options: SyncerOptions,
    private val pathOptions: PathOptions,
) {
    private val context = requireNotNull(options.context) { "Context cannot be null" }
    private val compiled: List<Pair<SyncRule, Regex>>

    init {
        val rules = requireNotNull(options.rules) { "Rules must be specified" }
        require(rules.isNotEmpty()) { "Rules must not be empty" }
        compiled =
            rules.map { rule ->
                require(
                    rule.action == SyncAction.Exclude || rule.comparer != null,
                ) { "Comparer is required" }
                val pattern =
                    PathHelper
                        .normalizePath(
                            rule.pattern,
                            pathOptions,
                        ).replace(pathOptions.pathSeparator, '/')
                rule to glob(pattern, pathOptions.caseInsensitive)
            }
    }

    fun evaluate(path: String): SyncRule {
        val normalized =
            PathHelper
                .normalizePath(
                    path,
                    pathOptions,
                ).replace(pathOptions.pathSeparator, '/')
        for ((rule, pattern) in compiled) {
            val enabled =
                rule.condition == SyncCondition.Always || context.isNewVersion || context.isForced
            if (enabled && pattern.matches(normalized)) return rule
        }
        throw IllegalArgumentException("No matching rule for $path")
    }
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
