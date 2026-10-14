package io.github.alphabs.fishsync.android

import android.database.Cursor
import android.database.MatrixCursor
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File

/** 테스트 APK 전용 제공자이며 라이브러리와 샘플 앱에는 포함하지 않는다. */
class TestDocumentsProvider : DocumentsProvider() {
    private val root get() =
        File(
            requireNotNull(context).cacheDir,
            "saf-fixture",
        ).apply { mkdirs() }
    private var rejectRename = false
    private var rejectQuery = false

    override fun onCreate() = true

    private fun file(id: String): File {
        require(id == "root" || id.startsWith("root/"))
        val file = if (id == "root") root else File(root, id.removePrefix("root/"))
        require(
            file.canonicalFile
                .toPath()
                .startsWith(
                    root.canonicalFile
                        .toPath(),
                ),
        )
        return file
    }

    private fun id(file: File) =
        if (file == root) {
            "root"
        } else {
            "root/" +
                file
                    .relativeTo(root)
                    .path
        }

    private val columns =
        arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
        )

    private fun row(
        cursor: MatrixCursor,
        file: File,
    ) {
        val mimeType = if (file.isDirectory) Document.MIME_TYPE_DIR else "application/octet-stream"
        val contentFlags =
            if (file.isDirectory) {
                Document.FLAG_DIR_SUPPORTS_CREATE
            } else {
                Document.FLAG_SUPPORTS_WRITE
            }
        val flags = Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME or contentFlags
        val values =
            mapOf<String, Any>(
                Document.COLUMN_DOCUMENT_ID to id(file),
                Document.COLUMN_DISPLAY_NAME to file.name,
                Document.COLUMN_MIME_TYPE to mimeType,
                Document.COLUMN_FLAGS to flags,
                Document.COLUMN_SIZE to file.length(),
            )
        val row =
            cursor.columnNames
                .map { values[it] }
                .toTypedArray()

        cursor.addRow(row)
    }

    override fun queryRoots(projection: Array<out String>?): Cursor =
        MatrixCursor(
            projection
                ?: arrayOf(
                    Root.COLUMN_ROOT_ID,
                    Root.COLUMN_DOCUMENT_ID,
                    Root.COLUMN_TITLE,
                    Root.COLUMN_FLAGS,
                ),
        ).apply {
            val values =
                mapOf<String, Any>(
                    Root.COLUMN_ROOT_ID to "fixture",
                    Root.COLUMN_DOCUMENT_ID to "root",
                    Root.COLUMN_TITLE to "FishSync fixture",
                    Root.COLUMN_FLAGS to Root.FLAG_SUPPORTS_CREATE,
                )
            addRow(
                columnNames
                    .map { values[it] }
                    .toTypedArray(),
            )
        }

    override fun queryDocument(
        documentId: String,
        projection: Array<out String>?,
    ): Cursor =
        MatrixCursor(projection ?: columns).also {
            val f = file(documentId)
            if (f.exists()) row(it, f)
        }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        if (rejectQuery) throw IllegalStateException("Test folder query failure")
        return MatrixCursor(projection ?: columns).also { c ->
            file(parentDocumentId)
                .listFiles()
                ?.forEach { row(c, it) }
        }
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode))

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String,
    ): String {
        require(!displayName.contains('/') && displayName !in listOf(".", ".."))
        val f = File(file(parentDocumentId), displayName)
        require(!f.exists())
        val created =
            if (mimeType == Document.MIME_TYPE_DIR) {
                f.mkdir()
            } else {
                f.createNewFile()
            }
        check(created)
        return id(f)
    }

    override fun renameDocument(
        documentId: String,
        displayName: String,
    ): String {
        if (rejectRename) throw UnsupportedOperationException("Test rename failure")
        val old = file(documentId)
        val next = File(old.parentFile, displayName)
        require(!next.exists())
        check(old.renameTo(next))
        return id(next)
    }

    override fun deleteDocument(documentId: String) {
        check(file(documentId).delete())
    }

    override fun isChildDocument(
        parentDocumentId: String,
        documentId: String,
    ) = file(documentId)
        .toPath()
        .startsWith(file(parentDocumentId).toPath())

    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle? {
        if (method == "resetFixture") {
            root.deleteRecursively()
            root.mkdirs()
            rejectRename = false
            rejectQuery = false
            return Bundle()
        }
        if (method == "grantFixture") {
            requireNotNull(context).grantUriPermission(
                requireNotNull(arg),
                android.net.Uri.parse(
                    "content://io.github.alphabs.fishsync.test.documents/tree/root",
                ),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
            )
            return Bundle()
        }
        if (method == "rejectQuery") {
            rejectQuery = true
            return Bundle()
        }
        if (method == "allowQuery") {
            rejectQuery = false
            return Bundle()
        }
        if (method == "rejectRename") {
            rejectRename = true
            return Bundle()
        }
        return super.call(method, arg, extras)
    }
}
