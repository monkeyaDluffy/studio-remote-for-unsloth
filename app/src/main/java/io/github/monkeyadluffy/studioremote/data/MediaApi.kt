package io.github.monkeyadluffy.studioremote.data

import io.github.monkeyadluffy.studioremote.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Studio's media pages: image and video generation, text to speech and Whisper dictation.

/** A downloaded model that isn't a chat model; [task] says which media page it belongs to. */
@Serializable
data class CachedRepo(
    @SerialName("repo_id") val repoId: String,
    @SerialName("size_bytes") val sizeBytes: Long = 0,
    val task: String? = null,
    val partial: Boolean = false,
    val companion: Boolean = false,
    @SerialName("model_format") val modelFormat: String? = null,
) {
    val isGguf: Boolean get() = modelFormat == "gguf"
}

@Serializable
private data class CachedRepoList(val cached: List<CachedRepo> = emptyList())

const val TASK_IMAGE = "text-to-image"
const val TASK_VIDEO = "text-to-video"
const val TASK_SPEECH = "text-to-speech"

/** Image or video model state; both pages share the shape. */
@Serializable
data class MediaStatus(
    val loaded: Boolean = false,
    @SerialName("repo_id") val repoId: String? = null,
    @SerialName("display_repo_id") val displayRepoId: String? = null,
    @SerialName("gguf_variant") val ggufVariant: String? = null,
) {
    val label: String? get() = (displayRepoId ?: repoId)?.let { id -> ggufVariant?.let { "$id · $it" } ?: id }
}

@Serializable
data class MediaLoadProgress(
    val phase: String? = null,
    val fraction: Double = 0.0,
    val error: String? = null,
)

@Serializable
data class MediaProgress(
    val active: Boolean = false,
    val step: Int = 0,
    @SerialName("total_steps") val totalSteps: Int = 0,
    val fraction: Double = 0.0,
    @SerialName("eta_seconds") val etaSeconds: Double? = null,
    /** Video only: the finished clip, or why it failed. */
    val video: GalleryItem? = null,
    val error: String? = null,
)

/** A generated image, clip or audio file in one of Studio's galleries. */
@Serializable
data class GalleryItem(
    val id: String,
    val prompt: String? = null,
    val model: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("duration_s") val durationS: Double? = null,
)

@Serializable
private data class ImageList(val images: List<GalleryItem> = emptyList())

@Serializable
private data class VideoList(val videos: List<GalleryItem> = emptyList())

@Serializable
private data class AudioList(val audio: List<GalleryItem> = emptyList())

enum class MediaKind(val base: String) { Image("images"), Video("video") }

/** Every downloaded model of any format, with its task. */
suspend fun Api.allCachedModels(): List<CachedRepo> {
    val gguf = json.decodeFromString<CachedRepoList>(call("/api/hub/cached-gguf")).cached
    val other = runCatching { json.decodeFromString<CachedRepoList>(call("/api/hub/cached-models")).cached }.getOrDefault(emptyList())
    return (gguf + other).filter { !it.partial && !it.companion }
}

suspend fun Api.mediaStatus(kind: MediaKind): MediaStatus =
    json.decodeFromString(call("/api/inference/${kind.base}/status"))

/** Starts loading; Studio returns before the model is ready, so follow [mediaLoadProgress]. */
suspend fun Api.mediaLoad(kind: MediaKind, repoId: String, ggufFilename: String?) {
    val body = buildJsonObject {
        put("model_path", repoId)
        ggufFilename?.let { put("gguf_filename", it) }
    }
    call("/api/inference/${kind.base}/load", "POST", body.toString())
}

suspend fun Api.mediaUnload(kind: MediaKind) {
    call("/api/inference/${kind.base}/unload", "POST")
}

suspend fun Api.mediaLoadProgress(kind: MediaKind): MediaLoadProgress =
    json.decodeFromString(call("/api/inference/${kind.base}/load-progress"))

