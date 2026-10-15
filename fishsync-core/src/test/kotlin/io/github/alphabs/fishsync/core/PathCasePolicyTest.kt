package io.github.alphabs.fishsync.core

import io.github.alphabs.fishsync.core.files.SyncFile
import io.github.alphabs.fishsync.core.files.VirtualSyncFile
import io.github.alphabs.fishsync.core.syncer.SyncPathComparer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathCasePolicyTest {
    @Test
    fun insensitivePairingUsesStandardUnicodeComparisonAndKeepsInputOrder() {
        val result =
            SyncPathComparer().comparePaths(
                listOf(file("z.txt"), file("I.txt")),
                listOf(file("ı.txt"), file("Z.txt")),
                PathOptions(),
            )
        assertTrue("I.txt".equals("ı.txt", ignoreCase = true))
        assertTrue(
            result.addedFiles
                .isEmpty(),
        )
        assertTrue(
            result.deletedFiles
                .isEmpty(),
        )
        assertEquals(
            listOf("z.txt", "I.txt"),
            result.duplicatedFiles
                .map { it.source.path.subPath },
        )
        assertEquals(
            listOf("Z.txt", "ı.txt"),
            result.duplicatedFiles
                .map { it.target.path.subPath },
        )
    }

    @Test
    fun duplicatesUseTheSamePolicyAsPairing() {
        assertFailsWith<IllegalArgumentException> {
            SyncPathComparer().comparePaths(
                listOf(file("İ.txt"), file("i.txt")),
                emptyList(),
                PathOptions(),
            )
        }
    }

    @Test
    fun sensitiveComparisonKeepsDifferentNames() {
        val result =
            SyncPathComparer().comparePaths(
                listOf(file("I.txt")),
                listOf(file("ı.txt")),
                PathOptions(caseInsensitive = false),
            )
        assertEquals(
            listOf("I.txt"),
            result.addedFiles
                .map { it.path.subPath },
        )
        assertEquals(
            listOf("ı.txt"),
            result.deletedFiles
                .map { it.path.subPath },
        )
        assertTrue(
            result.duplicatedFiles
                .isEmpty(),
        )
    }

    @Test
    fun rootPrefixUsesStandardCaseComparisonAndStillChecksDirectoryBoundary() {
        assertTrue(PathHelper.isRootDirectory("/I", "/ı/file", PathOptions()))
        assertFalse(
            PathHelper.isRootDirectory("/I", "/ı/file", PathOptions(caseInsensitive = false)),
        )
        assertFalse(PathHelper.isRootDirectory("/I", "/ı-other/file", PathOptions()))
    }

    private fun file(path: String): SyncFile =
        VirtualSyncFile(RootedPath.fromSubPath(path, PathOptions()))
}
