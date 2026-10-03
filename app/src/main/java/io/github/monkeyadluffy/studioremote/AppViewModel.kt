package io.github.monkeyadluffy.studioremote

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.monkeyadluffy.studioremote.data.Api
import io.github.monkeyadluffy.studioremote.data.ApiException
import io.github.monkeyadluffy.studioremote.data.ApiMessage
import io.github.monkeyadluffy.studioremote.data.AuthStatus
import io.github.monkeyadluffy.studioremote.data.CachedGguf
import io.github.monkeyadluffy.studioremote.data.ChatOptions
import io.github.monkeyadluffy.studioremote.data.ChatProject
import io.github.monkeyadluffy.studioremote.data.ChatThread
import io.github.monkeyadluffy.studioremote.data.Conversation
import io.github.monkeyadluffy.studioremote.data.GgufVariants
import io.github.monkeyadluffy.studioremote.data.InferenceStatus
import io.github.monkeyadluffy.studioremote.data.LoadRequest
import io.github.monkeyadluffy.studioremote.data.LocalizedException
import io.github.monkeyadluffy.studioremote.data.PendingFile
import io.github.monkeyadluffy.studioremote.data.Prefs
import io.github.monkeyadluffy.studioremote.data.ROLE_ASSISTANT
import io.github.monkeyadluffy.studioremote.data.ROLE_USER
import io.github.monkeyadluffy.studioremote.data.RunFrame
import io.github.monkeyadluffy.studioremote.data.Settings
import io.github.monkeyadluffy.studioremote.data.UiMessage
import io.github.monkeyadluffy.studioremote.data.applying
import io.github.monkeyadluffy.studioremote.data.chatRequestBody
import io.github.monkeyadluffy.studioremote.data.finished
import io.github.monkeyadluffy.studioremote.data.normalizeServerUrl
import io.github.monkeyadluffy.studioremote.data.str
import io.github.monkeyadluffy.studioremote.data.toStored
import io.github.monkeyadluffy.studioremote.data.toUi
import io.github.monkeyadluffy.studioremote.data.downloadDictationModel
import io.github.monkeyadluffy.studioremote.data.transcribe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID

enum class Screen { Splash, Setup, Main }

