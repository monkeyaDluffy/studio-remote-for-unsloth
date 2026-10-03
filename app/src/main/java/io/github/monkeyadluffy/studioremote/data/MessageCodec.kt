package io.github.monkeyadluffy.studioremote.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

// Conversion between Studio's stored message format and the UI model, plus the pure reducer
// that folds streamed events into a reply. Keeping both directions here keeps them in sync.

fun StoredMessage.toUi(): UiMessage {
    val text = StringBuilder()
    val reasoning = mutableListOf<String>()
    val images = mutableListOf<String>()
    val tools = mutableListOf<ToolCall>()
    val files = mutableListOf<String>()
    when (val c = content) {
        is JsonPrimitive -> text.append(c.contentOrNull.orEmpty())
        is JsonArray -> c.forEach { element ->
            val part = element as? JsonObject ?: return@forEach
            when (part.str("type")) {
                "text" -> text.append(part.str("text").orEmpty())
                "reasoning" -> part.str("text")?.let { reasoning += it }
                "image", "image_url" -> {
                    val url = part.str("image") ?: (part["image_url"] as? JsonObject)?.str("url")
                    if (url != null && url.startsWith("data:")) images += url
                }
                "tool-call" -> tools += ToolCall(
                    id = part.str("toolCallId").orEmpty(),
                    name = part.str("toolName") ?: "tool",
                    argsText = part.str("argsText") ?: part["args"]?.toString().orEmpty(),
                    result = part["result"]?.takeIf { it != JsonNull }?.let(::resultText),
                )
                "file" -> part.str("filename")?.let { files += it }
            }
        }
        else -> {}
    }
    val meta = metadata as? JsonObject
    return UiMessage(
        id = id,
        role = role,
        text = text.toString(),
        createdAt = createdAt,
        parentId = parentId,
        reasoning = reasoning.joinToString("\n\n"),
        images = images,
        files = files,
        reasoningMs = meta?.str("reasoningDuration")?.toDoubleOrNull()?.toLong(),
        tokensPerSecond = (meta?.get("timing") as? JsonObject)?.str("tokensPerSecond")?.toDoubleOrNull(),
        tools = tools,
        researchRunId = meta?.str("researchRunId"),
        generationRunId = meta?.str("generationRunId"),
    )
}

/** Serialises in the assistant-ui part format the Studio web UI reads back. */
fun UiMessage.toStored(threadId: String): StoredMessage {
    val content = buildJsonArray {
        if (reasoning.isNotEmpty()) add(part("reasoning") { put("text", reasoning) })
        tools.forEach { t ->
            add(part("tool-call") {
                put("toolCallId", t.id)
                put("toolName", t.name)
                put("argsText", t.argsText)
                put("args", t.args ?: JsonObject(emptyMap()))
                t.result?.let { put("result", buildJsonObject { put("text", it) }) }
            })
        }
        images.forEach { img -> add(part("image") { put("image", img) }) }
        files.forEach { name -> add(part("file") { put("filename", name) }) }
        add(part("text") { put("text", text) })
    }
    val metadata = if (isUser) null else buildJsonObject {
        reasoningMs?.let { put("reasoningDuration", it) }
        tokensPerSecond?.let { tps -> put("timing", buildJsonObject { put("tokensPerSecond", tps) }) }
        put("source", "unsloth-mobile")
    }
    return StoredMessage(
        id = id, threadId = threadId, parentId = parentId, role = role,
        content = content, metadata = metadata, createdAt = createdAt,
    )
}

private inline fun part(type: String, fill: JsonObjectBuilder.() -> Unit) =
    buildJsonObject {
        put("type", type)
        fill()
    }

/** Folds one streamed event into the reply being built. */
fun UiMessage.applying(ev: StreamEvent, now: Long = System.currentTimeMillis()): UiMessage = when (ev) {
    is StreamEvent.Reasoning -> copy(reasoning = reasoning + ev.text, reasoningStartedAt = reasoningStartedAt ?: now)
    // The first answer token ends the thinking phase.
    is StreamEvent.Content -> copy(text = text + ev.text, reasoningMs = reasoningMs ?: reasoningStartedAt?.let { now - it })
    is StreamEvent.Usage -> copy(tokensPerSecond = ev.tokensPerSecond)
    is StreamEvent.ToolStatus -> copy(toolStatus = ev.text.ifBlank { null })
    is StreamEvent.ToolStart -> copy(
        tools = tools.filterNot { it.id == ev.id } +
            ToolCall(ev.id, ev.name, ev.argsText, approvalId = ev.approvalId, awaiting = ev.awaiting)
    )
    is StreamEvent.ToolEnd -> copy(
        toolStatus = null,
        tools = tools.map { if (it.id == ev.id) it.copy(result = ev.result, awaiting = false) else it },
    )
}

/** Settles a reply once its stream ends, however it ended. */
fun UiMessage.finished(now: Long = System.currentTimeMillis()): UiMessage = copy(
    streaming = false,
    toolStatus = null,
    tools = tools.map { it.copy(awaiting = false) },
    reasoningMs = reasoningMs ?: reasoningStartedAt?.let { now - it },
)
