package io.github.alphabs.fishsync.android.internals

import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import io.github.alphabs.fishsync.core.filecomparers.FileComparerException
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** 문서 ID는 URI로만 다루며 논리 상대 경로의 표시 이름과 분리한다. */
internal class SafFileAccess(
    context: Context,
    treeUri: Uri,
) {
    private val application = context.applicationContext
    private val resolver = application.contentResolver
    private val root =
        DocumentFile.fromTreeUri(application, treeUri)
            ?: throw IllegalArgumentException("Invalid tree URI")

    fun listPaths(): List<String> {
        requireRoot()
        val result = mutableListOf<String>()
        val pending = ArrayDeque<Pair<DocumentFile, String>>()
        val visited = hashSetOf<String>()
        pending.add(root to "")
        while (pending.isNotEmpty()) {
            val (directory, prefix) = pending.removeLast()
            check(
                visited.add(
                    directory.uri
                        .toString(),
                ),
            ) { "Recursive document tree" }
            val names = hashSetOf<String>()
            for (child in children(directory)) {
                val name = requireNotNull(child.name)
                validateName(name)
                check(names.add(name)) { "Ambiguous document name" }
                val path = prefix + name
                if (child.isDirectory) {
                    pending.add(child to "$path/")
                } else {
                    check(child.isFile) { "Unsupported document" }
                    result.add(path)
                }
            }
        }
        return result
    }

    fun exists(path: String): Boolean = find(path, false)?.isFile == true

    fun length(path: String): Long {
        val file = find(path, false) ?: throw FileNotFoundException(path)
        val columns = arrayOf(DocumentsContract.Document.COLUMN_SIZE)
        val cursor =
            resolver.query(file.uri, columns, null, null, null)
                ?: throw IOException("Cannot query document size")
        return cursor.use {
            if (!it.moveToFirst() ||
                it.isNull(0)
            ) {
                throw FileComparerException("Unknown document size")
            }
            val value = it.getLong(0)
            if (value < 0) throw FileComparerException("Unknown document size")
            value
        }
    }

    fun openRead(
        path: String,
        signal: CancellationSignal,
    ): InputStream {
        val file = find(path, false, signal) ?: throw FileNotFoundException(path)
        check(file.isFile)
        val descriptor =
            resolver.openAssetFileDescriptor(file.uri, "r", signal)
                ?: throw IOException("Cannot open document")
        try {
            return descriptor.createInputStream()
        } catch (failure: Throwable) {
            descriptor.close()
            throw failure
        }
    }

    fun openWrite(
        path: String,
        signal: CancellationSignal,
    ): OutputStream {
        val file = find(path, true, signal) ?: throw IOException("Cannot create document")
        check(file.isFile)
        val descriptor =
            resolver.openAssetFileDescriptor(file.uri, "wt", signal)
                ?: throw IOException("Cannot write document")
        try {
            return descriptor.createOutputStream()
        } catch (failure: Throwable) {
            descriptor.close()
            throw failure
        }
    }

    fun delete(path: String) {
        val file = find(path, false) ?: return
        check(file.isFile) { "Cannot delete a directory as a file" }
        check(file.delete()) { "Provider refused deletion" }
    }

    private fun requireRoot() {
        // URI 권한 사전 조회는 제공자의 실제 접근 가능 여부와 다를 수 있다.
        // 조회·열기·생성 시 ContentResolver가 권한을 검사하도록 한다.
        check(root.isDirectory) { "Not a document directory" }
    }

    private fun validateName(name: String) {
        require(name.isNotEmpty() && name != "." && name != "..")
        require(name.none { it == '/' || it == '\\' || it == '\u0000' }) { "Invalid document name" }
    }

    private fun find(
        path: String,
        create: Boolean,
        signal: CancellationSignal? = null,
    ): DocumentFile? {
        requireRoot()
        val parts = path.split('/')
        parts.forEach(::validateName)
        var current = root
        for ((index, name) in parts.withIndex()) {
            signal?.throwIfCanceled()
            val matches = children(current, signal).filter { it.name == name }
            check(matches.size <= 1) { "Ambiguous document name" }
            val child = matches.singleOrNull()
            val last = index == parts.lastIndex
            if (child != null) {
                check(last || child.isDirectory) { "Not a directory" }
                current = child
            } else if (!create) {
                return null
            } else {
                val mime =
                    if (last) {
                        "application/octet-stream"
                    } else {
                        DocumentsContract.Document.MIME_TYPE_DIR
                    }
                val uri =
                    DocumentsContract.createDocument(resolver, current.uri, mime, name)
                        ?: throw IOException("Cannot create document")
                val created =
                    DocumentFile.fromSingleUri(application, uri)
                        ?: throw IOException("Invalid created document")
                check(created.name == name) { "Provider changed document name" }
                current = created
            }
        }
        return current
    }

    private fun children(
        parent: DocumentFile,
        signal: CancellationSignal? = null,
    ): List<DocumentFile> {
        val uri =
            DocumentsContract.buildChildDocumentsUriUsingTree(
                parent.uri,
                DocumentsContract.getDocumentId(parent.uri),
            )
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val cursor =
            resolver.query(uri, columns, null, null, null, signal)
                ?: throw IOException("Cannot enumerate documents")
        return cursor.use {
            val result = mutableListOf<DocumentFile>()
            while (it.moveToNext()) {
                val childUri =
                    DocumentsContract.buildDocumentUriUsingTree(
                        parent.uri,
                        it.getString(0),
                    )
                result.add(
                    DocumentFile.fromSingleUri(application, childUri)
                        ?: throw IOException("Invalid document URI"),
                )
            }
            result
        }
    }
}
