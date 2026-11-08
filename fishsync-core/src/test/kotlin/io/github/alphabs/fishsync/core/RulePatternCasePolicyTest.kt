package io.github.alphabs.fishsync.core

import io.github.alphabs.fishsync.core.filecomparers.LocalFileChecksumComparer
import io.github.alphabs.fishsync.core.filecomparers.LocalFileSizeComparer
import io.github.alphabs.fishsync.core.files.SyncFileChecksum
import io.github.alphabs.fishsync.core.syncer.LocalSyncer
import io.github.alphabs.fishsync.core.syncer.ParallelSyncFilePairSyncer
import io.github.alphabs.fishsync.core.syncer.SyncAction
import io.github.alphabs.fishsync.core.syncer.SyncCondition
import io.github.alphabs.fishsync.core.syncer.SyncRule
import io.github.alphabs.fishsync.core.syncer.SyncRuleEvaluator
import io.github.alphabs.fishsync.core.syncer.SyncerOptions
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RulePatternCasePolicyTest {
    @Test
    fun patternCaseDefaultsToPathOptionsAndCanOverrideEitherDirection() {
        val cases =
            listOf(
                Triple(true, null, SyncAction.FullSync),
                Triple(false, null, SyncAction.UpdateOnly),
                Triple(true, false, SyncAction.UpdateOnly),
                Triple(false, true, SyncAction.FullSync),
            )
        for ((pathCaseInsensitive, ruleCaseInsensitive, expectedAction) in cases) {
            val evaluator =
                SyncRuleEvaluator(
                    SyncerOptions(
                        rules = rules(),
                        rulePatternCaseInsensitive = ruleCaseInsensitive,
                    ),
                    PathOptions(caseInsensitive = pathCaseInsensitive),
                )

            assertEquals(
                expectedAction,
                evaluator
                    .evaluate("files/existing.txt")
                    .action,
                "path=$pathCaseInsensitive, rule=$ruleCaseInsensitive",
            )
        }
    }

    @Test
    fun sensitivePatternsKeepInsensitivePairingAndSelectOrphansByTheirActualCase() =
        runBlocking<Unit> {
            val directory =
                Files
                    .createTempDirectory("rule-case-")
                    .toFile()
            try {
                val target = writeFile(directory, "FILES/existing.txt", "old")
                val preserved = writeFile(directory, "files/preserve.txt", "preserved")
                val deleted = writeFile(directory, "FILES/delete.txt", "deleted")
                val sources =
                    listOf("files/existing.txt", "files/missing.txt")
                        .map { path ->
                            testSyncFile(
                                path,
                                3,
                                SyncFileChecksum("md5", "202cb962ac59075b964b07152d234b70"),
                            ) {
                                "123".byteInputStream()
                            }
                        }
                val syncer =
                    LocalSyncer(
                        directory.absolutePath,
                        PathOptions(),
                        ParallelSyncFilePairSyncer(),
                    )
                val result =
                    syncer.compareAndSyncFiles(
                        sources,
                        SyncerOptions(
                            rules = rules(),
                            rulePatternCaseInsensitive = false,
                        ),
                    )

                assertEquals(
                    listOf("files/missing.txt"),
                    result.addedFiles
                        .map { it.path.subPath },
                )
                assertEquals(
                    listOf("files/existing.txt" to "FILES/existing.txt"),
                    result.identicalFilePairs
                        .map { it.source.path.subPath to it.target.path.subPath },
                )
                assertEquals(
                    listOf("files/missing.txt"),
                    result.updatedFilePairs
                        .map { it.source.path.subPath },
                )
                assertEquals(
                    listOf("FILES/delete.txt"),
                    result.deletedFiles
                        .map { it.path.subPath },
                )

                syncer.deleteLocalFiles(result.deletedFiles)

                assertEquals("old", target.readText())
                assertFalse(File(directory, "files/existing.txt").exists())
                assertEquals("123", File(directory, "files/missing.txt").readText())
                assertEquals("preserved", preserved.readText())
                assertFalse(deleted.exists())
            } finally {
                directory.deleteRecursively()
            }
        }

    private fun rules(): List<SyncRule> =
        listOf(
            SyncRule(
                action = SyncAction.FullSync,
                condition = SyncCondition.Always,
                pattern = "FILES/**",
                comparer = LocalFileChecksumComparer(),
            ),
            SyncRule(
                action = SyncAction.UpdateOnly,
                condition = SyncCondition.Always,
                pattern = "**",
                comparer = LocalFileSizeComparer(),
            ),
        )

    private fun writeFile(
        directory: File,
        path: String,
        contents: String,
    ): File {
        val file = File(directory, path)
        file.parentFile
            .mkdirs()
        file.writeText(contents)
        return file
    }
}
