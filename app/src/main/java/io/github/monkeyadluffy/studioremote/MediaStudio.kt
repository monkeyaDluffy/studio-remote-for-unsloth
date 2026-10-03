package io.github.monkeyadluffy.studioremote

import android.util.LruCache
import io.github.monkeyadluffy.studioremote.data.Api
import io.github.monkeyadluffy.studioremote.data.ApiException
import io.github.monkeyadluffy.studioremote.data.CachedRepo
import io.github.monkeyadluffy.studioremote.data.GalleryItem
import io.github.monkeyadluffy.studioremote.data.GgufVariant
import io.github.monkeyadluffy.studioremote.data.MediaKind
import io.github.monkeyadluffy.studioremote.data.MediaProgress
import io.github.monkeyadluffy.studioremote.data.MediaStatus
import io.github.monkeyadluffy.studioremote.data.TASK_IMAGE
import io.github.monkeyadluffy.studioremote.data.TASK_SPEECH
import io.github.monkeyadluffy.studioremote.data.TASK_VIDEO
import io.github.monkeyadluffy.studioremote.data.allCachedModels
import io.github.monkeyadluffy.studioremote.data.audioGallery
import io.github.monkeyadluffy.studioremote.data.bytes
import io.github.monkeyadluffy.studioremote.data.cancelMedia
import io.github.monkeyadluffy.studioremote.data.deleteGalleryItem
import io.github.monkeyadluffy.studioremote.data.gallery
import io.github.monkeyadluffy.studioremote.data.generateImage
import io.github.monkeyadluffy.studioremote.data.generateVideo
import io.github.monkeyadluffy.studioremote.data.mediaLoad
import io.github.monkeyadluffy.studioremote.data.mediaLoadProgress
import io.github.monkeyadluffy.studioremote.data.mediaProgress
import io.github.monkeyadluffy.studioremote.data.mediaStatus
import io.github.monkeyadluffy.studioremote.data.mediaUnload
import io.github.monkeyadluffy.studioremote.data.speech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** One media page (images or videos). */
data class MediaPageState(
    val status: MediaStatus? = null,
    val models: List<CachedRepo> = emptyList(),
    /** Quantisations of a GGUF repo picked to load; null when no picker is open. */
    val picking: Pair<String, List<GgufVariant>>? = null,
    /** Label of the model being loaded, with its progress (0..1, or null when unknown). */
    val loadingModel: String? = null,
    val loadFraction: Double? = null,
    val generating: Boolean = false,
    val progress: MediaProgress? = null,
    val gallery: List<GalleryItem> = emptyList(),
    val galleryLoading: Boolean = false,
)

data class SpeechState(
    val models: List<CachedRepo> = emptyList(),
    val generating: Boolean = false,
    /** Local WAV file of the latest result, ready to play. */
    val lastAudio: java.io.File? = null,
    val gallery: List<GalleryItem> = emptyList(),
)

