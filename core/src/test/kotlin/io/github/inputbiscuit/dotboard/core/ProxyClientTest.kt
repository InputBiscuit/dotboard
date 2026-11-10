package io.github.inputbiscuit.dotboard.core

import io.github.inputbiscuit.dotboard.core.data.DepartureException
import io.github.inputbiscuit.dotboard.core.ldbws.ProxyClient
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ProxyClientTest {

    private val server = MockWebServer()

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private fun client() = ProxyClient(server.url("/"))

    private fun fixture(name: String) = requireNotNull(javaClass.classLoader.getResource(name)).readText()

    @Test fun `requests the canonical board path with no key`() = runTest {
        server.enqueue(MockResponse.Builder().body(fixture("depboard_PAD.json")).build())

        val board = client().board("pad", filterCrs = "rdg")

        val request = server.takeRequest()
        assertEquals("/v1/board/PAD", request.url.encodedPath)
        assertEquals("RDG", request.url.queryParameter("filter"))
        assertEquals(listOf("filter"), request.url.queryParameterNames.toList())
        assertNull(request.headers["x-apikey"])
        assertEquals("London Paddington", board.locationName)
    }

    @Test fun `omits the filter when there is none`() = runTest {
        server.enqueue(MockResponse.Builder().body(fixture("depboard_PAD.json")).build())
        client().board("PAD")
        assertEquals(0, server.takeRequest().url.querySize)
    }

    @Test fun `proxy errors are service errors, never a rejected key`() = runTest {
        for (code in listOf(401, 429, 502, 503)) {
            server.enqueue(MockResponse.Builder().code(code).body("""{"error":"x"}""").build())
            val e = assertFailsWith<DepartureException.Http> { client().board("PAD") }
            assertEquals(code, e.code)
        }
    }

    @Test fun `unreachable proxy is a network error`() = runTest {
        val c = client()
        server.close()
        assertFailsWith<DepartureException.Network> { c.board("PAD") }
    }
}
