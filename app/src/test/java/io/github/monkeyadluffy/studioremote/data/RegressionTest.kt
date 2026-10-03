package io.github.monkeyadluffy.studioremote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegressionTest {
    @Test
    fun galleryParsesStudioDates() {
        // Trimmed from a real /api/inference/audio/gallery answer: created_at is an ISO string.
        val body = """{"audio":[{"id":"cead","url":"/x","prompt":"SELAM","model":"m","audio_type":"snac",
            "sample_rate":24000,"duration_s":2.987,"created_at":"2026-09-20T07:02:16Z","pinned":false,
            "archived":false,"order_at":1789887736.08}],"has_more":false}"""
        val items = json.decodeFromString<Map<String, List<GalleryItem>>>(body.replace("\"has_more\":false", "\"x\":[]"))
        assertEquals("SELAM", items.getValue("audio").single().prompt)
    }

    @Test
    fun replyStampedByASlowerClockStillShows() {
        // The phone's question is "newer" than Studio's reply because the computer's clock lags.
        val user = UiMessage("u", ROLE_USER, "soru", createdAt = 2_000)
        val reply = UiMessage("a", ROLE_ASSISTANT, "", createdAt = 1_000, parentId = "u")
        assertEquals(listOf("u", "a"), Conversation.of(listOf(user, reply)).path.map { it.id })
    }

    @Test
    fun mediaModelsAreNotChatModels() {
        fun gguf(task: String?) = CachedGguf("r", task = task)
        assertTrue(gguf("text-generation").isTextModel)
        assertTrue(gguf(null).isTextModel)
        assertFalse(gguf("text-to-image").isTextModel)
        assertFalse(gguf("text-to-speech").isTextModel)
        assertFalse(gguf("text-to-video").isTextModel)
    }
}
