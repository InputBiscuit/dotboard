package io.github.inputbiscuit.dotboard.core

import io.github.inputbiscuit.dotboard.core.ldbws.FilterType
import io.github.inputbiscuit.dotboard.core.ldbws.LdbwsClient
import io.github.inputbiscuit.dotboard.core.data.DepartureException
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LdbwsClientTest {

    private val server = MockWebServer()

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private fun client(key: String = "test-key") =
        LdbwsClient(apiKey = key, baseUrl = server.url("/LDBWS/api/20220120"))

    private fun fixture(name: String) =
        requireNotNull(javaClass.classLoader.getResource(name)).readText()

    @Test fun `builds request with path, query and auth header`() = runTest {
        server.enqueue(MockResponse.Builder().body(fixture("depboard_PAD.json")).build())

        client().getDepBoardWithDetails("pad", numRows = 3, filterCrs = "rdg", filterType = FilterType.TO)

        val request = server.takeRequest()
        assertEquals("/LDBWS/api/20220120/GetDepBoardWithDetails/PAD", request.url.encodedPath)
        assertEquals("3", request.url.queryParameter("numRows"))
        assertEquals("RDG", request.url.queryParameter("filterCrs"))
        assertEquals("to", request.url.queryParameter("filterType"))
        assertEquals("test-key", request.headers["x-apikey"])
        assertEquals("application/json", request.headers["Accept"])
    }

    @Test fun `omits filter params when no filter`() = runTest {
        server.enqueue(MockResponse.Builder().body(fixture("depboard_PAD.json")).build())

        client().getDepBoardWithDetails("PAD")

        val url = server.takeRequest().url
        assertNull(url.queryParameter("filterCrs"))
        assertNull(url.queryParameter("filterType"))
    }

    @Test fun `parses board and ignores unknown fields`() = runTest {
        server.enqueue(MockResponse.Builder().body(fixture("depboard_PAD.json")).build())

        val board = client().getDepBoardWithDetails("PAD")

        assertEquals("London Paddington", board.locationName)
        assertEquals(4, board.trainServices.size)
        val first = board.trainServices.first()
        assertEquals("22:09", first.std)
        assertEquals("2", first.platform)
        assertEquals(4, first.subsequentCallingPoints.single().callingPoint.size)
    }

    @Test fun `401 maps to InvalidApiKey`() = runTest {
        server.enqueue(MockResponse.Builder().code(401).body(fixture("fault_invalid_key.json")).build())

        assertFailsWith<DepartureException.InvalidApiKey> { client().getDepBoardWithDetails("PAD") }
    }

    @Test fun `gateway fault code maps to InvalidApiKey even on other status`() = runTest {
        server.enqueue(MockResponse.Builder().code(400).body(fixture("fault_invalid_key.json")).build())

        assertFailsWith<DepartureException.InvalidApiKey> { client().getDepBoardWithDetails("PAD") }
    }

    @Test fun `500 maps to Http error`() = runTest {
        server.enqueue(MockResponse.Builder().code(500).body("oops").build())

        val e = assertFailsWith<DepartureException.Http> { client().getDepBoardWithDetails("PAD") }
        assertEquals(500, e.code)
    }

    @Test fun `non-JSON body maps to BadResponse`() = runTest {
        server.enqueue(MockResponse.Builder().body("<html>captive portal</html>").build())

        assertFailsWith<DepartureException.BadResponse> { client().getDepBoardWithDetails("PAD") }
    }

    @Test fun `blank key fails without a request`() = runTest {
        assertFailsWith<DepartureException.MissingApiKey> { client(key = " ").getDepBoardWithDetails("PAD") }
        assertEquals(0, server.requestCount)
    }

    @Test fun `connection failure maps to Network`() = runTest {
        val c = client()
        server.close()

        val e = assertFailsWith<DepartureException.Network> { c.getDepBoardWithDetails("PAD") }
        assertTrue(e.cause is java.io.IOException)
    }
}
