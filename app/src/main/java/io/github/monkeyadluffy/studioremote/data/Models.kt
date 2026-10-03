package io.github.monkeyadluffy.studioremote.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Lenient on purpose: Studio's internal API changes between releases. */
val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

/** Parses [text] as a JSON object, or null when it is anything else. */
fun parseObject(text: String): JsonObject? = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()

// ---------------- auth ----------------

@Serializable
data class AuthStatus(
    @SerialName("default_username") val defaultUsername: String? = null,
    @SerialName("login_mode") val loginMode: String? = null,
) {
    /** Single-user Studio has one account; its web login asks only for the password. */
    val singleUser: Boolean get() = loginMode == null || loginMode == "single"
}

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class Token(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
)

// ---------------- chat history ----------------

@Serializable
data class ChatThread(
    val id: String,
    val title: String = "",
    val modelType: String = "base",
    val modelId: String = "",
    val modelGgufVariant: String? = null,
    val projectId: String? = null,
    val archived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long? = null,
) {
    val sortKey: Long get() = updatedAt ?: createdAt
}

@Serializable
data class ThreadList(val threads: List<ChatThread> = emptyList())

@Serializable
data class ChatProject(
    val id: String,
    val name: String = "",
    val archived: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class ProjectList(val projects: List<ChatProject> = emptyList())

@Serializable
data class ThreadPatch(val title: String)

@Serializable
data class DeleteRequest(val ids: List<String>)

/** A message as Unsloth Studio stores it: content is a list of typed parts. */
@Serializable
data class StoredMessage(
    val id: String,
    val threadId: String,
    val parentId: String? = null,
    val role: String,
    val content: JsonElement? = null,
    val attachments: JsonElement? = null,
    val metadata: JsonElement? = null,
    val createdAt: Long,
)

@Serializable
data class MessageList(val messages: List<StoredMessage> = emptyList())

@Serializable
data class MessageSync(val messages: List<StoredMessage>)

// ---------------- models ----------------

@Serializable
data class CachedGguf(
    @SerialName("repo_id") val repoId: String,
    @SerialName("size_bytes") val sizeBytes: Long = 0,
    @SerialName("has_vision") val hasVision: Boolean = false,
    val task: String? = null,
    val partial: Boolean = false,
    @SerialName("last_modified") val lastModified: Double = 0.0,
    @SerialName("model_format") val modelFormat: String? = null,
) {
    /** Anything that isn't an image/video/speech model can chat. */
    val isTextModel: Boolean get() = !partial && task !in MEDIA_TASKS
    val isGguf: Boolean get() = modelFormat == null || modelFormat == "gguf"
}

private val MEDIA_TASKS = setOf(
    "text-to-image", "image-to-image", "text-to-video", "image-to-video", "text-to-speech",
    "text-to-audio", "automatic-speech-recognition",
)

@Serializable
data class CachedGgufList(val cached: List<CachedGguf> = emptyList())

@Serializable
data class GgufVariant(
    val quant: String,
    val filename: String = "",
    @SerialName("display_label") val displayLabel: String? = null,
    @SerialName("size_bytes") val sizeBytes: Long = 0,
    @SerialName("download_size_bytes") val downloadSizeBytes: Long = 0,
    val downloaded: Boolean = false,
    val partial: Boolean = false,
) {
    val label: String get() = displayLabel ?: quant
}

@Serializable
data class GgufVariants(
    @SerialName("repo_id") val repoId: String = "",
    val variants: List<GgufVariant> = emptyList(),
)

@Serializable
data class LoadRequest(
    @SerialName("model_path") val modelPath: String,
    @SerialName("gguf_variant") val ggufVariant: String? = null,
    /** 0 lets llama.cpp size its own context window. */
    @SerialName("max_seq_length") val maxSeqLength: Int = 0,
    /** -1 lets Studio decide how many layers go to the GPU. */
    @SerialName("gpu_layers") val gpuLayers: Int = -1,
    /** KV cache precision (f16, q8_0, q4_0); null keeps Studio's default. */
    @SerialName("cache_type_kv") val cacheTypeKv: String? = null,
)

@Serializable
data class InferenceStatus(
    @SerialName("active_model") val activeModel: String? = null,
    @SerialName("model_identifier") val modelIdentifier: String? = null,
    @SerialName("gguf_variant") val ggufVariant: String? = null,
    @SerialName("is_vision") val isVision: Boolean = false,
    @SerialName("supports_tools") val supportsTools: Boolean = true,
    @SerialName("reasoning_style") val reasoningStyle: String? = null,
    @SerialName("reasoning_effort_levels") val reasoningEffortLevels: List<String>? = null,
    @SerialName("reasoning_always_on") val reasoningAlwaysOn: Boolean = false,
    @SerialName("is_audio") val isAudio: Boolean = false,
    @SerialName("audio_type") val audioType: String? = null,
    @SerialName("context_length") val contextLength: Int? = null,
    @SerialName("gpu_layers") val gpuLayers: Int? = null,
    @SerialName("cache_type_kv") val cacheTypeKv: String? = null,
    /** Varies between bool, list and object across Studio versions. */
    val loading: JsonElement? = null,
) {
    val modelId: String? get() = modelIdentifier ?: activeModel
    val isLoading: Boolean get() = loading.truthy()
    val label: String? get() = modelId?.let { id -> ggufVariant?.let { "$id:$it" } ?: id }

    /** The template takes an `enable_thinking` flag (the default when Studio doesn't say). */
    val usesEnableThinking: Boolean get() = reasoningStyle == null || reasoningStyle.startsWith("enable_thinking")
    /** The template takes a `reasoning_effort` level. */
    val usesEffort: Boolean get() = reasoningStyle?.contains("effort") == true
}

private fun JsonElement?.truthy(): Boolean = when (this) {
    is JsonPrimitive -> contentOrNull == "true"
    is JsonArray -> isNotEmpty()
    is JsonObject -> isNotEmpty()
    else -> false
}

// ---------------- files (thread knowledge base) ----------------

@Serializable
data class RagUpload(val jobId: String = "")

@Serializable
data class RagJob(val status: String = "", val progress: Double = 0.0, val error: String? = null)

@Serializable
data class RagDocument(val id: String, val status: String = "") {
    val usable: Boolean get() = status != "failed" && status != "error"
}

@Serializable
data class RagDocumentList(val documents: List<RagDocument> = emptyList())

// ---------------- deep research ----------------

@Serializable
data class ResearchPlanStep(val title: String = "", val query: String = "")

@Serializable
data class ResearchPlan(val title: String = "", val steps: List<ResearchPlanStep> = emptyList())

@Serializable
data class ResearchRun(
    val id: String,
    val status: String = "planning",
    val plan: ResearchPlan? = null,
    val planRevision: Int = 0,
    val planHash: String? = null,
    val report: String? = null,
    val error: String? = null,
) {
    val terminal: Boolean get() = status == "completed" || status == "failed" || status == "cancelled"
    val awaitingApproval: Boolean get() = status == "awaiting_approval"
}

// ---------------- UI-side models ----------------

data class ToolCall(
    val id: String,
    val name: String,
    val argsText: String = "",
    val result: String? = null,
    val approvalId: String? = null,
    val awaiting: Boolean = false,
    val denied: Boolean = false,
) {
    val args: JsonObject? get() = parseObject(argsText)

    /** True once Studio confirmed it handed the question to a Deep Research run. */
    val startedResearch: Boolean
        get() = name == DEEP_RESEARCH_TOOL && result?.startsWith(DEEP_RESEARCH_STARTED) == true

    /** One-line human summary of the arguments: the query, url, command… */
    val summary: String
        get() {
            val obj = args ?: return argsText.take(120)
            val value = SUMMARY_KEYS.firstNotNullOfOrNull { obj[it] } ?: return argsText.take(120)
            val text = when (value) {
                is JsonPrimitive -> value.content
                is JsonArray -> value.joinToString(", ") { (it as? JsonPrimitive)?.content ?: it.toString() }
                else -> value.toString()
            }
            return text.lineSequence().firstOrNull().orEmpty().take(120)
        }

    private companion object {
        val SUMMARY_KEYS = listOf("query", "question", "url", "command", "code", "path", "queries", "image_queries")
    }
}

/** A file attached in the composer and indexed into the thread's knowledge base. */
data class PendingFile(
    val key: String,
    val name: String,
    val status: Status = Status.Uploading,
    val progress: Double = 0.0,
    val error: String? = null,
) {
    enum class Status { Uploading, Indexing, Ready, Failed }

    val busy: Boolean get() = status == Status.Uploading || status == Status.Indexing
}

const val ROLE_USER = "user"
const val ROLE_ASSISTANT = "assistant"

data class UiMessage(
    val id: String,
    val role: String,
    val text: String,
    val createdAt: Long,
    val parentId: String? = null,
    val reasoning: String = "",
    val images: List<String> = emptyList(), // data: URLs
    val files: List<String> = emptyList(),
    val streaming: Boolean = false,
    /** When the first reasoning token arrived; used to time the thinking phase. */
    val reasoningStartedAt: Long? = null,
    val reasoningMs: Long? = null,
    val tokensPerSecond: Double? = null,
    val error: String? = null,
    val tools: List<ToolCall> = emptyList(),
    val toolStatus: String? = null,
    val researchRunId: String? = null,
    val research: ResearchRun? = null,
    /** Studio's durable run that generated this reply; its event log can rebuild a blank reply. */
    val generationRunId: String? = null,
) {
    val isUser: Boolean get() = role == ROLE_USER

    val hasContent: Boolean get() = text.isNotEmpty() || reasoning.isNotEmpty() || tools.isNotEmpty()

    /** What this turn contributes to the conversation sent back to the model. */
    val promptText: String get() = text.ifEmpty { research?.report.orEmpty() }
}