suspend fun Api.mediaProgress(kind: MediaKind): MediaProgress =
    json.decodeFromString(call("/api/inference/${kind.base}/generate-progress"))

suspend fun Api.cancelMedia(kind: MediaKind) {
    runCatching { call("/api/inference/${kind.base}/generate/cancel", "POST") }
}

/**
 * Generates an image and waits for it. A long render can outlive the tunnel's response window;
 * the caller then falls back to watching [mediaProgress] and the gallery.
 */
suspend fun Api.generateImage(prompt: String, width: Int, height: Int, steps: Int, seed: Long?): List<GalleryItem> {
    val body = buildJsonObject {
        put("prompt", prompt)
        put("width", width)
        put("height", height)
        put("steps", steps)
        seed?.let { put("seed", it) }
    }
    return json.decodeFromString<ImageList>(call("/api/inference/images/generate", "POST", body.toString())).images
}

/** Starts a clip; Studio answers at once and reports the result through [mediaProgress]. */
suspend fun Api.generateVideo(prompt: String, width: Int?, height: Int?, numFrames: Int?, steps: Int?) {
    val body = buildJsonObject {
        put("prompt", prompt)
        width?.let { put("width", it) }
        height?.let { put("height", it) }
        numFrames?.let { put("num_frames", it) }
        steps?.let { put("steps", it) }
    }
    call("/api/inference/video/generate", "POST", body.toString())
}

suspend fun Api.gallery(kind: MediaKind, limit: Int = 60): List<GalleryItem> {
    val text = call("/api/inference/${kind.base}/gallery?limit=$limit")
    return when (kind) {
        MediaKind.Image -> json.decodeFromString<ImageList>(text).images
        MediaKind.Video -> json.decodeFromString<VideoList>(text).videos
    }
}

suspend fun Api.audioGallery(limit: Int = 60): List<GalleryItem> =
    json.decodeFromString<AudioList>(call("/api/inference/audio/gallery?limit=$limit")).audio

/** Gallery file path for an image ([thumb] gives a small WebP), clip or audio file. */
fun galleryFilePath(base: String, id: String, thumb: Boolean = false): String =
    "/api/inference/$base/gallery/$id/file" + if (thumb) "?thumb=1" else ""

suspend fun Api.deleteGalleryItem(base: String, id: String) {
    call("/api/inference/$base/gallery/$id", "DELETE")
}

/** Raw bytes of an authenticated Studio file. */
suspend fun Api.bytes(path: String): ByteArray =
    exchange { request(path) }.use {
        if (!it.isSuccessful) throw it.toException()
        it.body.bytes()
    }

/** Speaks [text] with the loaded text-to-speech model; returns WAV bytes. */
suspend fun Api.speech(text: String): ByteArray {
    val body = buildJsonObject {
        put("input", text)
        put("response_format", "wav")
    }
    return exchange { request("/api/inference/audio/speech", "POST", jsonBody(body.toString())) }.use {
        if (!it.isSuccessful) throw it.toException()
        val bytes = it.body.bytes()
        // A padded slow answer can still carry an error instead of audio.
        if (bytes.isNotEmpty() && (bytes[0] == ' '.code.toByte() || bytes[0] == '{'.code.toByte())) {
            val err = parseObject(bytes.decodeToString().trim())
            if (err != null) throw ApiException(500, (err["_deferred_error"] ?: err["detail"])?.let(::resultText), R.string.speech_failed)
        }
        bytes
    }
}

/** Transcribes recorded audio (any common format) with Studio's Whisper, beside the chat model. */
suspend fun Api.transcribe(audioBase64: String, language: String?): String {
    val body = buildJsonObject {
        put("audio", audioBase64)
        language?.let { put("language", it) }
    }
    return parseObject(call("/api/inference/audio/transcribe", "POST", body.toString()))?.str("text").orEmpty().trim()
}

/** Starts downloading Studio's default dictation model. */
suspend fun Api.downloadDictationModel() {
    call("/api/inference/audio/stt/download", "POST")
}
