package io.github.alphabs.fishsync.client

import io.github.alphabs.fishsync.core.ChecksumAlgorithms
import io.github.alphabs.fishsync.core.PathOptions
import io.github.alphabs.fishsync.core.RootedPath
import io.github.alphabs.fishsync.core.filecomparers.LocalFileChecksumComparer
import io.github.alphabs.fishsync.core.files.ReadableHttpSyncFile
import io.github.alphabs.fishsync.core.files.SyncFile
import io.github.alphabs.fishsync.core.files.SyncFileChecksum
import io.github.alphabs.fishsync.core.files.SyncFileMetadata
import io.github.alphabs.fishsync.core.progress.ByteProgress
import io.github.alphabs.fishsync.core.progress.SyncFileByteProgress
import io.github.alphabs.fishsync.core.storage.FileEntry
import io.github.alphabs.fishsync.core.storage.FileSyncStorage
import io.github.alphabs.fishsync.core.storage.ReadableSyncStorage
import io.github.alphabs.fishsync.core.storage.StorageLocalFile
import io.github.alphabs.fishsync.core.storage.StorageSyncer
import io.github.alphabs.fishsync.core.storage.SyncProgress
import io.github.alphabs.fishsync.core.storage.WritableSyncStorage
import io.github.alphabs.fishsync.core.storage.relativePath
import io.github.alphabs.fishsync.core.syncer.SyncAction
import io.github.alphabs.fishsync.core.syncer.SyncCondition
import io.github.alphabs.fishsync.core.syncer.SyncFileCollectionComparerResult
import io.github.alphabs.fishsync.core.syncer.SyncRule
import io.github.alphabs.fishsync.core.syncer.SyncerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URI
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class FishSyncClient(
    serverUrl: String,
    private val tokenProvider: () -> String? = { null },
    http: OkHttpClient = defaultHttpClient(),
) {
    private val base =
        serverUrl
            .trimEnd('/')
            .toHttpUrl()
    private val transport = HttpTransport(http)
    private val downloadHttp = http
    private val json = Json { ignoreUnknownKeys = true }
    private val uploadProgress = MutableSharedFlow<SyncProgress>(extraBufferCapacity = 64)

    val progress = uploadProgress.asSharedFlow()

    suspend fun login(
        username: String,
        password: String,
    ): String {
        val body =
            buildJsonObject {
                put("username", username)
                put("password", password)
            }.toString()
        val request = apiRequest(apiUrl("auth", "login"), body)
        val response = transport.requestText(request)
        val token =
            json
                .parseToJsonElement(response)
                .jsonObject["token"]
                ?.jsonPrimitive
                ?.content

        if (token.isNullOrBlank()) {
            error("Missing login token")
        }

        return token
    }

    suspend fun listBuckets(): List<String> {
        val response = transport.requestText(apiRequest(apiUrl("buckets")))
        val buckets =
            json
                .parseToJsonElement(response)
                .jsonObject
                .getValue("buckets")
                .jsonArray

        return buckets.map { it.jsonPrimitive.content }
    }

    suspend fun bucketInfo(id: String): JsonObject {
        val request = apiRequest(apiUrl("buckets", "common", id))
        val response = transport.requestText(request)
        return json
            .parseToJsonElement(response)
            .jsonObject
    }

    suspend fun files(id: String): BucketFiles {
        val request = apiRequest(apiUrl("buckets", "common", id, "files"))
        val response = transport.requestText(request)
        val result = json.decodeFromString<BucketFiles>(response)
        validateRemoteFiles(result)

        return result
    }

    /** 의존 버킷을 먼저 병합하고 요청한 버킷의 파일을 우선한다. */
    suspend fun filesRecursively(
        id: String,
        maxBuckets: Int = 8,
    ): BucketFiles {
        require(maxBuckets > 0) { "Bucket limit must be positive" }
        val visited = linkedSetOf<String>()
        val merged = sortedMapOf<String, RemoteFile>(String.CASE_INSENSITIVE_ORDER)
        var root: BucketFiles? = null
        var latest: OffsetDateTime? = null

        suspend fun visit(bucketId: String) {
            currentCoroutineContext().ensureActive()
            if (bucketId in visited) return
            require(visited.size < maxBuckets) { "Bucket dependency limit exceeded" }
            visited.add(bucketId)
            val bucket = files(bucketId)
            if (root == null) root = bucket
            val updated =
                bucket.lastUpdated
                    ?.let(OffsetDateTime::parse)
            if (updated != null && (latest == null || updated.isAfter(latest))) {
                latest = updated
            }
            for (dependency in bucket.dependencies) {
                visit(dependency)
            }
            for (file in bucket.files) {
                merged[file.path] = file
            }
        }

        visit(id)
        return BucketFiles(
            id = requireNotNull(root).id ?: id,
            lastUpdated = latest?.toString(),
            files =
                merged.values
                    .toList(),
            dependencies = visited.toList(),
        )
    }

    suspend fun pull(
        id: String,
        storage: WritableSyncStorage,
        rules: List<SyncRule> = defaultRules(),
    ): SyncFileCollectionComparerResult {
        val sources = sources(filesRecursively(id))
        val progressByPath = ConcurrentHashMap<String, ByteProgress>()
        val options =
            SyncerOptions(
                rules = rules,
                byteProgress = { event: SyncFileByteProgress ->
                    val path = event.syncFile.path.subPath
                    val current =
                        progressByPath.compute(path) { _, previous ->
                            (previous ?: ByteProgress()) + event.progress
                        }!!
                    uploadProgress.tryEmit(
                        SyncProgress(path, "download", current.progressedBytes, current.totalBytes),
                    )
                },
            )
        return StorageSyncer(storage).compareAndSyncFiles(sources, options)
    }

    /** 전송용 복사본은 동기화 루트 밖의 앱 전용 임시 폴더에 보관한다. */
    suspend fun push(
        id: String,
        storage: ReadableSyncStorage,
        scratchDirectory: File,
        maxRounds: Int = 10,
    ): PushResponse = uploadFiles(id, storage, scratchDirectory, maxRounds)

    private fun apiUrl(vararg segments: String): HttpUrl {
        val builder = base.newBuilder()

        for (segment in segments) {
            require(segment.isNotBlank() && segment != "." && segment != "..") {
                "Invalid API path segment"
            }
            builder.addPathSegment(segment)
        }

        return builder.build()
    }

    private fun apiRequest(
        url: HttpUrl,
        body: String? = null,
    ): Request {
        val builder =
            Request
                .Builder()
                .url(url)
        val token = tokenProvider()

        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }

        if (body != null) {
            builder.post(body.toRequestBody("application/json".toMediaType()))
        }

        return builder.build()
    }

    private fun validateRemoteFiles(result: BucketFiles) {
        val seen = sortedSetOf(String.CASE_INSENSITIVE_ORDER)

        for (file in result.files) {
            require(relativePath(file.path) == file.path)
            require(seen.add(file.path)) { "Duplicate remote path" }
            require(file.metadata.size >= 0)
            require(
                file.metadata.checksum.length == 32 &&
                    file.metadata.checksum
                        .all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' },
            ) { "Invalid server MD5 checksum" }
        }
    }

    private fun sources(files: BucketFiles): List<SyncFile> =
        files.files.map { file ->
            val remote =
                ReadableHttpSyncFile(
                    RootedPath.fromSubPath(file.path, PathOptions()),
                    downloadHttp,
                    SyncFileMetadata(
                        file.metadata.size,
                        SyncFileChecksum("md5", file.metadata.checksum),
                    ),
                )
            remote.location =
                file.location
                    ?.let(URI::create)
            remote
        }

    private suspend fun uploadFiles(
        id: String,
        storage: ReadableSyncStorage,
        scratchDirectory: File,
        maxRounds: Int,
    ): PushResponse =
        withContext(Dispatchers.IO) {
            require(maxRounds in 1..20)
            validateScratchDirectory(storage, scratchDirectory)

            val copies = linkedMapOf<String, File>()

            try {
                val snapshot = createUploadSnapshot(storage, scratchDirectory, copies)
                completePush(id, snapshot, copies, maxRounds)
            } finally {
                copies.values
                    .forEach { it.delete() }
            }
        }

    private fun validateScratchDirectory(
        storage: ReadableSyncStorage,
        directory: File,
    ) {
        if (storage is FileSyncStorage) {
            val scratchPath =
                directory.canonicalFile
                    .toPath()
            val rootPath = File(storage.identity).toPath()
            require(!scratchPath.startsWith(rootPath)) {
                "Upload scratch space must be outside the sync root"
            }
        }

        require(directory.isDirectory || directory.mkdirs()) {
            "Cannot create upload scratch space"
        }
    }

    private suspend fun createUploadSnapshot(
        storage: ReadableSyncStorage,
        directory: File,
        copies: MutableMap<String, File>,
    ): List<UploadFile> {
        val seen = sortedSetOf(String.CASE_INSENSITIVE_ORDER)
        val snapshot = mutableListOf<UploadFile>()

        for (entry in storage.list()) {
            require(seen.add(entry.path)) { "Duplicate local path" }
            val temporary = File.createTempFile("fishsync-upload-", ".tmp", directory)
            copies[entry.path] = temporary
            copyUploadFile(storage, entry.path, temporary)

            val checksum =
                temporary
                    .inputStream()
                    .use { ChecksumAlgorithms.computeHashAsync("md5", it) }
            snapshot.add(UploadFile(entry.path, temporary.length(), checksum))
        }

        return snapshot
    }

    private suspend fun copyUploadFile(
        storage: ReadableSyncStorage,
        path: String,
        target: File,
    ) {
        val source = StorageLocalFile(storage, FileEntry(path, null))
        target.outputStream().use { output ->
            source.copyTo(output)
        }
    }

    private suspend fun completePush(
        id: String,
        snapshot: List<UploadFile>,
        copies: Map<String, File>,
        maxRounds: Int,
    ): PushResponse {
        val body = json.encodeToString(UploadRequest(snapshot))

        repeat(maxRounds) {
            val request = apiRequest(apiUrl("buckets", "common", id, "sync"), body)
            val response = transport.requestText(request)
            val result = json.decodeFromString<PushResponse>(response)
            if (result.isSuccess) {
                return result
            }

            require(
                result.requiredActions
                    .isNotEmpty(),
            ) {
                "Server returned no actionable changes"
            }

            for (required in result.requiredActions) {
                val upload = createUploadRequest(required, copies)
                transport.requestText(upload, upload = true)
            }
        }

        error("Server synchronization exceeded $maxRounds rounds")
    }

    private fun createUploadRequest(
        required: RequiredAction,
        copies: Map<String, File>,
    ): Request {
        require(required.path == relativePath(required.path))
        require(required.action.type == "Http") { "Unsupported server sync action" }
        val parameters = required.action.parameters ?: error("Missing action parameters")
        val copy = copies[required.path] ?: error("Server requested a file outside the snapshot")
        val contentType =
            parameters.entries
                .firstOrNull {
                    it.key
                        .equals("headers.Content-Type", true)
                }?.value
                ?: "application/octet-stream"
        val body =
            UploadRequestBody(copy, required.path, contentType) {
                uploadProgress.tryEmit(it)
            }

        val method = parameters["method"] ?: error("Missing HTTP method")
        require(method == "PUT" || method == "POST") { "Unsupported upload method" }
        val url = parameters["url"]?.toHttpUrl() ?: error("Missing upload URL")
        val builder =
            Request
                .Builder()
                .url(url)
                .method(method, body)

        for ((key, value) in parameters) {
            if (key.startsWith("headers.")) {
                builder.header(key.removePrefix("headers."), value)
            }
        }

        return builder.build()
    }

    companion object {
        fun defaultRules() =
            listOf(
                SyncRule(
                    action = SyncAction.FullSync,
                    condition = SyncCondition.Always,
                    pattern = "**",
                    comparer = LocalFileChecksumComparer(),
                ),
            )
    }
}

private fun defaultHttpClient(): OkHttpClient =
    OkHttpClient
        .Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

@Serializable
private data class UploadFile(
    val path: String,
    val size: Long,
    val checksum: String,
)

@Serializable
private data class UploadRequest(
    val files: List<UploadFile>,
)
