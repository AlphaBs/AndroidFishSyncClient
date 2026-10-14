package io.github.alphabs.fishsync.client

import kotlinx.serialization.Serializable

@Serializable
data class RemoteMetadata(
    val size: Long,
    val checksum: String,
    val lastUpdated: String? = null,
)

@Serializable
data class RemoteFile(
    val path: String,
    val location: String? = null,
    val metadata: RemoteMetadata,
)

@Serializable
data class BucketFiles(
    val id: String? = null,
    val lastUpdated: String? = null,
    val files: List<RemoteFile> = emptyList(),
    val dependencies: List<String> = emptyList(),
)

@Serializable
data class HttpAction(
    val type: String,
    val parameters: Map<String, String>? = null,
)

@Serializable
data class RequiredAction(
    val path: String,
    val action: HttpAction,
)

@Serializable
data class PushResponse(
    val isSuccess: Boolean,
    val requiredActions: List<RequiredAction> = emptyList(),
    val updatedAt: String? = null,
)

class ApiException(
    val status: Int,
) : Exception("Server request failed (HTTP $status)")

class PushFailure(
    val uploaded: List<String>,
    cause: Throwable,
) : Exception("PUSH failed; server changes may already be applied", cause)