/** Studio's image, video and speech pages. */
class MediaStudio(
    private val api: Api,
    private val scope: CoroutineScope,
    private val cacheDir: java.io.File,
    private val report: (Throwable) -> Unit,
) {
    private val pages = MediaKind.entries.associateWith { MutableStateFlow(MediaPageState()) }
    fun state(kind: MediaKind): StateFlow<MediaPageState> = pages.getValue(kind).asStateFlow()

    private val _speech = MutableStateFlow(SpeechState())
    val speech: StateFlow<SpeechState> = _speech.asStateFlow()

    private val jobs = HashMap<MediaKind, Job>()
    /** Renders the user cancelled; their failure is expected and not reported. */
    private val cancelled = HashSet<MediaKind>()

    /** Thumbnails and files already fetched, keyed by path. */
    private val byteCache = object : LruCache<String, ByteArray>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray) = value.size
    }

    private fun page(kind: MediaKind) = pages.getValue(kind)

    private fun launchReporting(block: suspend () -> Unit) = scope.launchReporting(report, block)

    suspend fun fileBytes(path: String): ByteArray =
        byteCache.get(path) ?: api.bytes(path).also { byteCache.put(path, it) }

    /** Downloads a gallery file to the cache folder, for players that want a file. */
    suspend fun fileOnDisk(path: String, name: String): java.io.File {
        val file = java.io.File(cacheDir, name)
        if (!file.exists() || file.length() == 0L) {
            val bytes = byteCache.get(path) ?: api.bytes(path)
            val part = java.io.File(cacheDir, "$name.part")
            withContext(Dispatchers.IO) {
                part.writeBytes(bytes)
                part.renameTo(file)
            }
        }
        return file
    }

    // ---------------- images / videos ----------------

    fun open(kind: MediaKind) = launchReporting {
        val task = if (kind == MediaKind.Image) TASK_IMAGE else TASK_VIDEO
        val status = runCatching { api.mediaStatus(kind) }.getOrNull()
        page(kind).update { it.copy(status = status) }
        val models = api.allCachedModels().filter { it.task == task }
        page(kind).update { it.copy(models = models) }
        refreshGallery(kind)
        // A render started earlier (or from the web UI) is still running: show it.
        if (jobs[kind]?.isActive != true) {
            val p = runCatching { api.mediaProgress(kind) }.getOrNull()
            if (p?.active == true) jobs[kind] = launchReporting { watchGeneration(kind) }
        }
    }

    fun refreshGallery(kind: MediaKind) = launchReporting {
        page(kind).update { it.copy(galleryLoading = true) }
        try {
            val items = api.gallery(kind)
            page(kind).update { it.copy(gallery = items) }
        } finally {
            page(kind).update { it.copy(galleryLoading = false) }
        }
    }

    /** A GGUF repo needs one of its files picked; other formats load as they are. */
    fun pickModel(kind: MediaKind, model: CachedRepo) = launchReporting {
        if (!model.isGguf) {
            load(kind, model.repoId, null, model.repoId)
            return@launchReporting
        }
        val variants = api.variants(model.repoId).variants.filter { it.downloaded && it.filename.isNotEmpty() }
        when (variants.size) {
            0 -> report(IllegalStateException("Bu modelin indirilmiş dosyası yok"))
            1 -> load(kind, model.repoId, variants[0].filename, "${model.repoId} · ${variants[0].label}")
            else -> page(kind).update { it.copy(picking = model.repoId to variants) }
        }
    }

    fun closePicker(kind: MediaKind) = page(kind).update { it.copy(picking = null) }

    fun load(kind: MediaKind, repoId: String, ggufFilename: String?, label: String) {
        page(kind).update { it.copy(picking = null, loadingModel = label, loadFraction = null) }
        launchReporting {
            try {
                api.mediaLoad(kind, repoId, ggufFilename)
                // Loading continues on the server; up to 30 minutes for a first download.
                repeat(1200) { i ->
                    val status = runCatching { api.mediaStatus(kind) }.getOrNull()
                    val p = runCatching { api.mediaLoadProgress(kind) }.getOrNull()
                    p?.error?.let { throw ApiException(500, it) }
                    val ours = status?.repoId.equals(repoId, ignoreCase = true) || status?.displayRepoId.equals(repoId, ignoreCase = true)
                    // Studio may report the repo under another name; a settled load is enough then.
                    if (status?.loaded == true && (ours || (p?.phase == null && i >= 3))) {
                        page(kind).update { it.copy(status = status) }
                        return@launchReporting
                    }
                    page(kind).update { it.copy(loadFraction = p?.fraction?.takeIf { f -> f > 0 }) }
                    delay(1500)
                }
            } finally {
                page(kind).update { it.copy(loadingModel = null, loadFraction = null) }
            }
        }
    }

    fun unload(kind: MediaKind) = launchReporting {
        api.mediaUnload(kind)
        page(kind).update { it.copy(status = runCatching { api.mediaStatus(kind) }.getOrNull()) }
    }

    fun generateImage(prompt: String, width: Int, height: Int, steps: Int, seed: Long?) {
        if (jobs[MediaKind.Image]?.isActive == true) return
        cancelled -= MediaKind.Image
        jobs[MediaKind.Image] = launchReporting {
            val page = page(MediaKind.Image)
            page.update { it.copy(generating = true, progress = null) }
            try {
                val error = coroutineScope {
                    val request = async { runCatching { api.generateImage(prompt, width, height, steps, seed) } }
                    // Progress comes from a separate endpoint while the request is open.
                    while (request.isActive) {
                        runCatching { api.mediaProgress(MediaKind.Image) }.getOrNull()?.let { p -> page.update { it.copy(progress = p) } }
                        delay(1000)
                    }
                    request.await().exceptionOrNull()
                }
                when {
                    error == null || MediaKind.Image in cancelled -> {}
                    // The tunnel gave up on a long render; Studio keeps going, so wait for it.
                    error !is ApiException || error.code >= 500 -> watchGeneration(MediaKind.Image)
                    else -> throw error
                }
                refreshGallery(MediaKind.Image)
            } finally {
                page.update { it.copy(generating = false, progress = null) }
            }
        }
    }

    fun generateVideo(prompt: String, width: Int?, height: Int?, numFrames: Int?, steps: Int?) {
        if (jobs[MediaKind.Video]?.isActive == true) return
        cancelled -= MediaKind.Video
        jobs[MediaKind.Video] = launchReporting {
            page(MediaKind.Video).update { it.copy(generating = true, progress = null) }
            try {
                api.generateVideo(prompt, width, height, numFrames, steps)
                watchGeneration(MediaKind.Video)
            } finally {
                page(MediaKind.Video).update { it.copy(generating = false, progress = null) }
            }
        }
    }

    /** Follows a running render until Studio reports it idle, then refreshes the gallery. */
    private suspend fun watchGeneration(kind: MediaKind) {
        val page = page(kind)
        page.update { it.copy(generating = true) }
        try {
            var idle = 0
            val retry = Retry(20)
            while (true) {
                delay(1500)
                val p = retry.attempt { api.mediaProgress(kind) } ?: continue
                page.update { it.copy(progress = p) }
                p.error?.let { if (kind !in cancelled) throw ApiException(500, it) else return }
                if (p.video != null) break
                // A just-started job can briefly report inactive before its first step.
                if (!p.active && ++idle >= 3) break
            }
            refreshGallery(kind)
        } finally {
            page.update { it.copy(generating = false, progress = null) }
        }
    }

    fun cancel(kind: MediaKind) {
        cancelled += kind
        launchReporting { api.cancelMedia(kind) }
    }

    fun delete(kind: MediaKind, item: GalleryItem) = launchReporting {
        api.deleteGalleryItem(kind.base, item.id)
        page(kind).update { s -> s.copy(gallery = s.gallery.filterNot { it.id == item.id }) }
    }

    // ---------------- speech ----------------

    fun openSpeech() = launchReporting {
        val models = api.allCachedModels().filter { it.task == TASK_SPEECH }
        _speech.update { it.copy(models = models) }
        refreshAudioGallery()
    }

    private suspend fun refreshAudioGallery() {
        runCatching { api.audioGallery() }.onSuccess { items -> _speech.update { it.copy(gallery = items) } }
    }

    /** Speaks [text] with the text-to-speech model currently loaded in Studio. */
    fun speak(text: String) {
        if (_speech.value.generating || text.isBlank()) return
        _speech.update { it.copy(generating = true) }
        launchReporting {
            try {
                val wav = api.speech(text.trim())
                val file = java.io.File(cacheDir, "speech-${System.currentTimeMillis()}.wav").apply { writeBytes(wav) }
                _speech.value.lastAudio?.delete()
                _speech.update { it.copy(lastAudio = file) }
                refreshAudioGallery()
            } finally {
                _speech.update { it.copy(generating = false) }
            }
        }
    }

    fun deleteAudio(item: GalleryItem) = launchReporting {
        api.deleteGalleryItem("audio", item.id)
        _speech.update { s -> s.copy(gallery = s.gallery.filterNot { it.id == item.id }) }
    }
}