data class UiState(
    val screen: Screen = Screen.Splash,
    val settings: Settings = Settings(),
    val threads: List<ChatThread> = emptyList(),
    val projects: List<ChatProject> = emptyList(),
    val threadsLoading: Boolean = false,
    val currentThreadId: String? = null,
    val conversation: Conversation = Conversation(),
    val messagesLoading: Boolean = false,
    val generating: Boolean = false,
    val status: InferenceStatus? = null,
    val models: List<CachedGguf> = emptyList(),
    val modelsLoading: Boolean = false,
    /** Variant picker for one repo; null shows the repo list. */
    val variants: GgufVariants? = null,
    val variantsLoading: Boolean = false,
    /** Label of the model being loaded, while a switch is in progress. */
    val modelSwitching: String? = null,
    val error: String? = null,
    /** Files attached in the composer, indexed into the current thread's knowledge base. */
    val pendingFiles: List<PendingFile> = emptyList(),
    /** Indexed documents in the current thread; any turns on knowledge-base search. */
    val threadDocCount: Int = 0,
    /** Thread created early by a file upload, still carrying its placeholder title. */
    val untitledThreadId: String? = null,
) {
    /** The branch of the conversation on screen. */
    val messages: List<UiMessage> get() = conversation.path
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private val settings get() = _state.value.settings

    private val api = Api(
        onTokens = { t -> saveSettings(settings.copy(accessToken = t.accessToken, refreshToken = t.refreshToken)) },
        onSessionExpired = { _state.update { it.copy(error = str(R.string.session_expired)) } },
    )

    /** Whether the activity is on screen (set by MainActivity); off-screen replies notify when done. */
    @Volatile var appVisible: Boolean = true

    /** The reply currently streaming into the open chat. */
    private class ActiveReply(val cancelId: String, val assistantId: String) {
        lateinit var job: Job
        var threadId: String? = null
        /** Set once Studio owns the generation (durable run). */
        var runId: String? = null
        /** The user left the chat; the server finishes the reply and a later visit reattaches. */
        var detached = false
        /** The reply as it was when the user left a legacy stream, which then stops. */
        var lastSeen: UiMessage? = null
    }

    private var reply: ActiveReply? = null
    private val researchJobs = HashMap<String, Job>()

    private fun report(e: Throwable) = _state.update { it.copy(error = describe(e)) }

    /** Downloading, deleting and searching models. */
    val hub = ModelHub(api, viewModelScope, ::report, onInventoryChanged = { if (_state.value.models.isNotEmpty()) loadModels() })

    /** Image, video and speech generation. */
    val media = MediaStudio(api, viewModelScope, app.cacheDir, ::report)

    init {
        viewModelScope.launch {
            val s = prefs.load()
            api.baseUrl = s.serverUrl
            api.accessToken = s.accessToken
            api.refreshToken = s.refreshToken
            _state.update { it.copy(settings = s, screen = if (s.loggedIn) Screen.Main else Screen.Setup) }
            if (s.loggedIn) refreshAll()
        }
    }

    // ---------------- helpers ----------------

    private suspend fun saveSettings(s: Settings) {
        _state.update { it.copy(settings = s) }
        prefs.save(s)
    }

    private fun updateSettings(change: (Settings) -> Settings) {
        viewModelScope.launch { saveSettings(change(settings)) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    /** A string in the language picked in Settings (the application context alone follows the phone). */
    private fun str(@StringRes id: Int, vararg args: Any): String = AppLanguage.wrap(getApplication()).getString(id, *args)

    private fun describe(e: Throwable): String = when (e) {
        // FastAPI's bare "Not Found" means the route itself is gone, not a missing chat or model.
        is ApiException if e.code == 404 && e.message == "Not Found" -> str(R.string.studio_incompatible)
        is ApiException -> unsupportedArchitecture(e.message) ?: e.message ?: str(e.fallback, e.code)
        is LocalizedException -> e.message ?: str(e.res)
        is SerializationException -> str(R.string.studio_incompatible)
        is UnknownHostException -> str(R.string.server_unreachable)
        is SocketTimeoutException -> str(R.string.server_not_responding)
        else -> e.message ?: e.javaClass.simpleName
    }

    /** Studio's "llama.cpp does not recognise this architecture" error, explained. */
    private fun unsupportedArchitecture(message: String?): String? {
        val arch = ARCHITECTURE_ERROR.find(message.orEmpty())?.groupValues?.get(1) ?: return null
        val hint = if (arch.contains("whisper", ignoreCase = true)) R.string.unsupported_hint_whisper else R.string.unsupported_hint_audio
        return listOf(str(R.string.unsupported_architecture, arch), str(hint), str(R.string.unsupported_delete_hint)).joinToString(" ")
    }

    /** Launches [block], reporting failures through the snackbar. */
    private fun launchReporting(block: suspend () -> Unit) = viewModelScope.launchReporting(::report, block)

    private fun newId(): String = (1..7).map { ID_CHARS.random() }.joinToString("")

    private fun updateMessage(id: String, change: (UiMessage) -> UiMessage) {
        _state.update { s -> s.copy(conversation = s.conversation.update(id, change)) }
    }

    private fun message(id: String): UiMessage? = _state.value.conversation[id]

    private fun isCurrent(threadId: String) = _state.value.currentThreadId == threadId

    // ---------------- setup / auth ----------------

    suspend fun checkServer(rawUrl: String): Result<AuthStatus> = runCatching {
        api.baseUrl = normalizeServerUrl(rawUrl)
        api.authStatus()
    }

    suspend fun login(rawUrl: String, username: String, password: String, displayName: String): Result<Unit> =
        runCatching {
            api.baseUrl = normalizeServerUrl(rawUrl)
            val token = api.login(username.trim(), password)
            finishLogin(username.trim(), displayName, token.accessToken, token.refreshToken)
        }

    /** Alternative to the password: an `sk-unsloth-…` key created in Studio, used directly as the bearer. */
    suspend fun loginWithKey(rawUrl: String, key: String, displayName: String): Result<Unit> =
        runCatching {
            api.baseUrl = normalizeServerUrl(rawUrl)
            api.accessToken = key.trim()
            api.refreshToken = ""
            try {
                api.threads() // proves the key works
            } catch (e: Throwable) {
                api.accessToken = ""
                throw e
            }
            finishLogin("unsloth", displayName, key.trim(), "")
        }

    private suspend fun finishLogin(username: String, displayName: String, access: String, refresh: String) {
        saveSettings(
            settings.copy(
                serverUrl = api.baseUrl,
                username = username,
                displayName = displayName.trim(),
                accessToken = access,
                refreshToken = refresh,
            )
        )
        _state.update { it.copy(screen = Screen.Main) }
        refreshAll()
    }

    fun logout() {
        stop()
        api.accessToken = ""
        api.refreshToken = ""
        updateSettings { it.copy(accessToken = "", refreshToken = "") }
        _state.update { UiState(screen = Screen.Setup, settings = it.settings) }
    }

    /** The tunnel link changed: keep the session, just point at the new address. */
    fun changeServer(rawUrl: String) {
        api.baseUrl = normalizeServerUrl(rawUrl)
        updateSettings { it.copy(serverUrl = api.baseUrl) }
        refreshAll()
    }

    fun updatePreferences(temperature: Float, systemPrompt: String, displayName: String) =
        updateSettings { it.copy(temperature = temperature, systemPrompt = systemPrompt, displayName = displayName.trim()) }

    // ---------------- threads ----------------

    fun refreshAll() {
        refreshThreads()
        refreshStatus()
    }

    fun refreshThreads() = launchReporting {
        _state.update { it.copy(threadsLoading = true) }
        try {
            val threads = api.threads().filter { !it.archived }.sortedByDescending { it.sortKey }
            val projects = api.projects().filter { !it.archived }
            _state.update { it.copy(threads = threads, projects = projects) }
        } finally {
            _state.update { it.copy(threadsLoading = false) }
        }
    }

    fun newChat() {
        detach()
        _state.update {
            it.copy(currentThreadId = null, conversation = Conversation(), pendingFiles = emptyList(), threadDocCount = 0, untitledThreadId = null)
        }
    }

    fun openThread(id: String) {
        if (isCurrent(id)) return
        detach()
        _state.update {
            it.copy(
                currentThreadId = id, conversation = Conversation(), messagesLoading = true,
                pendingFiles = emptyList(), threadDocCount = 0, untitledThreadId = null,
            )
        }
        viewModelScope.launch {
            try {
                val conversation = Conversation.of(api.messages(id).map { it.toUi() })
                if (!isCurrent(id)) return@launch
                _state.update { it.copy(conversation = conversation, messagesLoading = false) }
                conversation.nodes.forEach { m -> m.researchRunId?.let { trackResearch(m.id, it) } }
                recoverBlankReplies(id)
                val docs = api.threadDocuments(id).count { it.usable }
                if (isCurrent(id)) _state.update { it.copy(threadDocCount = docs) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (isCurrent(id)) {
                    report(e)
                    _state.update { it.copy(messagesLoading = false) }
                }
            }
        }
    }

    fun renameThread(id: String, title: String) = launchReporting {
        api.renameThread(id, title)
        _state.update { s -> s.copy(threads = s.threads.map { if (it.id == id) it.copy(title = title) else it }) }
    }

    fun deleteThread(id: String) = launchReporting {
        api.deleteThread(id)
        _state.update { s -> s.copy(threads = s.threads.filterNot { it.id == id }) }
        if (isCurrent(id)) newChat()
    }

    fun createProject(name: String) = launchReporting {
        api.createProject(name.trim())
        refreshThreads()
    }

    fun renameProject(id: String, name: String) = launchReporting {
        api.renameProject(id, name.trim())
        refreshThreads()
    }

    /** Deletes the folder together with its chats (that's how Studio does it). */
    fun deleteProject(id: String) = launchReporting {
        val current = _state.value.threads.firstOrNull { it.id == _state.value.currentThreadId }
        api.deleteProject(id)
        if (current?.projectId == id) newChat()
        refreshThreads()
    }

    fun moveThread(threadId: String, projectId: String?) = launchReporting {
        api.moveThread(threadId, projectId)
        _state.update { s -> s.copy(threads = s.threads.map { if (it.id == threadId) it.copy(projectId = projectId) else it }) }
    }

    /** The chat's visible branch as Markdown, for the share sheet. */
    suspend fun exportThread(id: String): Result<String> = runCatching {
        val title = _state.value.threads.firstOrNull { it.id == id }?.title.orEmpty()
        val path = if (isCurrent(id)) _state.value.messages else Conversation.of(api.messages(id).map { it.toUi() }).path
        buildString {
            if (title.isNotBlank()) append("# ").append(title).append("\n\n")
            path.filter { it.text.isNotBlank() }.forEach { m ->
                append(if (m.isUser) "**Sen:**\n" else "**Model:**\n").append(m.text.trim()).append("\n\n")
            }
        }.trim()
    }

    /**
     * Returns the open thread, creating it on the server first if this is a new chat. A thread
     * created for a file upload ([title] null) gets its real title with the first message.
     */
    private suspend fun ensureThread(title: String?): String {
        val s = _state.value
        s.currentThreadId?.let { id ->
            if (title != null && s.untitledThreadId == id) {
                _state.update { it.copy(untitledThreadId = null) }
                renameThread(id, threadTitle(title))
            }
            return id
        }
        val st = s.status ?: runCatching { api.status() }.getOrNull()
        val now = System.currentTimeMillis()
        val created = api.createThread(
            ChatThread(
                id = "__LOCALID_" + newId(), // Studio's own id scheme for client-created threads
                title = title?.let(::threadTitle) ?: str(R.string.new_chat),
                modelId = st?.modelId.orEmpty(),
                modelGgufVariant = st?.ggufVariant,
                createdAt = now,
                updatedAt = now,
            )
        )
        _state.update {
            it.copy(
                currentThreadId = created.id,
                threads = listOf(created) + it.threads,
                untitledThreadId = if (title == null) created.id else null,
            )
        }
        return created.id
    }

    private fun threadTitle(text: String) = if (text.length > 120) text.take(120) + "…" else text

    // ---------------- models ----------------

    fun refreshStatus() {
        viewModelScope.launch {
            runCatching { api.status() }.onSuccess { st -> _state.update { it.copy(status = st) } }
        }
    }

    fun loadModels() = launchReporting {
        _state.update { it.copy(modelsLoading = true, variants = null) }
        try {
            val models = api.cachedModels().filter { it.isTextModel }.sortedByDescending { it.lastModified }
            _state.update { it.copy(models = models) }
            refreshStatus()
        } finally {
            _state.update { it.copy(modelsLoading = false) }
        }
    }

    fun loadVariants(repoId: String) = launchReporting {
        _state.update { it.copy(variantsLoading = true, variants = GgufVariants(repoId = repoId)) }
        try {
            val v = api.variants(repoId)
            _state.update { it.copy(variants = GgufVariants(repoId, v.variants.filter { x -> x.downloaded })) }
        } catch (e: Throwable) {
            _state.update { it.copy(variants = null) }
            throw e
        } finally {
            _state.update { it.copy(variantsLoading = false) }
        }
    }

    fun clearVariants() = _state.update { it.copy(variants = null) }

    /** Loads a chat (or speech) model; 0 / -1 / null options leave the choice to Studio. */
    /**
     * Loads a chat (or speech) model. Studio unloads the current model first, so when the new one
     * fails to load, the previous one is loaded back rather than leaving nothing loaded.
     */
    fun switchModel(repoId: String, variant: String?, contextLength: Int = 0, gpuLayers: Int = -1, cacheTypeKv: String? = null) =
        launchReporting {
            val previous = _state.value.status?.takeIf { it.modelId != null && !it.modelId.equals(repoId, ignoreCase = true) }
            try {
                loadAndWait(LoadRequest(repoId, variant, maxSeqLength = contextLength, gpuLayers = gpuLayers, cacheTypeKv = cacheTypeKv))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (previous == null) throw e
                _state.update { it.copy(error = describe(e) + "\n\n" + str(R.string.restoring_previous_model, previous.label.orEmpty())) }
                loadAndWait(LoadRequest(previous.modelId!!, previous.ggufVariant))
            }
        }

    private suspend fun loadAndWait(request: LoadRequest) {
        _state.update { it.copy(modelSwitching = request.ggufVariant?.let { v -> "${request.modelPath}:$v" } ?: request.modelPath, variants = null) }
        try {
            api.load(request)
            // The load call can return before the runtime is ready; poll until it settles (≤6 min).
            repeat(240) {
                val st = runCatching { api.status() }.getOrNull()
                if (st != null) {
                    _state.update { it.copy(status = st) }
                    if (!st.isLoading && st.modelId != null) return
                }
                delay(1500)
            }
        } finally {
            _state.update { it.copy(modelSwitching = null) }
            refreshStatus()
        }
    }

    /** Frees the GPU memory held by the loaded model. */
    fun unloadModel() {
        val id = _state.value.status?.modelId ?: return
        launchReporting {
            api.unload(id)
            _state.update { it.copy(status = runCatching { api.status() }.getOrNull()) }
        }
    }

    // ---------------- composer options ----------------

    fun setReasoningLevel(level: String) = updateSettings { it.copy(reasoningLevel = level) }
    fun setWebSearch(on: Boolean) = updateSettings { it.copy(webSearch = on) }
    fun setDeepResearch(on: Boolean) = updateSettings { it.copy(deepResearch = on) }
    fun setCodeTools(on: Boolean) = updateSettings { it.copy(codeTools = on) }
    fun setPermissionMode(mode: String) = updateSettings { it.copy(permissionMode = mode) }
    fun setStudioDictation(on: Boolean) = updateSettings { it.copy(studioDictation = on) }

    /**
     * Transcribes a recording with Studio's Whisper. When the dictation model isn't downloaded
     * yet, starts that download and says so.
     */
    suspend fun transcribe(audio: ByteArray): String? = try {
        api.transcribe(android.util.Base64.encodeToString(audio, android.util.Base64.NO_WRAP), language = null)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        if (e is ApiException && e.code == 409 && e.message.orEmpty().contains("download", ignoreCase = true)) {
            runCatching { api.downloadDictationModel() }
            _state.update { it.copy(error = str(R.string.whisper_downloading)) }
        } else {
            report(e)
        }
        null
    }

    private fun buildOptions(threadId: String): ChatOptions {
        val s = settings
        val ragOn = _state.value.threadDocCount > 0
        val (thinking, effort) = reasoningParams(s.reasoningLevel, _state.value.status)
        return ChatOptions(
            temperature = s.temperature,
            enableThinking = thinking,
            reasoningEffort = effort,
            enabledTools = buildList {
                if (ragOn) add("search_knowledge_base")
                if (s.webSearch) addAll(listOf("web_search", "web_fetch"))
                if (s.codeTools) addAll(listOf("python", "terminal", "edit_file"))
            },
            permissionMode = s.permissionMode,
            deepResearch = s.deepResearch,
            ragThreadId = threadId.takeIf { ragOn },
        )
    }

    /** Maps the saved level onto whichever knob (enable_thinking / reasoning_effort) the loaded template reads. */
    private fun reasoningParams(level: String, st: InferenceStatus?): Pair<Boolean?, String?> {
        if (level == "auto" || st == null) return null to null
        val flag = st.usesEnableThinking
        return when (level) {
            "off" -> (if (flag) false else null) to (if (st.usesEffort) "none" else null)
            "on" -> (if (flag) true else null) to null
            else -> (if (flag) true else null) to (if (st.usesEffort) level else null)
        }
    }

    // ---------------- files ----------------

    fun attachFile(name: String, mime: String, bytes: ByteArray) {
        val key = newId()
        _state.update { it.copy(pendingFiles = it.pendingFiles + PendingFile(key, name)) }
        fun update(change: (PendingFile) -> PendingFile) =
            _state.update { s -> s.copy(pendingFiles = s.pendingFiles.map { if (it.key == key) change(it) else it }) }

        viewModelScope.launch {
            try {
                val threadId = ensureThread(title = null)
                val jobId = api.uploadThreadDocument(threadId, name, mime, bytes).jobId
                update { it.copy(status = PendingFile.Status.Indexing) }
                repeat(600) { // up to ~10 minutes of indexing
                    val job = runCatching { api.ragJob(jobId) }.getOrNull()
                    when (job?.status) {
                        "completed", "ready" -> {
                            update { it.copy(status = PendingFile.Status.Ready, progress = 1.0) }
                            _state.update { it.copy(threadDocCount = it.threadDocCount + 1) }
                            return@launch
                        }
                        "failed", "error", "cancelled" -> {
                            update { it.copy(status = PendingFile.Status.Failed, error = job.error ?: str(R.string.file_processing_failed)) }
                            return@launch
                        }
                        null -> {}
                        else -> update { it.copy(progress = job.progress) }
                    }
                    delay(1000)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                update { it.copy(status = PendingFile.Status.Failed, error = describe(e)) }
            }
        }
    }

    fun removePendingFile(key: String) =
        _state.update { s -> s.copy(pendingFiles = s.pendingFiles.filterNot { it.key == key }) }

    // ---------------- tool approvals ----------------

    fun answerTool(messageId: String, toolId: String, allow: Boolean) {
        val approvalId = message(messageId)?.tools?.firstOrNull { it.id == toolId }?.approvalId ?: return
        val threadId = _state.value.currentThreadId ?: return
        updateMessage(messageId) { m ->
            m.copy(tools = m.tools.map { if (it.id == toolId) it.copy(awaiting = false, denied = !allow) else it })
        }
        launchReporting { api.toolConfirm(threadId, approvalId, allow) }
    }

    // ---------------- deep research ----------------

    private fun trackResearch(messageId: String, runId: String) {
        if (researchJobs[runId]?.isActive == true) return
        researchJobs[runId] = viewModelScope.launch {
            var failures = 0
            try {
                while (true) {
                    val run = try {
                        api.researchRun(runId).also { failures = 0 }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        if (++failures > 10) return@launch
                        delay(3000)
                        continue
                    }
                    updateMessage(messageId) { it.copy(research = run, researchRunId = runId) }
                    if (run.terminal) {
                        if (run.status == "completed" && !run.report.isNullOrBlank()) {
                            updateMessage(messageId) { it.copy(text = run.report) }
                        }
                        refreshThreads()
                        return@launch
                    }
                    delay(2500)
                }
            } finally {
                researchJobs.remove(runId)
            }
        }
    }

    private suspend fun startResearch(threadId: String, userId: String, assistantId: String, question: String?, options: ChatOptions) {
        val run = api.createResearchRun(
            threadId = threadId,
            userMessageId = userId,
            assistantMessageId = assistantId,
            question = question,
            model = _state.value.status?.modelId.orEmpty(),
            options = options,
        )
        updateMessage(assistantId) { it.copy(research = run, researchRunId = run.id) }
        trackResearch(assistantId, run.id)
    }

    fun approveResearch(messageId: String) {
        val run = message(messageId)?.research ?: return
        launchReporting {
            val updated = api.approveResearch(run)
            updateMessage(messageId) { it.copy(research = updated) }
        }
    }

    fun cancelResearch(messageId: String) {
        val run = message(messageId)?.research ?: return
        viewModelScope.launch { api.cancelResearch(run.id) }
    }

    // ---------------- chat ----------------

    fun send(text: String, images: List<String>) {
        val s = _state.value
        val trimmed = text.trim()
        if ((trimmed.isEmpty() && images.isEmpty()) || s.generating) return
        if (s.pendingFiles.any { it.busy }) {
            _state.update { it.copy(error = str(R.string.files_still_processing)) }
            return
        }
        val user = UiMessage(
            id = newId(),
            role = ROLE_USER,
            text = trimmed,
            createdAt = System.currentTimeMillis(),
            images = images,
            files = s.pendingFiles.filter { it.status == PendingFile.Status.Ready }.map { it.name },
        )
        _state.update { it.copy(pendingFiles = emptyList()) }
        generate(history = s.messages, user = user, userSaved = false)
    }

    /** Resends an edited version of a user message as a new branch; the original stays reachable. */
    fun editMessage(id: String, newText: String) {
        val s = _state.value
        val original = s.conversation[id] ?: return
        if (s.generating || newText.isBlank()) return
        val user = original.copy(id = newId(), text = newText.trim(), createdAt = System.currentTimeMillis())
        generate(history = s.messages.takeWhile { it.id != id }, user = user, userSaved = false)
    }

    /** Switches the chat to the branch through message [id]. */
    fun selectBranch(id: String) {
        if (_state.value.generating) return
        _state.update { it.copy(conversation = it.conversation.focus(id)) }
    }

    fun regenerate() {
        val msgs = _state.value.messages
        if (_state.value.generating) return
        val lastUser = msgs.indexOfLast { it.isUser }
        if (lastUser < 0) return
        generate(history = msgs.subList(0, lastUser), user = msgs[lastUser], userSaved = true)
    }

    private fun generate(history: List<UiMessage>, user: UiMessage, userSaved: Boolean) {
        val user = if (userSaved) user else user.copy(parentId = history.lastOrNull()?.id)
        val assistant = UiMessage(
            id = newId(), role = ROLE_ASSISTANT, text = "",
            createdAt = System.currentTimeMillis() + 1, parentId = user.id, streaming = true,
        )
        _state.update { it.copy(conversation = it.conversation.add(user, assistant), generating = true) }

        // Run ids must match ^[A-Za-z0-9_-]+$.
        launchReply(assistant.id, cancelId = "m" + UUID.randomUUID().toString().replace("-", "")) {
            val threadId = ensureThread(user.text.ifEmpty { str(R.string.image) })
            this.threadId = threadId
            val conversation = buildList {
                if (settings.systemPrompt.isNotBlank()) add(ApiMessage("system", settings.systemPrompt))
                (history + user).filter { it.error == null && (it.promptText.isNotEmpty() || it.images.isNotEmpty()) }
                    .forEach { add(ApiMessage(it.role, it.promptText, it.images)) }
            }
            val body = chatRequestBody(conversation, buildOptions(threadId), threadId, cancelId)

            val saved = userSaved || runCatching {
                api.saveMessages(threadId, listOf(user.toStored(threadId)))
            }.isSuccess
            // Durable runs survive disconnects; Studio keeps image turns on the legacy stream.
            val durable = saved && (history + user).none { it.images.isNotEmpty() } &&
                startDurableRun(threadId, user.id, assistant.id, body)
            if (durable) {
                followRun(cancelId, assistant.id)
            } else {
                api.streamChat(body).collect { ev -> updateMessage(assistant.id) { it.applying(ev) } }
            }
        }
    }

    /** True when Studio accepted the run; false means fall back to the legacy stream. */
    private suspend fun ActiveReply.startDurableRun(threadId: String, userId: String, assistantId: String, body: JsonObject): Boolean =
        try {
            api.createChatRun(cancelId, threadId, userId, assistantId, body)
            runId = cancelId
            true
        } catch (e: ApiException) {
            if (e.code == 401) throw e
            false // older Studio or an unsupported request
        }

    /**
     * Rebuilds replies Studio generated while the chat was closed. Studio inserts the reply
     * message empty and never writes its text, so a reply that ran (or is still running) without
     * us following it is blank here; its durable event log has the content. The last one is
     * followed live, which also reattaches to a reply that is still being written.
     */
    private fun recoverBlankReplies(threadId: String) {
        val s = _state.value
        val blanks = s.conversation.nodes.filter { !it.isUser && it.generationRunId != null && !it.hasContent }
        val live = blanks.firstOrNull { it.id == s.messages.lastOrNull()?.id }
        if (live != null && !s.generating) {
            updateMessage(live.id) { it.copy(streaming = true) }
            _state.update { it.copy(generating = true) }
            launchReply(live.id, cancelId = live.generationRunId!!) {
                this.threadId = threadId
                runId = cancelId
                followRun(cancelId, live.id, failIfCancelledEmpty = true)
            }
        }
        // Replies on other branches are rebuilt quietly.
        blanks.filter { it.id != live?.id }.forEach { m ->
            viewModelScope.launch {
                runCatching { followRun(m.generationRunId!!, m.id) }
                message(m.id)?.takeIf { it.hasContent }?.let { persistReply(threadId, it.finished()) }
            }
        }
    }

    /**
     * Runs one assistant reply. Holds the foreground service so the stream survives the screen
     * turning off, and whatever the outcome settles the message, saves it and notifies.
     */
    private fun launchReply(assistantId: String, cancelId: String, produce: suspend ActiveReply.() -> Unit) {
        val ctx = getApplication<Application>()
        val r = ActiveReply(cancelId, assistantId)
        reply = r
        r.job = viewModelScope.launch {
            GenerationService.start(ctx)
            var completed = false
            try {
                r.produce()
                completed = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateMessage(assistantId) { it.copy(error = describe(e)) }
            } finally {
                GenerationService.stop(ctx)
                if (reply === r) {
                    reply = null
                    _state.update { it.copy(generating = false) }
                }
                if (!r.detached) {
                    updateMessage(assistantId) { m -> m.finished().let { if (completed) it.copy(error = null) else it } }
                    // A stopped legacy stream may belong to a chat that is no longer on screen.
                    val final = message(assistantId) ?: r.lastSeen?.finished()
                    if (final != null) {
                        if (completed && !appVisible && final.text.isNotBlank()) GenerationService.notifyDone(ctx, final.text)
                        persistReply(r.threadId, final)
                    }
                }
            }
        }
    }

    /**
     * Follows a durable run until it settles, reconnecting from the last seen seq whenever the
     * connection drops (screen off, network switch, tunnel hiccup). Studio keeps generating
     * meanwhile, so nothing is lost.
     */
    private suspend fun followRun(runId: String, assistantId: String, failIfCancelledEmpty: Boolean = false) {
        var cursor = 0L
        var failures = 0
        while (true) {
            var finished: RunFrame.Finished? = null
            try {
                api.followChatRun(runId, cursor).collect { frame ->
                    cursor = frame.seq
                    if (failures > 0) {
                        failures = 0
                        updateMessage(assistantId) { it.copy(error = null) }
                    }
                    when (frame) {
                        is RunFrame.Events -> frame.events.forEach { ev -> updateMessage(assistantId) { it.applying(ev) } }
                        is RunFrame.Finished -> finished = frame
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (e is ApiException && e.code in 400..499 && e.code !in RETRYABLE) throw e
                failures++
                if (failures >= 3) updateMessage(assistantId) { it.copy(error = str(R.string.connection_lost)) }
                delay(minOf(1000L * failures, 5000L))
                continue
            }
            val end = finished ?: continue // closed before the end: reconnect
            if (end.status == "failed") throw ApiException(500, end.error, R.string.reply_failed)
            if (end.status == "cancelled" && failIfCancelledEmpty && message(assistantId)?.hasContent != true) {
                throw ApiException(499, null, R.string.reply_stopped)
            }
            return // completed or cancelled
        }
    }

    /** Saves the settled reply and, if it handed off to Deep Research, starts that run. */
    private fun persistReply(threadId: String?, final: UiMessage) {
        if (threadId == null) return
        val research = final.tools.firstOrNull { it.startedResearch }
        val userId = final.parentId
        val options = buildOptions(threadId)
        viewModelScope.launch {
            if (final.hasContent) runCatching { api.saveMessages(threadId, listOf(final.toStored(threadId))) }
            if (research != null && userId != null) {
                runCatching { startResearch(threadId, userId, final.id, research.args?.str("question"), options) }
                    .onFailure { e -> if (e !is CancellationException) report(e) }
            }
            refreshThreads()
        }
    }

    /** User pressed Stop: end the reply on the server too. */
    fun stop() {
        val r = reply?.takeIf { it.job.isActive } ?: return
        r.job.cancel()
        viewModelScope.launch {
            r.runId?.let { api.cancelChatRun(it) }
            api.cancel(r.cancelId)
        }
    }

    /** Leaving the chat mid-reply: a durable run keeps going on the server; a legacy stream is stopped. */
    private fun detach() {
        val r = reply ?: return
        if (r.runId == null) {
            r.lastSeen = message(r.assistantId)
            stop()
            return
        }
        r.detached = true
        r.job.cancel()
        reply = null
        _state.update { it.copy(generating = false) }
    }

    private companion object {
        val ARCHITECTURE_ERROR = Regex("""does not recognise this GGUF's model architecture \('([^']+)'\)""")


        const val ID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val RETRYABLE = setOf(401, 408, 429)
    }
}
