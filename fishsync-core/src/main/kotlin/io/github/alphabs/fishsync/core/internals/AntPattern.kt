package io.github.alphabs.fishsync.core.internals

import org.apache.tools.ant.types.selectors.TokenizedPath
import org.apache.tools.ant.types.selectors.TokenizedPattern

/** 패턴을 한 번 파싱하고 Ant의 경로 매칭 규칙을 사용한다. */
internal class AntPattern(
    pattern: String,
    private val insensitive: Boolean = false,
) {
    private val compiled = TokenizedPattern(pattern.replace('\\', '/'))

    fun matches(subject: String): Boolean =
        compiled.matchPath(TokenizedPath(subject.replace('\\', '/')), !insensitive)
}
