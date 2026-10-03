package io.github.monkeyadluffy.studioremote.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ApiTest {
    private val server = MockWebServer()
    private val rotated = mutableListOf<Token>()
    private lateinit var api: Api

    @Before
    fun setUp() {
        server.start()
        api = Api(onTokens = { rotated += it }, onSessionExpired = {})
        api.baseUrl = server.url("/").toString()
        api.accessToken = "old"
        api.refreshToken = "r1"
    }

    @After
    fun tearDown() = server.close()

    private fun sse(body: String) =
        MockResponse.Builder().addHeader("Content-Type", "text/event-stream").body(body).build()

    @Test
    fun expiredSessionIsRenewedOnceAndRetried() = runBlocking {
        server.enqueue(MockResponse.Builder().code(401).body("""{"detail":"expired"}""").build())
        server.enqueue(MockResponse.Builder().body("""{"access_token":"new","refresh_token":"r2"}""").build())
        server.enqueue(MockResponse.Builder().body("""{"threads":[{"id":"t1","createdAt":1}]}""").build())

        assertEquals(listOf("t1"), api.threads().map { it.id })
        assertEquals("Bearer old", server.takeRequest().headers["Authorization"])
        assertEquals("/api/auth/refresh", server.takeRequest().url.encodedPath)
        assertEquals("Bearer new", server.takeRequest().headers["Authorization"])
        assertEquals(listOf(Token("new", "r2")), rotated)
    }

    @Test
    fun streamsUntilDoneWithoutDroppingTokensForASlowCollector() = runBlocking {
        val count = 300 // well past the flow's 64-element buffer
        val body = buildString {
            repeat(count) { append("data: {\"choices\":[{\"delta\":{\"content\":\"$it \"}}]}\n\n") }
            append("data: [DONE]\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":\"after done\"}}]}\n\n")
        }
        server.enqueue(sse(body))

        val received = mutableListOf<StreamEvent>()
        api.streamChat(buildJsonObject {}).collect {
            if (received.size % 50 == 0) delay(20)
            received += it
        }
        assertEquals((0 until count).map { StreamEvent.Content("$it ") }, received)
        assertEquals("1", server.takeRequest().headers["X-Unsloth-Events"])
    }

    @Test
    fun followsDurableRunAndReportsTheEnd() = runBlocking {
        server.enqueue(sse(
            """
            id: 1
            event: run.started
            data: {"seq":1,"type":"run.started","payload":{},"run":{"status":"running"}}

            id: 2
            event: chunk
            data: {"seq":2,"type":"chunk","payload":{"choices":[{"delta":{"content":"Merhaba"}}]}}

            : keep-alive 123

            id: 3
            event: run.failed
            data: {"seq":3,"type":"run.failed","payload":{"status":"failed","error":"OOM"}}

            """.trimIndent() + "\n"
        ))

        val frames = api.followChatRun("run1", after = 0).toList()
        assertEquals(
            listOf(
                RunFrame.Events(1, emptyList()),
                RunFrame.Events(2, listOf(StreamEvent.Content("Merhaba"))),
                RunFrame.Finished(3, "failed", "OOM"),
            ),
            frames,
        )
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/inference/chat-runs/run1/events?after=0", request.url.encodedPath + "?" + request.url.encodedQuery)
    }

    @Test(expected = ApiException::class)
    fun httpErrorsSurfaceTheServerDetail(): Unit = runBlocking {
        server.enqueue(MockResponse.Builder().code(400).body("""{"detail":"bad model"}""").build())
        try {
            api.streamChat(buildJsonObject {}).toList()
        } catch (e: ApiException) {
            assertEquals("bad model", e.message)
            throw e
        }
    }
}
