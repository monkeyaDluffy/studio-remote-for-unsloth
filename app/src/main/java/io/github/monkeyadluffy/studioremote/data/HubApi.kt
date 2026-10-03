package io.github.monkeyadluffy.studioremote.data

import io.github.monkeyadluffy.studioremote.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Request

// Model management: Hugging Face search, downloads into Studio's cache, deletion.

/** A search hit from the Hugging Face model index. */
@Serializable
data class HubModel(
    val id: String,
    val downloads: Long = 0,
    val likes: Long = 0,
)

@Serializable
data class DownloadStatus(val state: String = "idle", val error: String? = null)

@Serializable
data class DownloadProgress(
    @SerialName("downloaded_bytes") val downloadedBytes: Long = 0,
    @SerialName("expected_bytes") val expectedBytes: Long = 0,
    val progress: Double = 0.0,
)

@Serializable
data class ActiveDownload(
    @SerialName("repo_id") val repoId: String? = null,
    val variant: String? = null,
    val state: String = "",
)

@Serializable
private data class ActiveDownloadList(val downloads: List<ActiveDownload> = emptyList())

/**
 * Searches GGUF models on Hugging Face, most downloaded first. The Studio web UI asks the Hub
 * directly too; the phone has its own internet access.
 */
suspend fun Api.searchHub(text: String): List<HubModel> = withContext(Dispatchers.IO) {
    val url = "https://huggingface.co/api/models?" +
        query("search" to text.trim(), "filter" to "gguf", "sort" to "downloads", "direction" to "-1", "limit" to "40")
    http.newCall(Request.Builder().url(url).build()).execute().use {
        if (!it.isSuccessful) throw ApiException(it.code, null, R.string.hub_search_failed)
        json.decodeFromString<List<HubModel>>(it.body.string())
    }
}

/** Every quantisation of a repo, downloaded or not. */
suspend fun Api.hubVariants(repoId: String): GgufVariants =
    json.decodeFromString(call("/api/hub/gguf-variants?" + query("repo_id" to repoId)))

suspend fun Api.startDownload(repoId: String, variant: String?) {
    val body = buildJsonObject {
        put("repo_id", repoId)
        variant?.let { put("gguf_variant", it) }
    }
    call("/api/hub/download", "POST", body.toString())
}

suspend fun Api.cancelDownload(repoId: String, variant: String?) {
    val body = buildJsonObject {
        put("repo_id", repoId)
        variant?.let { put("gguf_variant", it) }
    }
    call("/api/hub/download/cancel", "POST", body.toString())
}

suspend fun Api.downloadStatus(repoId: String, variant: String?): DownloadStatus =
    json.decodeFromString(call("/api/hub/download-status?" + query("repo_id" to repoId, "gguf_variant" to variant.orEmpty())))

suspend fun Api.downloadProgress(repoId: String, variant: String?, expectedBytes: Long): DownloadProgress =
    json.decodeFromString(
        call(
            "/api/hub/gguf-download-progress?" +
                query("repo_id" to repoId, "variant" to variant.orEmpty(), "expected_bytes" to expectedBytes.toString())
        )
    )

suspend fun Api.activeDownloads(): List<ActiveDownload> =
    json.decodeFromString<ActiveDownloadList>(call("/api/hub/active-downloads")).downloads

/** Deletes one downloaded quantisation, or the whole repo when [variant] is null. */
suspend fun Api.deleteCached(repoId: String, variant: String?) {
    val body = buildJsonObject {
        put("repo_id", repoId)
        variant?.let { put("variant", it) }
    }
    call("/api/hub/delete-cached", "DELETE", body.toString())
}
