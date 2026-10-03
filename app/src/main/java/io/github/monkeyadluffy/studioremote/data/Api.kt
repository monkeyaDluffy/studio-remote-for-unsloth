package io.github.monkeyadluffy.studioremote.data

import androidx.annotation.StringRes
import io.github.monkeyadluffy.studioremote.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** [message] is Studio's own wording when it sent one; otherwise the app shows [fallback] (with [code]). */
class ApiException(val code: Int, message: String?, @param:StringRes val fallback: Int = R.string.error_http) : IOException(message)

/** A failure the app words itself, in the chosen language; [detail] from the server wins when present. */
class LocalizedException(@param:StringRes val res: Int, detail: String? = null) : Exception(detail)

/** Normalises a pasted link: adds https:// and drops any path such as /chat. */
fun normalizeServerUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return trimmed
    val url = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    val pathStart = url.indexOf('/', url.indexOf("://") + 3)
    return if (pathStart > 0) url.substring(0, pathStart) else url
}

/**
 * Client for the Unsloth Studio REST API. Every request goes through [exchange], which renews
 * an expired session once and retries; rotated tokens are reported through [onTokens].
 */
class Api(
    private val onTokens: suspend (Token) -> Unit,
    private val onSessionExpired: suspend () -> Unit,
) {
    @Volatile var baseUrl: String = ""
    @Volatile var accessToken: String = ""
    @Volatile var refreshToken: String = ""

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(2, TimeUnit.MINUTES)
        .build()

    private val jsonType = "application/json".toMediaType()
    private val refreshLock = Mutex()

    // ---------------- plumbing ----------------

    internal fun request(path: String, method: String = "GET", body: RequestBody? = null, auth: Boolean = true): Request =
        Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .apply { if (auth && accessToken.isNotEmpty()) header("Authorization", "Bearer $accessToken") }
            .method(method, body ?: if (method == "GET") null else "{}".toRequestBody(jsonType))
            .build()

    internal fun jsonBody(text: String) = text.toRequestBody(jsonType)

    internal fun query(vararg params: Pair<String, String>) =
        params.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }

    /**
     * Executes the request [build] produces. On 401 it renews the session once and rebuilds the
     * request, so the retry carries the new token. [onCall] exposes the live call for cancellation.
     */
    internal suspend fun exchange(auth: Boolean = true, onCall: (Call) -> Unit = {}, build: () -> Request): Response =
        withContext(Dispatchers.IO) {
            val tokenUsed = accessToken
            fun run() = client.newCall(build()).also(onCall).execute()
            val first = run()
            if (auth && first.code == 401 && renewSession(tokenUsed)) {
                first.close()
                run()
            } else {
                first
            }
        }

    /**
     * Swaps the refresh token for a new pair. Serialised so parallel 401s don't each spend the
     * (single-use) refresh token; a caller whose token was already replaced just retries.
     */
    private suspend fun renewSession(staleToken: String): Boolean = refreshLock.withLock {
        if (accessToken != staleToken) return@withLock true
        if (refreshToken.isEmpty()) return@withLock false
        val body = jsonBody(json.encodeToString(RefreshRequest(refreshToken)))
        val response = runCatching { client.newCall(request("/api/auth/refresh", "POST", body, auth = false)).execute() }
            .getOrNull() ?: return@withLock false
        response.use {
            if (!it.isSuccessful) return@withLock false
            val token = json.decodeFromString<Token>(it.body.string())
            accessToken = token.accessToken
            refreshToken = token.refreshToken
            onTokens(token)
            true
        }
    }

    internal fun Response.toException(): ApiException {
        val raw = body.string()
        val detail = parseObject(raw)?.get("detail")?.toString()?.trim('"')
        return ApiException(code, detail ?: raw.take(300).ifBlank { "HTTP $code" })
    }

    /** Plain HTTP for hosts other than Studio (the Hugging Face search). */
    internal val http: OkHttpClient get() = client

    /**
     * Performs a JSON request and returns the response body. Slow Studio calls answer with a
     * padded body whose late failure arrives in-band as `_deferred_error`; that is raised here.
     */
    internal suspend fun call(path: String, method: String = "GET", body: String? = null, auth: Boolean = true): String =
        exchange(auth) { request(path, method, body?.let(::jsonBody), auth) }.use {
            if (!it.isSuccessful) {
                if (it.code == 401 && auth) onSessionExpired()
                throw it.toException()
            }
            val text = it.body.string()
            if (text.startsWith(" ")) {
                val err = parseObject(text.trim())?.get("_deferred_error") as? JsonObject
                if (err != null) throw ApiException(err.str("status_code")?.toIntOrNull() ?: 500, err["detail"]?.let(::resultText), R.string.request_failed)
            }
            text
        }

    private class SseEvent(val type: String, val data: String)

    /**
     * Streams server-sent events. Events are handed over with a suspending send, so a slow
     * collector applies back-pressure instead of losing tokens; cancelling the collector
     * cancels the HTTP call, which also unblocks a read that is waiting on a quiet server.
     */
    private fun sse(build: () -> Request): Flow<SseEvent> = callbackFlow {
        var call: Call? = null
        val reader = launch(Dispatchers.IO) {
            try {
                exchange(onCall = { call = it }, build = build).use { response ->
                    if (!response.isSuccessful) throw response.toException()
                    val source = response.body.source()
                    var type = ""
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        when {
                            line.isEmpty() -> type = ""
                            line.startsWith("event:") -> type = line.substringAfter(':').trim()
                            line.startsWith("data:") -> send(SseEvent(type, line.substringAfter(':').trim()))
                        }
                    }
                }
                channel.close()
            } catch (e: Throwable) {
                channel.close(e)
            }
        }
        awaitClose {
            call?.cancel()
            reader.cancel()
        }
    }.flowOn(Dispatchers.IO)

    // ---------------- auth ----------------

    suspend fun authStatus(): AuthStatus = json.decodeFromString(call("/api/auth/status", auth = false))

    suspend fun login(username: String, password: String): Token {
        val body = json.encodeToString(LoginRequest(username, password))
        val token = json.decodeFromString<Token>(call("/api/auth/login", "POST", body, auth = false))
        accessToken = token.accessToken
        refreshToken = token.refreshToken
        return token
    }

    // ---------------- chat history ----------------

    suspend fun threads(): List<ChatThread> = json.decodeFromString<ThreadList>(call("/api/chat/threads")).threads

    /** Folders; optional, so an older Studio without them just shows none. */
    suspend fun projects(): List<ChatProject> =
        runCatching { json.decodeFromString<ProjectList>(call("/api/chat/projects")).projects }.getOrDefault(emptyList())

    suspend fun createThread(t: ChatThread): ChatThread =
        json.decodeFromString(call("/api/chat/threads", "POST", json.encodeToString(t)))

    suspend fun renameThread(id: String, title: String) {
        call("/api/chat/threads/$id", "PATCH", json.encodeToString(ThreadPatch(title)))
    }

    /** Moves a chat into a folder, or out of any folder when [projectId] is null. */
    suspend fun moveThread(id: String, projectId: String?) {
        // An explicit null is what tells Studio to clear the folder; a missing key would leave it.
        call("/api/chat/threads/$id", "PATCH", buildJsonObject { put("projectId", projectId) }.toString())
    }

    suspend fun createProject(name: String): ChatProject {
        val now = System.currentTimeMillis()
        val project = ChatProject(id = "proj_" + java.util.UUID.randomUUID().toString().take(8), name = name, createdAt = now, updatedAt = now)
        return json.decodeFromString(call("/api/chat/projects", "POST", json.encodeToString(project)))
    }

    suspend fun renameProject(id: String, name: String) {
        call("/api/chat/projects/$id", "PATCH", buildJsonObject { put("name", name) }.toString())
    }

    /** Studio deletes the folder's chats along with it. */
    suspend fun deleteProject(id: String) {
        call("/api/chat/projects/$id", "DELETE")
    }

    suspend fun deleteThread(id: String) {
        call("/api/chat/threads", "DELETE", json.encodeToString(DeleteRequest(listOf(id))))
    }

    suspend fun messages(threadId: String): List<StoredMessage> =
        json.decodeFromString<MessageList>(call("/api/chat/threads/$threadId/messages")).messages

    /** Upserts messages by id. */
    suspend fun saveMessages(threadId: String, messages: List<StoredMessage>) {
        call("/api/chat/threads/$threadId/messages", "PUT", json.encodeToString(MessageSync(messages)))
    }

    // ---------------- models ----------------

    suspend fun status(): InferenceStatus = json.decodeFromString(call("/api/inference/status"))

    suspend fun cachedModels(): List<CachedGguf> =
        json.decodeFromString<CachedGgufList>(call("/api/models/cached-gguf")).cached

    suspend fun variants(repoId: String): GgufVariants = json.decodeFromString(
        call("/api/models/gguf-variants?" + query("repo_id" to repoId, "prefer_local_cache" to "true"))
    )

    suspend fun load(request: LoadRequest) {
        call("/api/inference/load", "POST", json.encodeToString(request))
    }

    suspend fun unload(modelPath: String) {
        call("/api/inference/unload", "POST", buildJsonObject { put("model_path", modelPath) }.toString())
    }

    // ---------------- generation ----------------

    /** Legacy streaming: the generation lives only as long as this connection. */
    fun streamChat(body: JsonObject): Flow<StreamEvent> =
        sse {
            request("/api/inference/chat/completions", "POST", jsonBody(body.toString()))
                .newBuilder().header("X-Unsloth-Events", "1").build() // opt into tool-card frames
        }
            .takeWhile { it.data != "[DONE]" }
            .transform { event -> parseObject(event.data)?.let { parseChunk(it).forEach { e -> emit(e) } } }

    /** Best-effort cancel of a legacy stream. */
    suspend fun cancel(cancelId: String) {
        runCatching { call("/api/inference/cancel", "POST", buildJsonObject { put("cancel_id", cancelId) }.toString()) }
    }

    /**
     * Starts a generation Studio runs to completion by itself, so it survives the phone locking
     * or losing the network. Studio inserts the (empty) assistant message.
     */
    suspend fun createChatRun(runId: String, threadId: String, userMessageId: String, assistantMessageId: String, payload: JsonObject) {
        val body = buildJsonObject {
            put("runId", runId)
            put("threadId", threadId)
            put("userMessageId", userMessageId)
            put("assistantMessageId", assistantMessageId)
            // The server owns the cancel id of a durable run.
            put("requestPayload", JsonObject(payload - "cancel_id"))
        }
        call("/api/inference/chat-runs", "POST", body.toString())
    }

    suspend fun cancelChatRun(runId: String) {
        runCatching { call("/api/inference/chat-runs/$runId/cancel", "POST") }
    }

    /**
     * Follows a durable run's event log after [after]. Completes when the run settles or the
     * connection drops; the caller reconnects from the last seq it saw.
     */
    fun followChatRun(runId: String, after: Long): Flow<RunFrame> =
        // POST: quick tunnels buffer a streamed GET until it closes.
        sse { request("/api/inference/chat-runs/$runId/events?after=$after", "POST") }
            .transform { event ->
                val obj = parseObject(event.data) ?: return@transform
                val seq = obj.str("seq")?.toLongOrNull() ?: return@transform
                val payload = obj["payload"] as? JsonObject
                when (val type = obj.str("type") ?: event.type) {
                    "chunk" -> emit(RunFrame.Events(seq, payload?.let { runCatching { parseChunk(it) }.getOrNull() }.orEmpty()))
                    "run.completed", "run.failed", "run.cancelled" -> {
                        val error = payload?.str("error") ?: (obj["run"] as? JsonObject)?.str("error")
                        emit(RunFrame.Finished(seq, type.removePrefix("run."), error))
                    }
                    else -> emit(RunFrame.Events(seq, emptyList()))
                }
            }

    suspend fun toolConfirm(sessionId: String, approvalId: String, allow: Boolean) {
        val body = buildJsonObject {
            put("session_id", sessionId)
            put("approval_id", approvalId)
            put("decision", if (allow) "allow" else "deny")
        }
        call("/api/inference/tool-confirm", "POST", body.toString())
    }

    // ---------------- files (thread knowledge base) ----------------

    suspend fun uploadThreadDocument(threadId: String, filename: String, mime: String, bytes: ByteArray): RagUpload {
        val form = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", filename, bytes.toRequestBody(mime.toMediaType()))
            .build()
        return exchange { request("/api/rag/threads/$threadId/documents", "POST", form) }.use {
            if (!it.isSuccessful) throw it.toException()
            json.decodeFromString(it.body.string())
        }
    }

    suspend fun ragJob(jobId: String): RagJob = json.decodeFromString(call("/api/rag/jobs/$jobId"))

    suspend fun threadDocuments(threadId: String): List<RagDocument> = runCatching {
        json.decodeFromString<RagDocumentList>(call("/api/rag/threads/$threadId/documents")).documents
    }.getOrDefault(emptyList())

    // ---------------- deep research ----------------

    suspend fun createResearchRun(
        threadId: String,
        userMessageId: String,
        assistantMessageId: String,
        question: String?,
        model: String,
        options: ChatOptions,
    ): ResearchRun {
        val body = buildJsonObject {
            put("threadId", threadId)
            put("userMessageId", userMessageId)
            put("assistantMessageId", assistantMessageId)
            if (!question.isNullOrBlank()) put("question", question.take(2000))
            putJsonObject("inferenceRequest") {
                put("model", model)
                put("temperature", options.temperature.toDouble())
                put("topP", 0.95)
                put("maxTokens", 8192)
                options.enableThinking?.let { put("enableThinking", it) }
                options.reasoningEffort?.let { put("reasoningEffort", it) }
            }
            options.ragThreadId?.let { ragThread ->
                putJsonObject("ragScope") {
                    put("thread_id", ragThread)
                    put("default_top_k", 5)
                    put("mode", "hybrid")
                }
            }
        }
        return json.decodeFromString(call("/api/chat/research-runs", "POST", body.toString()))
    }

    suspend fun researchRun(id: String): ResearchRun = json.decodeFromString(call("/api/chat/research-runs/$id"))

    suspend fun approveResearch(run: ResearchRun): ResearchRun {
        val body = buildJsonObject {
            put("planRevision", run.planRevision)
            put("planHash", run.planHash.orEmpty())
        }
        return json.decodeFromString(call("/api/chat/research-runs/${run.id}/approve", "POST", body.toString()))
    }

    suspend fun cancelResearch(id: String) {
        runCatching { call("/api/chat/research-runs/$id/cancel", "POST") }
    }
}
