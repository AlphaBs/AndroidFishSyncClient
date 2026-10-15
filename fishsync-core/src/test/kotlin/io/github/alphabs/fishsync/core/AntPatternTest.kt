package io.github.alphabs.fishsync.core

import io.github.alphabs.fishsync.core.internals.AntPattern
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AntPatternTest {
    @Test
    fun recursivePatternsIncludeZeroOrMoreDirectories() {
        val pattern = AntPattern("**/*.txt")
        assertTrue(pattern.matches("file.txt"))
        assertTrue(pattern.matches("a/b/file.txt"))
        assertTrue(AntPattern("a/**/b").matches("a/b"))
        assertTrue(AntPattern("a/**/b").matches("a/x/y/b"))
        assertFalse(AntPattern("*.txt").matches("a/file.txt"))
    }

    @Test
    fun bracketsAndBracesAreLiteralAntCharacters() {
        assertFalse(AntPattern("[ab]").matches("a"))
        assertTrue(AntPattern("[ab]").matches("[ab]"))
        assertFalse(AntPattern("{a,b}").matches("a"))
        assertTrue(AntPattern("{a,b}").matches("{a,b}"))
        assertFalse(AntPattern("[broken").matches("b"))
        assertTrue(AntPattern("[broken").matches("[broken"))
    }

    @Test
    fun separatorsAndCasePolicyAreExplicit() {
        assertTrue(AntPattern("a/**/*.txt").matches("a\\b\\file.txt"))
        assertTrue(AntPattern("**/*.txt", true).matches("A/FILE.TXT"))
        assertFalse(AntPattern("**/*.txt").matches("A/FILE.TXT"))
    }
}
