package io.github.monkeyadluffy.studioremote

import io.github.monkeyadluffy.studioremote.data.Api
import io.github.monkeyadluffy.studioremote.data.CachedRepo
import io.github.monkeyadluffy.studioremote.data.GgufVariant
import io.github.monkeyadluffy.studioremote.data.HubModel
import io.github.monkeyadluffy.studioremote.data.activeDownloads
import io.github.monkeyadluffy.studioremote.data.allCachedModels
import io.github.monkeyadluffy.studioremote.data.cancelDownload
import io.github.monkeyadluffy.studioremote.data.deleteCached
import io.github.monkeyadluffy.studioremote.data.downloadProgress
import io.github.monkeyadluffy.studioremote.data.downloadStatus
import io.github.monkeyadluffy.studioremote.data.hubVariants
import io.github.monkeyadluffy.studioremote.data.searchHub
import io.github.monkeyadluffy.studioremote.data.startDownload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A model download Studio is running; [progress] is 0..1. */
data class DownloadJob(
    val repoId: String,
    val variant: String?,
    val progress: Double = 0.0,
    val downloadedBytes: Long = 0,
    val expectedBytes: Long = 0,
) {
    val key: String get() = downloadKey(repoId, variant)
}

fun downloadKey(repoId: String, variant: String?) = "$repoId:${variant.orEmpty()}"

data class HubState(
    val local: List<CachedRepo> = emptyList(),
    val localLoading: Boolean = false,
    val query: String = "",
    val results: List<HubModel> = emptyList(),
    val searching: Boolean = false,
    /** Repo whose quantisations are on screen. */
    val repo: String? = null,
    val variants: List<GgufVariant> = emptyList(),
    val variantsLoading: Boolean = false,
    val downloads: Map<String, DownloadJob> = emptyMap(),
)

/** Model management: what is downloaded, Hugging Face search, downloads and deletion. */
class ModelHub(
    private val api: Api,
    private val scope: CoroutineScope,
    private val report: (Throwable) -> Unit,
    /** Downloads or deletions changed what can be loaded. */
    private val onInventoryChanged: () -> Unit,
) {
    private val _state = MutableStateFlow(HubState())
    val state: StateFlow<HubState> = _state.asStateFlow()
    private val pollers = HashMap<String, Job>()
    /** Downloads listed as running whose status can't be read under the same key; not re-polled. */
    private val unreadable = HashSet<String>()
    private var searchJob: Job? = null

    private fun launchReporting(block: suspend () -> Unit) = scope.launchReporting(report, block)

    /** Refreshes the downloaded list and picks up downloads started elsewhere (e.g. the web UI). */
    fun refresh() = launchReporting {
        _state.update { it.copy(localLoading = true) }
        try {
            val local = api.allCachedModels().sortedBy { it.repoId.lowercase() }
            _state.update { it.copy(local = local) }
            runCatching { api.activeDownloads() }.getOrDefault(emptyList())
                .filter { it.repoId != null && it.state == "running" }
                .filter { downloadKey(it.repoId!!, it.variant?.ifEmpty { null }) !in unreadable }
                .forEach { track(it.repoId!!, it.variant?.ifEmpty { null }, expectedBytes = 0) }
        } finally {
            _state.update { it.copy(localLoading = false) }
        }
    }

    fun search(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(results = emptyList(), searching = false) }
            return
        }
        searchJob = launchReporting {
            delay(350) // debounce typing
            _state.update { it.copy(searching = true) }
            try {
                val results = api.searchHub(query)
                _state.update { it.copy(results = results) }
            } finally {
                _state.update { it.copy(searching = false) }
            }
        }
    }

    fun openRepo(repoId: String) {
        _state.update { it.copy(repo = repoId, variants = emptyList(), variantsLoading = true) }
        launchReporting {
            try {
                val variants = api.hubVariants(repoId).variants.sortedBy { it.sizeBytes }
                if (_state.value.repo == repoId) _state.update { it.copy(variants = variants) }
            } finally {
                _state.update { it.copy(variantsLoading = false) }
            }
        }
    }

    fun closeRepo() = _state.update { it.copy(repo = null, variants = emptyList()) }

    fun download(repoId: String, v: GgufVariant) = launchReporting {
        api.startDownload(repoId, v.quant)
        track(repoId, v.quant, v.downloadSizeBytes.takeIf { it > 0 } ?: v.sizeBytes)
    }

    fun cancel(job: DownloadJob) = launchReporting {
        api.cancelDownload(job.repoId, job.variant)
    }

    /** Deletes one quantisation, or the whole repo when [variant] is null. */
    fun delete(repoId: String, variant: String?) = launchReporting {
        api.deleteCached(repoId, variant)
        afterInventoryChange(repoId)
    }

    private fun afterInventoryChange(repoId: String) {
        if (_state.value.repo == repoId) openRepo(repoId)
        refresh()
        onInventoryChanged()
    }

    /** Polls a download until Studio reports it finished, failed or cancelled. */
    private fun track(repoId: String, variant: String?, expectedBytes: Long) {
        val key = downloadKey(repoId, variant)
        if (pollers[key]?.isActive == true) return
        _state.update { it.copy(downloads = it.downloads + (key to DownloadJob(repoId, variant, expectedBytes = expectedBytes))) }
        pollers[key] = launchReporting {
            val retry = Retry(20)
            var idle = 0
            var finished = false
            try {
                while (true) {
                    delay(1500)
                    val status = retry.attempt { api.downloadStatus(repoId, variant) } ?: continue
                    runCatching { api.downloadProgress(repoId, variant, expectedBytes) }.getOrNull()?.let { p ->
                        _state.update { s ->
                            val job = s.downloads[key] ?: return@update s
                            s.copy(
                                downloads = s.downloads + (key to job.copy(
                                    progress = p.progress.coerceIn(0.0, 1.0),
                                    downloadedBytes = p.downloadedBytes,
                                    expectedBytes = p.expectedBytes.takeIf { it > 0 } ?: job.expectedBytes,
                                ))
                            )
                        }
                    }
                    when (status.state) {
                        "complete", "cancelled" -> {
                            finished = true
                            break
                        }
                        "error" -> {
                            finished = true
                            throw IllegalStateException(status.error ?: "İndirme başarısız")
                        }
                        // Studio doesn't know this download by this name; stop rather than poll forever.
                        "idle" -> if (++idle >= 3) {
                            unreadable += key
                            break
                        }
                        else -> idle = 0
                    }
                }
            } finally {
                pollers.remove(key)
                _state.update { it.copy(downloads = it.downloads - key) }
                if (finished) afterInventoryChange(repoId)
            }
        }
    }
}
