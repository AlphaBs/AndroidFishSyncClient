package io.github.alphabs.fishsync.core.syncer

import io.github.alphabs.fishsync.core.PathHelper
import io.github.alphabs.fishsync.core.PathOptions
import io.github.alphabs.fishsync.core.internals.GlobPattern

internal class SyncRuleEvaluator(
    options: SyncerOptions,
    private val pathOptions: PathOptions,
) {
    private val context = requireNotNull(options.context) { "Context cannot be null" }
    private val compiled: List<Pair<SyncRule, GlobPattern>>

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
                rule to GlobPattern(pattern, pathOptions.caseInsensitive)
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
