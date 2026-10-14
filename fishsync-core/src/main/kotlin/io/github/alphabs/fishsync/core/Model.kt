package io.github.alphabs.fishsync.core

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

fun relativePath(value: String): String {
    val path = value.replace('\\', '/')
    require(path.isNotEmpty() && !path.startsWith('/') && !path.contains(':') && !path.contains('\u0000')) { "Invalid relative path" }
    require(path.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "Invalid relative path" }
    return path
}

data class Checksum(val algorithm: String, val hex: String) {
    init {
        val length = when (algorithm) { "md5" -> 32; "sha1" -> 40; else -> error("Unsupported checksum algorithm") }
        require(hex.length == length && hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) { "Invalid checksum" }
    }
}
data class FileEntry(val path: String, val size: Long?) {
    init { require(path == relativePath(path)); require(size == null || size >= 0) }
}
data class SourceFile(val path: String, val size: Long?, val checksum: Checksum?, val open: suspend () -> InputStream) {
    init { require(path == relativePath(path)); require(size == null || size >= 0) }
}
interface PendingWrite : AutoCloseable {
    fun output(): OutputStream
    fun input(): InputStream
    fun commit()
}
interface SyncStorage {
    val identity: String
    fun list(): List<FileEntry>
    fun read(path: String): InputStream
    fun prepare(path: String): PendingWrite
    fun delete(path: String)
}
enum class SyncAction { FullSync, UpdateOnly, InstallOnly, Exclude }
enum class SyncCondition { Always, OnNewVersion }
enum class Comparison { Size, Checksum, SizeAndChecksum }
data class SyncContext(val isNewVersion: Boolean = false, val isForced: Boolean = false)
data class SyncRule(val action: SyncAction, val pattern: String = "**", val condition: SyncCondition = SyncCondition.Always, val comparison: Comparison = Comparison.Checksum)
data class SyncPlan(val added: List<String>, val updated: List<String>, val deleted: List<String>, val identical: List<String>, val fingerprint: String = "") {
    val hasChanges: Boolean get() = added.isNotEmpty() || updated.isNotEmpty() || deleted.isNotEmpty()
}
data class SyncProgress(val path: String, val phase: String, val bytes: Long = 0, val total: Long? = null)
data class SyncResult(val plan: SyncPlan, val applied: List<String>, val deleted: List<String>)
class SyncFailure(val applied: List<String>, val deleted: List<String>, cause: Throwable) : Exception("Sync failed; some changes may already be applied", cause)
class IntegrityException(path: String) : Exception("Content verification failed: $path")
class ChangedFileException(path: String) : Exception("File changed during synchronization: $path")

suspend fun hash(input: InputStream, algorithm: String = "md5"): String {
    val digest = MessageDigest.getInstance(when (algorithm) { "md5" -> "MD5"; "sha1" -> "SHA-1"; else -> error("Unsupported checksum algorithm") })
    val buffer = ByteArray(65536)
    while (true) {
        currentCoroutineContext().ensureActive()
        val n = input.read(buffer)
        if (n < 0) break
        if (n > 0) digest.update(buffer, 0, n)
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
}
