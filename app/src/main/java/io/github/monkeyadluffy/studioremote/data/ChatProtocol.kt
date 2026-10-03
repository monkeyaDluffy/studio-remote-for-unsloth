package io.github.monkeyadluffy.studioremote.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

const val DEEP_RESEARCH_TOOL = "deep_research"

/** Opening of the tool result Studio returns once a Deep Research run has really started. */
const val DEEP_RESEARCH_STARTED = "Deep Research has started"

/** One decoded piece of a streamed reply. */
sealed interface StreamEvent {
    data class Content(val text: String) : StreamEvent
    data class Reasoning(val text: String) : StreamEvent
    data class Usage(val tokensPerSecond: Double) : StreamEvent
    data class ToolStart(
        val id: String,
        val name: String,
        val argsText: String,
        val approvalId: String?,
        val awaiting: Boolean,
    ) : StreamEvent
    data class ToolEnd(val id: String, val result: String) : StreamEvent
    data class ToolStatus(val text: String) : StreamEvent
}

/** A frame of a durable run's event log; [seq] is the resume cursor. */
sealed interface RunFrame {
    val seq: Long

    data class Events(override val seq: Long, val events: List<StreamEvent>) : RunFrame
    data class Finished(override val seq: Long, val status: String, val error: String?) : RunFrame
}

/** A conversation turn as sent to the OpenAI-compatible endpoint. */
data class ApiMessage(val role: String, val text: String, val images: List<String> = emptyList())

/** Per-request generation options, mirroring what the Studio web composer sends. */
data class ChatOptions(
    val temperature: Float,
    val enableThinking: Boolean? = null,
    val reasoningEffort: String? = null,
    val enabledTools: List<String> = emptyList(),
    val permissionMode: String = "off",
    val deepResearch: Boolean = false,
    val ragThreadId: String? = null,
)

/** Body for /chat/completions; also used verbatim as a durable run's payload. */
fun chatRequestBody(messages: List<ApiMessage>, o: ChatOptions, threadId: String, cancelId: String): JsonObject =
    buildJsonObject {
        put("model", "default")
        put("stream", true)
        put("temperature", o.temperature.toDouble())
        o.enableThinking?.let { put("enable_thinking", it) }
        o.reasoningEffort?.let { put("reasoning_effort", it) }
        put("session_id", threadId)
        put("thread_id", threadId)
        put("cancel_id", cancelId)
        put("permission_mode", o.permissionMode)
        // Studio sends confirm_tool_calls only outside "auto", which gates itself on risk.
        if (o.permissionMode != "auto") put("confirm_tool_calls", o.permissionMode == "ask")
        put("bypass_permissions", o.permissionMode == "full")
        if (o.deepResearch) put("deep_research_armed", true)
        val toolsOn = o.enabledTools.isNotEmpty() || o.deepResearch
        put("enable_tools", toolsOn)
        if (toolsOn) {
            put("enabled_tools", buildJsonArray { o.enabledTools.forEach { add(JsonPrimitive(it)) } })
            put("run_tools_locally", true)
            put("auto_heal_tool_calls", true)
            put("max_tool_calls_per_message", 25)
        }
        o.ragThreadId?.let { ragThread ->
            putJsonObject("rag_scope") {
                put("thread_id", ragThread)
                put("default_top_k", 5)
                put("mode", "hybrid")
                put("autoinject", true)
                put("autoinject_min_score", 0.7)
            }
        }
        putJsonObject("stream_options") { put("include_usage", true) }
        put("messages", buildJsonArray { messages.forEach { add(it.toJson()) } })
    }

private fun ApiMessage.toJson(): JsonElement = buildJsonObject {
    put("role", role)
    if (images.isEmpty()) {
        put("content", text)
    } else {
        put("content", buildJsonArray {
            images.forEach { img ->
                add(buildJsonObject {
                    put("type", "image_url")
                    putJsonObject("image_url") { put("url", img) }
                })
            }
            add(buildJsonObject {
                put("type", "text")
                put("text", text)
            })
        })
    }
}

/**
 * Decodes one OpenAI-style chunk, or one of Studio's UI control frames (tool cards), into
 * stream events. Throws [ApiException] for an in-band error chunk.
 */
fun parseChunk(obj: JsonObject): List<StreamEvent> {
    when (obj.str("type")) {
        "tool_start" -> return listOf(
            StreamEvent.ToolStart(
                id = obj.str("tool_call_id").orEmpty(),
                name = obj.str("tool_name") ?: "tool",
                argsText = obj.str("arguments_text") ?: obj["arguments"]?.toString().orEmpty(),
                approvalId = obj.str("approval_id")?.takeIf { it.isNotEmpty() },
                awaiting = obj.str("awaiting_confirmation") == "true",
            )
        )
        "tool_end" -> return listOf(StreamEvent.ToolEnd(obj.str("tool_call_id").orEmpty(), resultText(obj["result"])))
        "tool_status" -> return listOf(StreamEvent.ToolStatus(obj.str("content").orEmpty()))
        "tool_output", "tool_args", "reasoning_summary" -> return emptyList()
    }
    obj["error"]?.let { err -> throw ApiException(500, (err as? JsonObject)?.str("message") ?: err.toString()) }

    val delta = ((obj["choices"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("delta") as? JsonObject
    return buildList {
        delta?.str("reasoning_content")?.takeIf { it.isNotEmpty() }?.let { add(StreamEvent.Reasoning(it)) }
        delta?.str("content")?.takeIf { it.isNotEmpty() }?.let { add(StreamEvent.Content(it)) }
        (obj["timings"] as? JsonObject)?.str("predicted_per_second")?.toDoubleOrNull()
            ?.let { add(StreamEvent.Usage(it)) }
    }
}

/** Tool results arrive (and are stored) either as a bare string or as `{"text": …}`. */
fun resultText(result: JsonElement?): String = when (result) {
    null -> ""
    is JsonObject -> result.str("text") ?: result.toString()
    is JsonPrimitive -> result.content
    else -> result.toString()
}
