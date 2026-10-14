package io.github.alphabs.fishsync.client

import io.github.alphabs.fishsync.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.FilterInputStream
import java.io.InputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable data class RemoteMetadata(val size: Long, val checksum: String, val lastUpdated: String? = null)
@Serializable data class RemoteFile(val path: String, val location: String? = null, val metadata: RemoteMetadata)
@Serializable data class BucketFiles(val id: String? = null, val lastUpdated: String? = null, val files: List<RemoteFile> = emptyList(), val dependencies: List<String> = emptyList())
@Serializable data class HttpAction(val type: String, val parameters: Map<String, String>? = null)
@Serializable data class RequiredAction(val path: String, val action: HttpAction)
@Serializable data class PushResponse(val isSuccess: Boolean, val requiredActions: List<RequiredAction> = emptyList(), val updatedAt: String? = null)
@Serializable private data class UploadFile(val path: String, val size: Long, val checksum: String)
@Serializable private data class UploadRequest(val files: List<UploadFile>)
class ApiException(val status: Int) : Exception("Server request failed (HTTP $status)")
class PushFailure(val uploaded: List<String>, cause: Throwable) : Exception("PUSH failed; server changes may already be applied", cause)

class FishSyncClient(
    serverUrl: String,
    private val tokenProvider: () -> String? = { null },
    private val http: OkHttpClient = OkHttpClient.Builder().readTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build(),
    val engine: SyncEngine = SyncEngine(caseInsensitive = true)
) {
    private val base = serverUrl.trimEnd('/').toHttpUrl()
    private val uploadHttp = http.newBuilder().retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val uploadProgress = MutableSharedFlow<SyncProgress>(extraBufferCapacity = 64)
    val progress get() = merge(engine.progress, uploadProgress)
    private fun apiUrl(vararg segments: String): HttpUrl = base.newBuilder().apply { segments.forEach { require(it.isNotBlank() && it != "." && it != "..") { "Invalid API path segment" }; addPathSegment(it) } }.build()
    private fun apiRequest(url: HttpUrl, body: String? = null): Request = Request.Builder().url(url).apply {
        tokenProvider()?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
        if (body != null) post(body.toRequestBody("application/json".toMediaType()))
    }.build()
    private suspend fun headers(request: Request, upload: Boolean = false): Pair<Call, Response> {
        val call = (if (upload) uploadHttp else http).newCall(request)
        val response = suspendCancellableCoroutine<Response> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    if (!continuation.isActive) { response.close(); return }
                    continuation.resume(response) { _, value, _ -> value.close() }
                }
            })
        }
        return call to response
    }
    private suspend fun requestText(request: Request, upload: Boolean = false): String = coroutineScope {
        val (call, response) = headers(request, upload)
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) { try { awaitCancellation() } finally { call.cancel() } }
        try {
            withContext(Dispatchers.IO) { response.use {
                if (!it.isSuccessful) throw ApiException(it.code)
                val body = it.body ?: error("Empty HTTP response")
                require(body.contentLength() <= 10 * 1024 * 1024) { "Response too large" }
                body.byteStream().use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) { currentCoroutineContext().ensureActive(); val n = input.read(buffer); if (n < 0) break; require(output.size() + n <= 10 * 1024 * 1024); output.write(buffer, 0, n) }
                    output.toString("UTF-8")
                }
            } }
        } finally { watcher.cancel() }
    }
    suspend fun login(username: String, password: String): String {
        val body = buildJsonObject { put("username", username); put("password", password) }.toString()
        return json.parseToJsonElement(requestText(apiRequest(apiUrl("auth", "login"), body))).jsonObject["token"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: error("Missing login token")
    }
    suspend fun listBuckets(): List<String> = json.parseToJsonElement(requestText(apiRequest(apiUrl("buckets")))).jsonObject.getValue("buckets").jsonArray.map { it.jsonPrimitive.content }
    suspend fun bucketInfo(id: String): JsonObject = json.parseToJsonElement(requestText(apiRequest(apiUrl("buckets", "common", id)))).jsonObject
    suspend fun files(id: String): BucketFiles {
        val result = json.decodeFromString<BucketFiles>(requestText(apiRequest(apiUrl("buckets", "common", id, "files"))))
        require(result.dependencies.isEmpty()) { "Dependent buckets are not supported yet" }
        val seen = hashSetOf<String>()
        for (f in result.files) {
            require(relativePath(f.path) == f.path)
            require(seen.add(f.path.lowercase(java.util.Locale.ROOT))) { "Duplicate remote path" }
            require(f.metadata.size >= 0); Checksum("md5", f.metadata.checksum)
        }
        return result
    }
    private fun sources(files: BucketFiles): List<SourceFile> = files.files.map { file ->
        SourceFile(file.path, file.metadata.size, Checksum("md5", file.metadata.checksum)) {
            // API authorization is never automatically attached to a download URL.
            val url = file.location?.toHttpUrl() ?: error("Missing download URL")
            val (call, response) = headers(Request.Builder().url(url).build())
            if (!response.isSuccessful) { val code = response.code; response.close(); throw ApiException(code) }
            val stream = response.body?.byteStream() ?: run { response.close(); error("Empty download") }
            val watcher = CoroutineScope(currentCoroutineContext()).launch(start = CoroutineStart.UNDISPATCHED) { try { awaitCancellation() } finally { call.cancel() } }
            object : FilterInputStream(stream) {
                override fun close() { try { response.close() } finally { watcher.cancel() } }
            }
        }
    }
    suspend fun comparePull(id: String, storage: SyncStorage, rules: List<SyncRule> = defaultRules()): SyncPlan = engine.compare(sources(files(id)), storage, rules)
    suspend fun pull(id: String, storage: SyncStorage, rules: List<SyncRule> = defaultRules(), expectedPlan: SyncPlan? = null): SyncResult = engine.pull(sources(files(id)), storage, rules, expectedPlan = expectedPlan)

    suspend fun comparePush(id: String, storage: SyncStorage): SyncPlan = withContext(Dispatchers.IO) {
        val remote = files(id)
        val local = storage.list().map { entry -> UploadFile(entry.path, entry.size ?: -1, storage.read(entry.path).use { hash(it) }) }
        pushPlan(remote, local)
    }
    private suspend fun pushPlan(remoteFiles: BucketFiles, local: List<UploadFile>): SyncPlan {
        val remote = remoteFiles.files.associateBy { it.path.lowercase(java.util.Locale.ROOT) }
        val seen = hashSetOf<String>()
        val signature = StringBuilder()
        val added = mutableListOf<String>(); val updated = mutableListOf<String>(); val identical = mutableListOf<String>()
        for (entry in local.sortedBy { it.path }) {
            val key = entry.path.lowercase(java.util.Locale.ROOT)
            require(seen.add(key)) { "Duplicate local path" }
            signature.append(entry.path).append('\u0000').append(entry.checksum).append('\n')
            val other = remote[key]
            if (other == null) added.add(entry.path)
            else if ((entry.size < 0 || entry.size == other.metadata.size) && entry.checksum.equals(other.metadata.checksum, true)) identical.add(entry.path)
            else updated.add(entry.path)
        }
        remote.values.sortedBy { it.path }.forEach { signature.append(it.path).append('\u0000').append(it.metadata).append('\n') }
        return SyncPlan(added.sorted(), updated.sorted(), remote.filterKeys { it !in seen }.values.map { it.path }.sorted(), identical.sorted(), signature.toString().byteInputStream().use { hash(it, "sha1") })
    }

    /** Stable copies are supplied by the caller in app-private scratch space, outside the sync root. */
    suspend fun push(id: String, storage: SyncStorage, scratchDirectory: File, maxRounds: Int = 10, expectedPlan: SyncPlan? = null): PushResponse = StorageLocks.run(storage) {
        pushUnlocked(id, storage, scratchDirectory, maxRounds, expectedPlan)
    }
    private suspend fun pushUnlocked(id: String, storage: SyncStorage, scratchDirectory: File, maxRounds: Int, expectedPlan: SyncPlan?): PushResponse = withContext(Dispatchers.IO) {
        require(maxRounds in 1..20)
        if (storage is FileSyncStorage) require(!scratchDirectory.canonicalFile.toPath().startsWith(File(storage.identity).toPath())) { "Upload scratch space must be outside the sync root" }
        require(scratchDirectory.isDirectory || scratchDirectory.mkdirs()) { "Cannot create upload scratch space" }
        val copies = linkedMapOf<String, File>()
        val uploaded = mutableListOf<String>()
        try {
            val seen = hashSetOf<String>()
            val snapshot = storage.list().map { entry ->
                require(seen.add(entry.path.lowercase(java.util.Locale.ROOT))) { "Duplicate local path" }
                val temp = File.createTempFile("fishsync-upload-", ".tmp", scratchDirectory)
                copies[entry.path] = temp
                storage.read(entry.path).use { input -> temp.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    while (true) { currentCoroutineContext().ensureActive(); val n = input.read(buffer); if (n < 0) break; output.write(buffer, 0, n) }
                } }
                val digest = temp.inputStream().use { hash(it) }
                if (digest != storage.read(entry.path).use { hash(it) }) throw ChangedFileException(entry.path)
                UploadFile(entry.path, temp.length(), digest)
            }
            if (expectedPlan != null) require(pushPlan(files(id), snapshot) == expectedPlan) { "Changes differ from the preview; compare again" }
            val body = json.encodeToString(UploadRequest(snapshot))
            repeat(maxRounds) {
                val result = json.decodeFromString<PushResponse>(requestText(apiRequest(apiUrl("buckets", "common", id, "sync"), body)))
                if (result.isSuccess) return@withContext result
                require(result.requiredActions.isNotEmpty()) { "Server returned no actionable changes" }
                for (required in result.requiredActions) {
                    require(required.path == relativePath(required.path))
                    require(required.action.type == "Http") { "Unsupported server sync action" }
                    val params = required.action.parameters ?: error("Missing action parameters")
                    val copy = copies[required.path] ?: error("Server requested a file outside the snapshot")
                    val paramsContentType = params.entries.firstOrNull { it.key.equals("headers.Content-Type", true) }?.value ?: "application/octet-stream"
                    val requestBody = object : RequestBody() {
                        override fun isOneShot() = true
                        override fun contentType() = paramsContentType.toMediaType()
                        override fun contentLength() = copy.length()
                        override fun writeTo(sink: BufferedSink) {
                            copy.inputStream().use { input ->
                                var sent = 0L
                                val buffer = ByteArray(65536)
                                while (true) { val n = input.read(buffer); if (n < 0) break; sink.write(buffer, 0, n); sent += n; uploadProgress.tryEmit(SyncProgress(required.path, "upload", sent, copy.length())) }
                            }
                        }
                    }
                    val method = params["method"] ?: error("Missing HTTP method")
                    require(method == "PUT" || method == "POST") { "Unsupported upload method" }
                    val builder = Request.Builder().url(params["url"]?.toHttpUrl() ?: error("Missing upload URL")).method(method, requestBody)
                    params.filterKeys { it.startsWith("headers.") }.forEach { (key, value) -> builder.header(key.removePrefix("headers."), value) }
                    requestText(builder.build(), upload = true)
                    uploaded.add(required.path)
                }
            }
            error("Server synchronization exceeded $maxRounds rounds")
        } catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) { throw PushFailure(uploaded.toList(), failure) }
        finally { copies.values.forEach { it.delete() } }
    }
    companion object { fun defaultRules() = listOf(SyncRule(SyncAction.FullSync)) }
}
