package io.github.alphabs.fishsync.android

import android.content.Context
import android.net.Uri
import io.github.alphabs.fishsync.core.PathOptions
import io.github.alphabs.fishsync.core.RootedPath
import io.github.alphabs.fishsync.core.filecomparers.FileComparerException
import io.github.alphabs.fishsync.core.files.LocalSyncFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import java.io.InputStream
import java.io.OutputStream

/** URI는 별도로 보관하며 path는 파일 시스템 루트가 없는 논리 상대 경로다. */
class SafSyncFile(
    context: Context,
    val treeUri: Uri,
    relativePath: String,
    pathOptions: PathOptions = PathOptions(),
) : LocalSyncFile(RootedPath.fromSubPath(relativePath, pathOptions)) {
    private val storage = SafSyncStorage(context, treeUri)
    private val documentPath =
        path.subPath
            .replace(pathOptions.pathSeparator, '/')
    override val exists get() =
        storage
            .list()
            .any { it.path == documentPath }
    override val length get() =
        storage
            .list()
            .firstOrNull { it.path == documentPath }
            ?.size
            ?: throw FileComparerException("Provider does not report file size")
    override val isReadable = true
    override val isWritable = true

    override suspend fun openReadStream(): InputStream = storage.read(documentPath)

    override suspend fun openWriteStream(): OutputStream {
        val pending = storage.prepare(documentPath)
        val output =
            try {
                pending.output()
            } catch (failure: Throwable) {
                pending.close()
                throw failure
            }
        val job = currentCoroutineContext()[Job]
        return object : OutputStream() {
            private var failed = false
            private var closed = false

            override fun write(value: Int) = write(byteArrayOf(value.toByte()))

            override fun write(
                bytes: ByteArray,
                offset: Int,
                length: Int,
            ) {
                try {
                    output.write(bytes, offset, length)
                } catch (failure: Throwable) {
                    failed = true
                    throw failure
                }
            }

            override fun flush() {
                try {
                    output.flush()
                } catch (failure: Throwable) {
                    failed = true
                    throw failure
                }
            }

            override fun close() {
                if (closed) return
                closed = true
                try {
                    output.close()
                    if (!failed && job?.isActive != false) pending.commit()
                } finally {
                    pending.close()
                }
            }
        }
    }
}
