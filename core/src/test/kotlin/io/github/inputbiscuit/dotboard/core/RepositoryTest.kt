package io.github.inputbiscuit.dotboard.core

import io.github.inputbiscuit.dotboard.core.data.BoardQuery
import io.github.inputbiscuit.dotboard.core.data.CachedBoard
import io.github.inputbiscuit.dotboard.core.data.CachingDepartureRepository
import io.github.inputbiscuit.dotboard.core.data.DepartureException
import io.github.inputbiscuit.dotboard.core.data.DepartureRepository
import io.github.inputbiscuit.dotboard.core.data.FakeDepartureRepository
import io.github.inputbiscuit.dotboard.core.data.FakeScenario
import io.github.inputbiscuit.dotboard.core.data.FileBoardStore
import io.github.inputbiscuit.dotboard.core.model.Board
import io.github.inputbiscuit.dotboard.core.model.DepartureStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RepositoryTest {

    private val tenPast10 = ZonedDateTime.of(2026, 11, 9, 22, 6, 30, 0, ZoneId.of("Europe/London"))

    private fun fake(scenario: FakeScenario) = FakeDepartureRepository(
        scenario = { scenario },
        stationName = { if (it == "PAD") "London Paddington" else null },
        now = { tenPast10 },
        latencyMillis = 0,
    )

    @Test fun `mixed sample covers every status`() = runTest {
        val board = fake(FakeScenario.MIXED).board(BoardQuery("PAD"))
        assertEquals("London Paddington", board.stationName)
        assertEquals("22:09", board.departures.first().scheduled)
        assertEquals(
            listOf("On time", "Exp 22:19", "Cancelled", "Delayed", "On time", "On time"),
            board.departures.map { it.status.boardText },
        )
        assertTrue(board.departures.first().callingPoints.size >= 10)
    }

    @Test fun `long names sample has long destinations and calling lists`() = runTest {
        val board = fake(FakeScenario.LONG_NAMES).board(BoardQuery("PAD"))
        assertTrue(board.departures.any { it.destination.length > 30 })
        assertTrue(board.departures.first().callingPoints.size > 15)
        assertEquals(DepartureStatus.Expected("22:12"), board.departures.first().status)
    }

    @Test fun `no services sample is empty`() = runTest {
        assertTrue(fake(FakeScenario.NO_SERVICES).board(BoardQuery("PAD")).departures.isEmpty())
    }

    @Test fun `error samples throw the matching exception`() = runTest {
        assertFailsWith<DepartureException.Network> { fake(FakeScenario.NO_CONNECTION).board(BoardQuery("PAD")) }
        assertFailsWith<DepartureException.InvalidApiKey> { fake(FakeScenario.INVALID_KEY).board(BoardQuery("PAD")) }
        assertFailsWith<DepartureException.MissingApiKey> { fake(FakeScenario.NO_API_KEY).board(BoardQuery("PAD")) }
    }

    @Test fun `query key normalises case and includes filter`() {
        assertEquals("PAD>RDG", BoardQuery.of("pad", "rdg").key)
        assertEquals("PAD", BoardQuery.of("PAD", "").key)
    }

    private class CountingRepo : DepartureRepository {
        val calls = AtomicInteger()
        override suspend fun board(query: BoardQuery): Board {
            calls.incrementAndGet()
            delay(100)
            return Board(query.crs, query.crs, null, null, emptyList(), emptyList())
        }
    }

    @Test fun `concurrent requests for the same board share one fetch`() = runTest {
        val counting = CountingRepo()
        var now = 0L
        val cache = CachingDepartureRepository(counting, clock = { now })

        (1..5).map { async { cache.board(BoardQuery("PAD")) } }.awaitAll()
        assertEquals(1, counting.calls.get())

        cache.board(BoardQuery("RDG"))
        assertEquals(2, counting.calls.get())
    }

    @Test fun `refetches only when older than max age`() = runTest {
        val counting = CountingRepo()
        var now = 1_000L
        val cache = CachingDepartureRepository(counting, clock = { now })

        cache.cached(BoardQuery("PAD"), maxAgeMillis = 60_000)
        now += 59_000
        cache.cached(BoardQuery("PAD"), maxAgeMillis = 60_000)
        assertEquals(1, counting.calls.get())
        now += 2_000
        cache.cached(BoardQuery("PAD"), maxAgeMillis = 60_000)
        assertEquals(2, counting.calls.get())
    }

    @Test fun `file store round-trips a board`() = runTest {
        val dir = Files.createTempDirectory("boards").toFile()
        val board = fake(FakeScenario.MIXED).board(BoardQuery("PAD"))
        FileBoardStore(dir).put("PAD>RDG", CachedBoard(board, 42))

        val restored = FileBoardStore(dir).get("PAD>RDG")
        assertEquals(board, restored?.board)
        assertEquals(42, restored?.fetchedAtMillis)
        dir.deleteRecursively()
    }
}
