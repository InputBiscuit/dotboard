package io.github.inputbiscuit.dotboard.core

import io.github.inputbiscuit.dotboard.core.ldbws.LdbwsClient
import io.github.inputbiscuit.dotboard.core.ldbws.StationBoard
import io.github.inputbiscuit.dotboard.core.model.DepartureStatus
import io.github.inputbiscuit.dotboard.core.model.toBoard
import io.github.inputbiscuit.dotboard.core.model.withoutDeparted
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class DepartureTest {

    private val board = LdbwsClient.json.decodeFromString(
        StationBoard.serializer(),
        requireNotNull(javaClass.classLoader.getResource("depboard_PAD.json")).readText(),
    ).toBoard()

    @Test fun `status text matches real boards`() {
        assertEquals(
            listOf("On time", "Exp 22:19", "Cancelled", "Delayed"),
            board.departures.map { it.status.boardText },
        )
    }

    @Test fun `expected time equal to scheduled is on time`() {
        assertEquals(DepartureStatus.OnTime, DepartureStatus.from("22:09", "22:09", cancelled = false))
    }

    @Test fun `cancelled flag wins over expected text`() {
        assertEquals(DepartureStatus.Cancelled, DepartureStatus.from("22:09", "On time", cancelled = true))
    }

    @Test fun `maps destination, via, platform and calling points`() {
        val first = board.departures[0]
        assertEquals("Reading", first.destination)
        assertEquals("2", first.platform)
        assertEquals(listOf("Ealing Broadway", "Slough", "Maidenhead", "Reading"), first.callingPoints.map { it.name })

        val second = board.departures[1]
        assertEquals("via Reading", second.via)
        assertEquals(DepartureStatus.Expected("23:23"), second.callingPoints.single().status)
    }

    @Test fun `drops departed trains using expected time`() {
        // Fixture: 22:09 on time, 22:14 exp 22:19, 22:39 cancelled, 22:54 delayed.
        assertEquals(4, board.withoutDeparted(LocalTime.of(22, 5)).departures.size)
        assertEquals(listOf("22:14", "22:39", "22:54"), board.withoutDeparted(LocalTime.of(22, 11)).departures.map { it.scheduled })
        // 22:14 is expected at 22:19, so it's still there at 22:17.
        assertEquals("22:14", board.withoutDeparted(LocalTime.of(22, 17)).departures.first().scheduled)
        // Within the 1-minute grace it stays.
        assertEquals("22:09", board.withoutDeparted(LocalTime.of(22, 10)).departures.first().scheduled)
    }

    @Test fun `departed filter wraps at midnight`() {
        // Just after midnight the 22:xx trains count as earlier today (gone), not 22 hours ahead.
        assertEquals(0, board.withoutDeparted(LocalTime.of(0, 3)).departures.size)
        // Just before midnight, nothing in the fixture is in the future.
        assertEquals(0, board.withoutDeparted(LocalTime.of(23, 59)).departures.size)
    }

    @Test fun `decodes entities seen in live messages`() {
        val raw = "Delayed by up to 40 minutes&amp;nbsp;or revised. Fares &#163;5 &#x2013; see <a href=\"x\">here</a>."
        val board = LdbwsClient.json.decodeFromString(
            StationBoard.serializer(),
            """{"nrccMessages":[{"Value":${kotlinx.serialization.json.JsonPrimitive(raw)}}]}""",
        ).toBoard()
        assertEquals(listOf("Delayed by up to 40 minutes or revised. Fares £5 – see here."), board.messages)
    }

    @Test fun `strips html from messages`() {
        assertEquals(listOf("Disruption between Reading & Didcot Parkway."), board.messages)
    }
}
