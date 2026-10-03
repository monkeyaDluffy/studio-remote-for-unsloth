package io.github.monkeyadluffy.studioremote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageCodecTest {
    @Test
    fun normalizesPastedLinks() {
        assertEquals("https://a.trycloudflare.com", normalizeServerUrl(" a.trycloudflare.com/chat?x=1 "))
        assertEquals("http://192.168.1.4:8888", normalizeServerUrl("http://192.168.1.4:8888/"))
        assertEquals("", normalizeServerUrl("  "))
    }

    @Test
    fun parsesContentReasoningAndUsage() {
        val chunk = parseObject(
            """{"choices":[{"delta":{"reasoning_content":"hmm","content":"Hi"}}],"timings":{"predicted_per_second":12.5}}"""
        )!!
        assertEquals(
            listOf(StreamEvent.Reasoning("hmm"), StreamEvent.Content("Hi"), StreamEvent.Usage(12.5)),
            parseChunk(chunk),
        )
    }

    @Test
    fun parsesToolFrames() {
        val start = parseChunk(parseObject(
            """{"type":"tool_start","tool_name":"web_search","tool_call_id":"c1","arguments_text":"{\"query\":\"kedi\"}","approval_id":"a1","awaiting_confirmation":true}"""
        )!!).single() as StreamEvent.ToolStart
        assertEquals("c1", start.id)
        assertEquals("a1", start.approvalId)
        assertTrue(start.awaiting)

        val end = parseChunk(parseObject("""{"type":"tool_end","tool_call_id":"c1","result":{"text":"ok"}}""")!!)
        assertEquals(listOf(StreamEvent.ToolEnd("c1", "ok")), end)
    }

    @Test(expected = ApiException::class)
    fun inBandErrorThrows() {
        parseChunk(parseObject("""{"error":{"message":"boom"}}""")!!)
    }

    @Test
    fun reducerTimesThinkingAndTracksTools() {
        var m = UiMessage("a", ROLE_ASSISTANT, "", 0, streaming = true)
        m = m.applying(StreamEvent.Reasoning("let me think"), now = 1_000)
        m = m.applying(StreamEvent.Reasoning(" more"), now = 1_500)
        assertNull(m.reasoningMs)
        m = m.applying(StreamEvent.Content("Answer"), now = 4_000)
        assertEquals(3_000L, m.reasoningMs)
        assertEquals("let me think more", m.reasoning)

        m = m.applying(StreamEvent.ToolStart("t", DEEP_RESEARCH_TOOL, """{"question":"q?"}""", null, false))
        m = m.applying(StreamEvent.ToolEnd("t", "$DEEP_RESEARCH_STARTED on that question."))
        val tool = m.tools.single()
        assertTrue(tool.startedResearch)
        assertEquals("q?", tool.args?.str("question"))
        assertEquals("q?", tool.summary)

        val done = m.copy(tools = m.tools + ToolCall("x", "python", awaiting = true)).finished()
        assertFalse(done.streaming)
        assertTrue(done.tools.none { it.awaiting })
    }

    @Test
    fun storedRoundTripKeepsEverything() {
        val original = UiMessage(
            id = "m1", role = ROLE_ASSISTANT, text = "final", createdAt = 42,
            reasoning = "why", files = listOf("doc.pdf"), reasoningMs = 1200, tokensPerSecond = 3.5,
            tools = listOf(ToolCall("c", "web_search", """{"query":"x"}""", result = "found")),
        )
        val back = original.copy(parentId = "u1").toStored("t1").toUi()
        assertEquals("u1", back.parentId)
        assertEquals(original.text, back.text)
        assertEquals(original.reasoning, back.reasoning)
        assertEquals(original.files, back.files)
        assertEquals(original.reasoningMs, back.reasoningMs)
        assertEquals(original.tokensPerSecond, back.tokensPerSecond)
        assertEquals(original.tools, back.tools)
    }

    @Test
    fun conversationOpensOnNewestBranchAndSwitches() {
        fun msg(id: String, parent: String?, at: Long, role: String = ROLE_ASSISTANT) = UiMessage(id, role, id, at, parentId = parent)
        // u → a1 → u2 → a3, and a2 is a regeneration of a1 made later.
        val c = Conversation.of(listOf(msg("u", null, 1, ROLE_USER), msg("a1", "u", 2), msg("u2", "a1", 3, ROLE_USER), msg("a3", "u2", 4), msg("a2", "u", 5)))
        assertEquals(listOf("u", "a2"), c.path.map { it.id })
        assertEquals(listOf("a1", "a2"), c.siblings(c["a2"]!!).map { it.id })

        // Picking a1 follows its newest descendants down to a3.
        assertEquals(listOf("u", "a1", "u2", "a3"), c.focus("a1").path.map { it.id })

        val edited = c.add(msg("u3", null, 6, ROLE_USER))
        assertEquals(listOf("u3"), edited.path.map { it.id })
        assertEquals(2, edited.siblings(edited["u"]!!).size)
    }
}
