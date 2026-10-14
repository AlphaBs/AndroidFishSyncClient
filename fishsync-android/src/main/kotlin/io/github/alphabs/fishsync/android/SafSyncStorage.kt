package io.github.alphabs.fishsync.android

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import io.github.alphabs.fishsync.core.*
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** SAF 파일 교체는 백업을 통해 복구하지만 원자적 이름 변경은 보장하지 않는다. */
class SafSyncStorage(
    context: Context,
    treeUri: Uri,
) : SyncStorage {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val root =
        DocumentFile.fromTreeUri(context.applicationContext, treeUri) ?: error("Invalid folder URI")
    override val identity = treeUri.toString()
    private val reserved = ".fishsync-"

    private fun pieces(path: String): List<String> {
        val parts =
            relativePath(path)
                .split('/')
        require(parts.none { it.startsWith(reserved) }) { "Reserved synchronization filename" }
        return parts
    }

    private fun children(parent: DocumentFile): List<DocumentFile> {
        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(
                parent.uri,
                DocumentsContract.getDocumentId(parent.uri),
            )
        val result = mutableListOf<DocumentFile>()
        val cursor =
            resolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null,
                null,
                null,
            )
                ?: error("Provider failed to list folder; refusing synchronization")
        cursor.use {
            while (it.moveToNext()) {
                val uri = DocumentsContract.buildDocumentUriUsingTree(parent.uri, it.getString(0))
                result.add(
                    DocumentFile.fromSingleUri(appContext, uri)
                        ?: error("Invalid child document URI"),
                )
            }
        }
        return result
    }

    private fun child(
        parent: DocumentFile,
        name: String,
    ): DocumentFile? {
        val children = children(parent).filter { it.name == name }
        require(children.size <= 1) { "Ambiguous document name" }
        return children.singleOrNull()
    }

    private fun create(
        parent: DocumentFile,
        mime: String,
        name: String,
    ): DocumentFile {
        val uri =
            DocumentsContract.createDocument(resolver, parent.uri, mime, name)
                ?: error("Cannot create document")
        return DocumentFile.fromSingleUri(appContext, uri) ?: error("Invalid created document")
    }

    private fun rename(
        document: DocumentFile,
        name: String,
    ): DocumentFile {
        val uri =
            DocumentsContract.renameDocument(resolver, document.uri, name)
                ?: error("Provider cannot rename document")
        return DocumentFile.fromSingleUri(appContext, uri)?.also {
            require(it.name == name) { "Provider changed document name" }
        }
            ?: error("Invalid renamed document")
    }

    private fun findParent(
        path: String,
        create: Boolean,
    ): Pair<DocumentFile, String> {
        val parts = pieces(path)
        require(root.isDirectory && root.canRead()) { "Folder permission is unavailable" }
        var current = root
        for (name in parts.dropLast(1)) {
            val found =
                child(current, name)
                    ?: if (create) {
                        create(
                            current,
                            DocumentsContract.Document.MIME_TYPE_DIR,
                            name,
                        )
                    } else {
                        error("Folder not found")
                    }
            require(found.isDirectory && found.name == name) { "Invalid folder" }
            current = found
        }
        return current to parts.last()
    }

    private fun file(path: String): DocumentFile {
        val (parent, name) = findParent(path, false)
        return child(parent, name)?.also { require(it.isFile) } ?: error("File not found")
    }

    override fun list(): List<FileEntry> {
        require(root.isDirectory && root.canRead()) { "Folder permission is unavailable" }
        val result = mutableListOf<FileEntry>()
        val visited = hashSetOf<String>()

        fun walk(
            directory: DocumentFile,
            prefix: String,
            depth: Int,
        ) {
            require(
                depth <= 128 &&
                    visited.add(
                        directory.uri
                            .toString(),
                    ),
            ) { "Recursive or overly deep folder" }
            val seen = hashSetOf<String>()
            for (item in children(directory)) {
                val name = item.name ?: error("Document has no name")
                require(
                    name.isNotEmpty() && name !in listOf(".", "..") && !name.contains('/') &&
                        !name.contains('\\'),
                )
                require(seen.add(name)) { "Duplicate document name" }
                if (name.startsWith(reserved)) continue
                val path = relativePath(prefix + name)
                if (item.isDirectory) {
                    walk(item, "$path/", depth + 1)
                } else {
                    require(item.isFile) { "Unsupported document" }
                    val size =
                        resolver
                            .query(
                                item.uri,
                                arrayOf(DocumentsContract.Document.COLUMN_SIZE),
                                null,
                                null,
                                null,
                            )?.use { cursor ->
                                if (cursor.moveToFirst() &&
                                    !cursor.isNull(0)
                                ) {
                                    cursor.getLong(0)
                                } else {
                                    null
                                }
                            }
                    result.add(FileEntry(path, size))
                }
            }
        }
        walk(root, "", 0)
        return result.sortedBy { it.path }
    }

    override fun read(path: String): InputStream =
        resolver.openInputStream(file(path).uri) ?: error("Cannot read document")

    private fun supportsRename(document: DocumentFile): Boolean {
        val columns = arrayOf(DocumentsContract.Document.COLUMN_FLAGS)
        val cursor = resolver.query(document.uri, columns, null, null, null) ?: return false

        return cursor.use {
            if (!it.moveToFirst()) {
                return@use false
            }

            val flags = it.getLong(0)
            val renameFlag =
                DocumentsContract.Document.FLAG_SUPPORTS_RENAME
                    .toLong()
            flags and renameFlag != 0L
        }
    }

    override fun prepare(path: String): PendingWrite {
        val (parent, name) = findParent(path, true)
        require(parent.canWrite()) { "Folder is not writable" }
        val existing = child(parent, name)
        require(existing == null || existing.isFile && supportsRename(existing)) {
            "Provider cannot safely replace this file"
        }
        val stagingName = reserved + "staging-" + UUID.randomUUID()
        var temporary = create(parent, "application/octet-stream", stagingName)
        try {
            require(supportsRename(temporary)) { "Provider does not support rename" }
        } catch (failure: Exception) {
            temporary.delete()
            throw failure
        }
        return object : PendingWrite {
            private var committed = false

            override fun output(): OutputStream =
                resolver.openOutputStream(temporary.uri, "wt") ?: error("Cannot write document")

            override fun input(): InputStream =
                resolver.openInputStream(temporary.uri) ?: error("Cannot read temporary document")

            override fun commit() {
                val current = child(parent, name)
                require(current?.uri == existing?.uri) { "Destination changed" }
                val backupName = reserved + "backup-" + UUID.randomUUID()
                val backup = existing?.let { rename(it, backupName) }
                try {
                    temporary = rename(temporary, name)
                    committed = true
                } catch (failure: Exception) {
                    if (backup != null) {
                        try {
                            rename(backup, name)
                        } catch (restore: Exception) {
                            failure.addSuppressed(restore)
                            throw IllegalStateException(
                                "Restore failed; original remains at $backupName",
                                failure,
                            )
                        }
                    }
                    throw failure
                }
                // 백업 삭제가 실패해도 원본은 보존하며, 백업 문서는 동기화 목록에서 제외한다.
                backup?.delete()
            }

            override fun close() {
                if (!committed) temporary.delete()
            }
        }
    }

    override fun delete(path: String) {
        require(file(path).delete()) { "Cannot delete document" }
    }
}
