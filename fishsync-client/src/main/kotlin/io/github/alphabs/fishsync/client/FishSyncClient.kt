package io.github.alphabs.fishsync.client

import io.github.alphabs.fishsync.core.files.Checksum
import io.github.alphabs.fishsync.core.files.FileSyncStorage
import io.github.alphabs.fishsync.core.files.SourceFile
import io.github.alphabs.fishsync.core.files.SyncStorage
import io.github.alphabs.fishsync.core.hash
import io.github.alphabs.fishsync.core.internals.StorageLocks
import io.github.alphabs.fishsync.core.progress.SyncProgress
import io.github.alphabs.fishsync.core.relativePath
import io.github.alphabs.fishsync.core.syncer.ChangedFileException
import io.github.alphabs.fishsync.core.syncer.LocalSyncer
import io.github.alphabs.fishsync.core.syncer.SyncAction
import io.github.alphabs.fishsync.core.syncer.SyncFileCollectionSyncer
import io.github.alphabs.fishsync.core.syncer.SyncPlan
import io.github.alphabs.fishsync.core.syncer.SyncResult
import io.github.alphabs.fishsync.core.syncer.SyncRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge
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
import java.util.Locale
import java.util.concurrent.TimeUnit

class FishSyncClient(
    serverUrl: String,
    private val tokenProvider: () -> String? = { null },
    http: OkHttpClient = defaultHttpClient(),
    val collectionSyncer: SyncFileCollectionSyncer =
        SyncFileCollectionSyncer(caseInsensitive = true),
) {
    private val base =
        serverUrl
            .trimEnd('/')
            .toHttpUrl()
    private val transport = HttpTransport(http)
    private val json = Json { ignoreUnknownKeys = true }
    private val uploadProgress = MutableSharedFlow<SyncProgress>(extraBufferCapacity = 64)

    val progress get() = merge(collectionSyncer.progress, uploadProgress)

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

    suspend fun comparePull(
        id: String,
        storage: SyncStorage,
        rules: List<SyncRule> = defaultRules(),
    ): SyncPlan {
        val sources = sources(files(id))
        return LocalSyncer(storage, collectionSyncer).compareFiles(sources, rules)
    }

    suspend fun pull(
        id: String,
        storage: SyncStorage,
        rules: List<SyncRule> = defaultRules(),
        expectedPlan: SyncPlan? = null,
    ): SyncResult {
        val sources = sources(files(id))
        return LocalSyncer(storage, collectionSyncer).compareAndSyncFiles(
            sources,
            rules,
            expectedPlan = expectedPlan,
        )
    }

    suspend fun comparePush(
        id: String,
        storage: SyncStorage,
    ): SyncPlan =
        withContext(Dispatchers.IO) {
            val remote = files(id)
            val local =
                storage.list().map { entry ->
                    val checksum =
                        storage
                            .read(entry.path)
                            .use { hash(it) }
                    UploadFile(entry.path, entry.size ?: -1, checksum)
                }

            pushPlan(remote, local)
        }

    /** 전송용 복사본은 동기화 루트 밖의 앱 전용 임시 폴더에 보관한다. */
    suspend fun push(
        id: String,
        storage: SyncStorage,
        scratchDirectory: File,
        maxRounds: Int = 10,
        expectedPlan: SyncPlan? = null,
    ): PushResponse =
        StorageLocks.run(storage) {
            pushUnlocked(id, storage, scratchDirectory, maxRounds, expectedPlan)
        }

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
        require(
            result.dependencies
                .isEmpty(),
        ) { "Dependent buckets are not supported yet" }
        val seen = hashSetOf<String>()

        for (file in result.files) {
            require(relativePath(file.path) == file.path)
            require(
                seen.add(
                    file.path
                        .lowercase(Locale.ROOT),
                ),
            ) { "Duplicate remote path" }
            require(file.metadata.size >= 0)
            Checksum("md5", file.metadata.checksum)
        }
    }

    private fun sources(files: BucketFiles): List<SourceFile> =
        files.files.map { file ->
            SourceFile(
                path = file.path,
                size = file.metadata.size,
                checksum = Checksum("md5", file.metadata.checksum),
                open = { transport.openDownload(file.location) },
            )
        }

    private suspend fun pushPlan(
        remoteFiles: BucketFiles,
        local: List<UploadFile>,
    ): SyncPlan {
        val remote =
            remoteFiles.files
                .associateBy {
                    it.path
                        .lowercase(Locale.ROOT)
                }
        val seen = hashSetOf<String>()
        val signature = StringBuilder()
        val added = mutableListOf<String>()
        val updated = mutableListOf<String>()
        val identical = mutableListOf<String>()

        for (entry in local.sortedBy { it.path }) {
            val key =
                entry.path
                    .lowercase(Locale.ROOT)
            require(seen.add(key)) { "Duplicate local path" }
            appendSnapshotSignature(signature, entry)

            val other = remote[key]
            if (other == null) {
                added.add(entry.path)
            } else if (matchesRemote(entry, other)) {
                identical.add(entry.path)
            } else {
                updated.add(entry.path)
            }
        }

        val sortedRemote =
            remote.values
                .sortedBy { it.path }
        for (file in sortedRemote) {
            signature.append(file.path)
            signature.append('\u0000')
            signature.append(file.metadata)
            signature.append('\n')
        }

        val fingerprint =
            signature
                .toString()
                .byteInputStream()
                .use { hash(it, "sha1") }
        val deleted =
            remote
                .filterKeys { it !in seen }
                .values
                .map { it.path }
                .sorted()

        return SyncPlan(added.sorted(), updated.sorted(), deleted, identical.sorted(), fingerprint)
    }

    private fun appendSnapshotSignature(
        signature: StringBuilder,
        entry: UploadFile,
    ) {
        signature.append(entry.path)
        signature.append('\u0000')
        signature.append(entry.checksum)
        signature.append('\n')
    }

    private fun matchesRemote(
        local: UploadFile,
        remote: RemoteFile,
    ): Boolean {
        val matchesSize = local.size < 0 || local.size == remote.metadata.size
        return matchesSize &&
            local.checksum
                .equals(remote.metadata.checksum, true)
    }

    private suspend fun pushUnlocked(
        id: String,
        storage: SyncStorage,
        scratchDirectory: File,
        maxRounds: Int,
        expectedPlan: SyncPlan?,
    ): PushResponse =
        withContext(Dispatchers.IO) {
            require(maxRounds in 1..20)
            validateScratchDirectory(storage, scratchDirectory)

            val copies = linkedMapOf<String, File>()
            val uploaded = mutableListOf<String>()

            try {
                val snapshot = createUploadSnapshot(storage, scratchDirectory, copies)
                if (expectedPlan != null) {
                    require(pushPlan(files(id), snapshot) == expectedPlan) {
                        "Changes differ from the preview; compare again"
                    }
                }

                completePush(id, snapshot, copies, uploaded, maxRounds)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                throw PushFailure(uploaded.toList(), failure)
            } finally {
                copies.values
                    .forEach { it.delete() }
            }
        }

    private fun validateScratchDirectory(
        storage: SyncStorage,
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
        storage: SyncStorage,
        directory: File,
        copies: MutableMap<String, File>,
    ): List<UploadFile> {
        val seen = hashSetOf<String>()
        val snapshot = mutableListOf<UploadFile>()

        for (entry in storage.list()) {
            require(
                seen.add(
                    entry.path
                        .lowercase(Locale.ROOT),
                ),
            ) { "Duplicate local path" }
            val temporary = File.createTempFile("fishsync-upload-", ".tmp", directory)
            copies[entry.path] = temporary
            copyUploadFile(storage, entry.path, temporary)

            val checksum =
                temporary
                    .inputStream()
                    .use { hash(it) }
            val current =
                storage
                    .read(entry.path)
                    .use { hash(it) }
            if (checksum != current) {
                throw ChangedFileException(entry.path)
            }

            snapshot.add(UploadFile(entry.path, temporary.length(), checksum))
        }

        return snapshot
    }

    private suspend fun copyUploadFile(
        storage: SyncStorage,
        path: String,
        target: File,
    ) {
        storage.read(path).use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(65536)

                while (true) {
                    currentCoroutineContext().ensureActive()
                    val bytesRead = input.read(buffer)
                    if (bytesRead < 0) {
                        break
                    }
                    output.write(buffer, 0, bytesRead)
                }
            }
        }
    }

    private suspend fun completePush(
        id: String,
        snapshot: List<UploadFile>,
        copies: Map<String, File>,
        uploaded: MutableList<String>,
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
                uploaded.add(required.path)
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
        fun defaultRules() = listOf(SyncRule(SyncAction.FullSync))
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
