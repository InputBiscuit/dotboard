package io.github.inputbiscuit.dotboard.core

import io.github.inputbiscuit.dotboard.core.data.BoardQuery
import io.github.inputbiscuit.dotboard.core.data.CachedBoard
import io.github.inputbiscuit.dotboard.core.data.FakeDepartureRepository
import io.github.inputbiscuit.dotboard.core.data.FakeScenario
import io.github.inputbiscuit.dotboard.core.ldbws.LdbwsClient
import io.github.inputbiscuit.dotboard.core.ldbws.StationBoard
import io.github.inputbiscuit.dotboard.core.model.Coach
import io.github.inputbiscuit.dotboard.core.model.CoachClass
import io.github.inputbiscuit.dotboard.core.model.Toilet
import io.github.inputbiscuit.dotboard.core.model.ToiletStatus
import io.github.inputbiscuit.dotboard.core.model.ToiletType
import io.github.inputbiscuit.dotboard.core.model.toBoard
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FormationTest {

    private val board = LdbwsClient.json.decodeFromString(
        StationBoard.serializer(),
        requireNotNull(javaClass.classLoader.getResource("depboard_formation.json")).readText(),
    ).toBoard()

    @Test fun `parses service-level formation including capitalised Value`() {
        val f = requireNotNull(board.departures[0].formation)
        assertEquals(
            listOf(
                Coach("A", CoachClass.FIRST, Toilet(ToiletType.ACCESSIBLE, ToiletStatus.IN_SERVICE), 20),
                Coach("B", CoachClass.STANDARD, Toilet(ToiletType.STANDARD, ToiletStatus.NOT_IN_SERVICE), 85),
                // loadingSpecified=false means unknown, not empty.
                Coach("C", CoachClass.MIXED, Toilet(ToiletType.NONE, ToiletStatus.UNKNOWN), null),
            ),
            f.coaches,
        )
        assertEquals(52, f.averageLoading)
    }

    @Test fun `falls back to the next calling point when the train doesn't split`() {
        val f = requireNotNull(board.departures[1].formation)
        assertEquals(listOf("1", "2"), f.coaches.map { it.number })
        assertEquals(ToiletType.STANDARD, f.coaches[0].toilet.type)
        assertEquals(40, f.coaches[0].loading)
        assertEquals(ToiletType.UNKNOWN, f.coaches[1].toilet.type)
        assertNull(f.coaches[1].loading)
    }

    @Test fun `no fallback for a train that splits`() {
        assertNull(board.departures[2].formation)
    }

    @Test fun `missing or empty formation is null`() {
        assertNull(board.departures[3].formation)
        assertNull(board.departures[4].formation)
    }

    @Test fun `boards without formation data still parse`() {
        val old = LdbwsClient.json.decodeFromString(
            StationBoard.serializer(),
            requireNotNull(javaClass.classLoader.getResource("depboard_PAD.json")).readText(),
        ).toBoard()
        assertTrue(old.departures.all { it.formation == null })
    }

    @Test fun `sample data mixes services with and without formations`() = runTest {
        val repo = FakeDepartureRepository(
            scenario = { FakeScenario.MIXED },
            now = { ZonedDateTime.of(2026, 11, 9, 22, 6, 0, 0, ZoneId.of("Europe/London")) },
            latencyMillis = 0,
        )
        val deps = repo.board(BoardQuery("PAD")).departures
        assertTrue(deps.count { it.formation != null } >= 2)
        assertTrue(deps.count { it.formation == null } >= 2)
        assertTrue(deps.mapNotNull { it.formation }.flatMap { it.coaches }.any { it.loading == null })
    }

    @Test fun `cached boards from before formations existed still load`() {
        val json = """{"board":{"stationName":"London Paddington","crs":"PAD","filterName":null,"generatedAt":null,""" +
            """"departures":[{"scheduled":"22:09","status":{"type":"io.github.inputbiscuit.dotboard.core.model.DepartureStatus.OnTime"},""" +
            """"destination":"Reading","via":null,"platform":"1","operator":null,"callingPoints":[],""" +
            """"cancelReason":null,"delayReason":null,"serviceId":null}],"messages":[]},"fetchedAtMillis":1}"""
        val cached = Json { ignoreUnknownKeys = true }.decodeFromString(CachedBoard.serializer(), json)
        assertNull(cached.board.departures.single().formation)
    }
}
