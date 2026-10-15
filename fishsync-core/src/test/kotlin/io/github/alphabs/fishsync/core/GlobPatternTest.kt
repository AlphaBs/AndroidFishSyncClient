package io.github.alphabs.fishsync.core

import io.github.alphabs.fishsync.core.internals.GlobPattern
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlobPatternTest {
    @Test
    fun recursivePatternsIncludeZeroOrMoreDirectories() {
        val pattern = GlobPattern("**/*.txt")
        assertTrue(pattern.matches("file.txt"))
        assertTrue(pattern.matches("a/b/file.txt"))
        assertTrue(GlobPattern("a/**/b").matches("a/b"))
        assertTrue(GlobPattern("a/**/b").matches("a/x/y/b"))
        assertFalse(GlobPattern("*.txt").matches("a/file.txt"))
    }

    @Test
    fun bracketsAndBracesAreLiteralAntCharacters() {
        assertFalse(GlobPattern("[ab]").matches("a"))
        assertTrue(GlobPattern("[ab]").matches("[ab]"))
        assertFalse(GlobPattern("{a,b}").matches("a"))
        assertTrue(GlobPattern("{a,b}").matches("{a,b}"))
        assertFalse(GlobPattern("[broken").matches("b"))
        assertTrue(GlobPattern("[broken").matches("[broken"))
    }

    @Test
    fun separatorsAndCasePolicyAreExplicit() {
        assertTrue(GlobPattern("a/**/*.txt").matches("a\\b\\file.txt"))
        assertTrue(GlobPattern("**/*.txt", true).matches("A/FILE.TXT"))
        assertFalse(GlobPattern("**/*.txt").matches("A/FILE.TXT"))
    }

    @Test
    fun singleWildcardsNeverCrossDirectoryBoundaries() {
        assertFalse(GlobPattern("a?b").matches("a/b"))
        assertFalse(GlobPattern("a*b").matches("a/x/b"))
        assertFalse(GlobPattern("*.txt").matches("dir/file.txt"))
        assertTrue(GlobPattern("a*b").matches("axyb"))
    }

    @Test
    fun insensitiveMatchingDoesNotConfuseLettersWithPunctuation() {
        assertFalse(GlobPattern("A.txt", true).matches("!.txt"))
        assertTrue(GlobPattern("A.txt", true).matches("a.txt"))
    }

    @Test
    fun unicodePatternMatchingUsesStandardKotlinComparison() {
        assertTrue(GlobPattern("İ.txt", true).matches("i.txt"))
        assertTrue(GlobPattern("I.txt", true).matches("ı.txt"))
    }

    @Test
    fun onlyStandaloneDoubleStarCrossesDirectories() {
        assertTrue(GlobPattern("ab**cd").matches("abXYZcd"))
        assertFalse(GlobPattern("ab**cd").matches("ab/x/cd"))
        assertFalse(GlobPattern("***").matches("a/b"))
        assertTrue(GlobPattern("a/**/**/b/**/c.txt").matches("a/b/c.txt"))
        assertTrue(GlobPattern("a/**/**/b/**/c.txt").matches("a/x/y/b/z/c.txt"))
    }

    @Test
    fun separatorsAndEmptyPathsHaveDefinedBehavior() {
        assertTrue(GlobPattern("a//b/").matches("a/b"))
        assertTrue(GlobPattern("/a/**").matches("/a/b"))
        assertFalse(GlobPattern("/a/**").matches("a/b"))
        assertFalse(GlobPattern("**").matches("/a/b"))
        assertTrue(GlobPattern("").matches(""))
        assertFalse(GlobPattern("").matches("a"))
        assertTrue(GlobPattern("**").matches(""))
        assertFalse(GlobPattern("*").matches(""))
    }

    @Test
    fun questionMarkConsumesOneKotlinCharAndStarsIncludeNewlines() {
        assertTrue(GlobPattern("a?b").matches("a\nb"))
        assertTrue(GlobPattern("*.txt").matches("a\nb.txt"))
        assertFalse(GlobPattern("?").matches("😀"))
        assertTrue(GlobPattern("??").matches("😀"))
    }

    @org.junit.Test(timeout = 5000)
    fun longPathsAndFailingWildcardPatternsDoNotUseRecursiveSearch() {
        val deepPath = "a/".repeat(20000) + "file.txt"
        assertTrue(GlobPattern("**/file.txt").matches(deepPath))
        assertFalse(GlobPattern("**/missing.txt").matches(deepPath))
        val pattern = "*a".repeat(80) + "b"
        val name = "a".repeat(8000)
        assertFalse(GlobPattern(pattern).matches(name))
        assertTrue(GlobPattern("?").matches("a"))
        assertTrue(GlobPattern("?".repeat(20000)).matches("a".repeat(20000)))
    }

    @Test
    fun boundedInputsMatchIndependentReference() {
        val patterns = combinations(listOf("a", "b", "?", "*", "a*", "*b", "**"), 3)
        val paths = combinations(listOf("a", "b", "aa", "ab"), 3)
        val segmentMatchers = mutableMapOf<String, Regex>()
        for (parts in patterns) {
            val pattern = parts.joinToString("/")
            val matcher = GlobPattern(pattern)
            for (names in paths) {
                val path = names.joinToString("/")
                val expected = referenceMatches(parts, names, 0, 0, segmentMatchers)
                assertEquals(expected, matcher.matches(path), "pattern=$pattern path=$path")
            }
        }
    }

    private fun combinations(
        alphabet: List<String>,
        maximumLength: Int,
    ): List<List<String>> {
        val result = mutableListOf(emptyList<String>())
        var level = listOf(emptyList<String>())
        repeat(maximumLength) {
            level = level.flatMap { prefix -> alphabet.map { prefix + it } }
            result.addAll(level)
        }
        return result
    }

    /** 작은 입력에서만 **의 소비 길이를 전수 열거하고 파일명은 정규식으로 대조한다. */
    private fun referenceMatches(
        parts: List<String>,
        names: List<String>,
        patternIndex: Int,
        nameIndex: Int,
        segmentMatchers: MutableMap<String, Regex>,
    ): Boolean {
        if (patternIndex == parts.size) return nameIndex == names.size
        val segment = parts[patternIndex]
        if (segment == "**") {
            return (nameIndex..names.size).any {
                referenceMatches(parts, names, patternIndex + 1, it, segmentMatchers)
            }
        }
        if (nameIndex == names.size) return false
        val regex =
            segmentMatchers.getOrPut(segment) {
                val expression =
                    segment
                        .map {
                            when (it) {
                                '*' -> ".*"
                                '?' -> "."
                                else -> Regex.escape(it.toString())
                            }
                        }.joinToString("")
                Regex(expression)
            }
        return regex.matches(names[nameIndex]) &&
            referenceMatches(parts, names, patternIndex + 1, nameIndex + 1, segmentMatchers)
    }
}
