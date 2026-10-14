package io.github.alphabs.fishsync.core

internal class RuleSet(rules: List<SyncRule>, context: SyncContext, caseInsensitive: Boolean) {
    private val compiled: List<Pair<SyncRule, Regex>>
    private val context = context
    init {
        require(rules.isNotEmpty()) { "Explicit rules are required" }
        compiled = rules.map { it to glob(it.pattern, caseInsensitive) }
    }
    fun forPath(path: String): SyncRule = compiled.firstOrNull { (rule, regex) ->
        (rule.condition == SyncCondition.Always || context.isNewVersion || context.isForced) && regex.matches(path)
    }?.first ?: error("No matching rule for $path")
}

internal fun glob(pattern: String, insensitive: Boolean = false): Regex {
    require(pattern.isNotEmpty() && !pattern.startsWith('/'))
    val p = pattern.replace('\\', '/')
    val out = StringBuilder("^")
    var i = 0
    while (i < p.length) {
        when (val c = p[i]) {
            '*' -> if (i + 1 < p.length && p[i + 1] == '*') {
                i++
                if (i + 1 < p.length && p[i + 1] == '/') { i++; out.append("(?:.*/)?") } else out.append(".*")
            } else out.append("[^/]*")
            '?' -> out.append("[^/]")
            '[' -> {
                val end = p.indexOf(']', i + 1)
                require(end > i + 1) { "Invalid glob character class" }
                val body = p.substring(i + 1, end)
                require(body.none { it == '/' || it == '[' || it == '\\' })
                out.append("(?=[^/])[")
                if (body.startsWith('!')) out.append('^').append(body.drop(1)) else out.append(body)
                out.append(']'); i = end
            }
            '{', '}' -> error("Brace glob syntax is not supported in this version")
            else -> out.append(Regex.escape(c.toString()))
        }
        i++
    }
    out.append('$')
    return Regex(out.toString(), if (insensitive) setOf(RegexOption.IGNORE_CASE) else emptySet())
}
