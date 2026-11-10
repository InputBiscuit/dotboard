package io.github.inputbiscuit.dotboard.core.data

import io.github.inputbiscuit.dotboard.core.model.Board
import io.github.inputbiscuit.dotboard.core.model.CallingAt
import io.github.inputbiscuit.dotboard.core.model.Coach
import io.github.inputbiscuit.dotboard.core.model.CoachClass
import io.github.inputbiscuit.dotboard.core.model.Formation
import io.github.inputbiscuit.dotboard.core.model.Toilet
import io.github.inputbiscuit.dotboard.core.model.ToiletStatus
import io.github.inputbiscuit.dotboard.core.model.ToiletType
import io.github.inputbiscuit.dotboard.core.model.Departure
import io.github.inputbiscuit.dotboard.core.model.DepartureStatus
import kotlinx.coroutines.delay
import java.io.IOException
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Sample boards for development and for anyone without an API key yet. */
enum class FakeScenario(val label: String) {
    MIXED("Mixed: on time, delayed, cancelled"),
    LONG_NAMES("Long destinations and calling points"),
    NO_SERVICES("No services"),
    NO_CONNECTION("Error: no connection"),
    INVALID_KEY("Error: API key rejected"),
    NO_API_KEY("Error: no API key"),
}

/**
 * Returns realistic boards with times relative to [now], so the board looks live.
 *
 * @param stationName resolves a CRS to a display name (e.g. from the bundled station list).
 */
class FakeDepartureRepository(
    private val scenario: suspend () -> FakeScenario,
    private val stationName: suspend (String) -> String? = { null },
    private val now: () -> ZonedDateTime = { ZonedDateTime.now(LONDON) },
    private val latencyMillis: Long = 250,
) : DepartureRepository {

    override suspend fun board(query: BoardQuery): Board {
        if (latencyMillis > 0) delay(latencyMillis)
        val base = now().withZoneSameInstant(LONDON).truncatedTo(ChronoUnit.MINUTES).toLocalTime()
        val departures = when (scenario()) {
            FakeScenario.MIXED -> mixed(base)
            FakeScenario.LONG_NAMES -> longNames(base)
            FakeScenario.NO_SERVICES -> emptyList()
            FakeScenario.NO_CONNECTION -> throw DepartureException.Network(IOException("Sample: no connection"))
            FakeScenario.INVALID_KEY -> throw DepartureException.InvalidApiKey("Sample: Invalid ApiKey")
            FakeScenario.NO_API_KEY -> throw DepartureException.MissingApiKey()
        }
        return Board(
            stationName = stationName(query.crs) ?: query.crs,
            crs = query.crs,
            filterName = query.filterCrs?.let { stationName(it) ?: it },
            generatedAt = now().toOffsetDateTime().toString(),
            departures = departures,
            messages = if (departures.isEmpty()) {
                listOf("There are no scheduled services from this station at the moment.")
            } else {
                emptyList()
            },
        )
    }

    private fun mixed(t: LocalTime) = listOf(
        departure(
            t, 3, "Oxford", platform = "12", operator = "Great Western Railway",
            // 8 coaches: first class at both ends, busier towards the front.
            formation = formation(
                coach("1", CoachClass.FIRST, loading = 35),
                coach("2", toilet = ToiletType.STANDARD, loading = 80),
                coach("3", toilet = ToiletType.ACCESSIBLE, loading = 65),
                coach("4", loading = 50),
                coach("5", CoachClass.FIRST, loading = 20),
                coach("6", toilet = ToiletType.STANDARD, loading = 40),
                coach("7", toilet = ToiletType.ACCESSIBLE, loading = 25),
                coach("8", loading = 10),
            ),
            calling = listOf(
                "Ealing Broadway" to 8, "Southall" to 14, "Hayes & Harlington" to 17, "West Drayton" to 21,
                "Slough" to 28, "Burnham" to 32, "Maidenhead" to 37, "Twyford" to 44, "Reading" to 52,
                "Tilehurst" to 57, "Pangbourne" to 61, "Goring & Streatley" to 66, "Cholsey" to 71,
                "Didcot Parkway" to 77, "Radley" to 84, "Oxford" to 92,
            ),
        ),
        departure(
            t, 8, "Reading", platform = "9", operator = "Great Western Railway", expectedDelay = 5,
            delayReason = "This train has been delayed by a points failure",
            formation = formation(
                coach("1", loading = 90),
                coach("2", toilet = ToiletType.ACCESSIBLE, loading = 95),
                coach("3", loading = 70),
                coach("4", toilet = ToiletType.STANDARD, loading = 55),
            ),
            calling = listOf("Slough" to 15, "Maidenhead" to 21, "Twyford" to 28, "Reading" to 34),
        ),
        departure(
            t, 17, "Penzance", platform = "1", operator = "Great Western Railway", cancelled = true,
            cancelReason = "This train has been cancelled because of a shortage of train crew",
            calling = listOf("Reading" to 25, "Taunton" to 108, "Exeter St Davids" to 132, "Plymouth" to 190, "Penzance" to 300),
        ),
        departure(
            t, 24, "Heathrow Terminal 5", platform = null, operator = "Heathrow Express", delayed = true,
            delayReason = "This train has been delayed by a broken down train",
            calling = listOf("Heathrow Central" to 15, "Heathrow Terminal 5" to 21),
        ),
        departure(
            t, 33, "Cardiff Central", platform = "4", operator = "Great Western Railway",
            // Formation known but no loading data.
            formation = formation(
                coach("1", CoachClass.MIXED),
                coach("2", toilet = ToiletType.STANDARD),
                coach("3", toilet = ToiletType.ACCESSIBLE),
                coach("4"),
                coach("5", CoachClass.MIXED),
            ),
            calling = listOf(
                "Reading" to 25, "Didcot Parkway" to 41, "Swindon" to 56, "Bristol Parkway" to 79,
                "Newport" to 99, "Cardiff Central" to 113,
            ),
        ),
        departure(
            t, 38, "Didcot Parkway", platform = "11", operator = "Great Western Railway",
            calling = listOf("Slough" to 18, "Maidenhead" to 25, "Reading" to 40, "Didcot Parkway" to 58),
        ),
    )

    private fun longNames(t: LocalTime) = listOf(
        departure(
            t, 2, "Manchester Airport & Liverpool Lime Street", platform = "10A",
            operator = "TransPennine Express", expectedDelay = 4,
            // Lettered coaches, one toilet out of service.
            formation = formation(
                coach("A", CoachClass.FIRST, toilet = ToiletType.ACCESSIBLE, loading = 30),
                coach("B", loading = 85),
                coach("C", toilet = ToiletType.STANDARD, toiletStatus = ToiletStatus.NOT_IN_SERVICE, loading = 75),
                coach("D", loading = 60),
                coach("E", toilet = ToiletType.STANDARD, loading = 45),
            ),
            calling = listOf(
                "Wakefield Westgate" to 12, "Dewsbury" to 21, "Huddersfield" to 29, "Stalybridge" to 44,
                "Manchester Victoria" to 58, "Manchester Piccadilly" to 66, "Manchester Oxford Road" to 69,
                "Deansgate" to 71, "Gatley" to 80, "Heald Green" to 84, "Manchester Airport" to 91,
                "Newton-le-Willows" to 97, "St Helens Junction" to 101, "Lea Green" to 104,
                "Rainhill" to 107, "Whiston" to 109, "Huyton" to 112, "Broad Green" to 115,
                "Wavertree Technology Park" to 118, "Edge Hill" to 121, "Liverpool Lime Street" to 126,
            ),
        ),
        departure(
            t, 7, "Rhoose Cardiff International Airport", platform = "3", operator = "Transport for Wales",
            via = "via Barry Island",
            calling = listOf("Cardiff Central" to 9, "Grangetown" to 13, "Cogan" to 17, "Barry Island" to 26,
                "Rhoose Cardiff International Airport" to 38),
        ),
        departure(
            t, 11, "Shoeburyness", platform = "12", operator = "c2c", cancelled = true,
            cancelReason = "This train has been cancelled because of a fault on this train",
            calling = listOf("Southend Central" to 48, "Shoeburyness" to 58),
        ),
        departure(
            t, 19, "London St Pancras International", platform = "4", operator = "East Midlands Railway",
            delayed = true, calling = listOf("Luton Airport Parkway" to 30, "London St Pancras International" to 55),
        ),
    )

    private fun departure(
        base: LocalTime,
        inMinutes: Long,
        destination: String,
        platform: String?,
        operator: String,
        calling: List<Pair<String, Long>>,
        via: String? = null,
        expectedDelay: Long = 0,
        delayed: Boolean = false,
        cancelled: Boolean = false,
        cancelReason: String? = null,
        delayReason: String? = null,
        formation: Formation? = null,
    ): Departure {
        val std = base.plusMinutes(inMinutes)
        val status = when {
            cancelled -> DepartureStatus.Cancelled
            delayed -> DepartureStatus.Delayed
            expectedDelay > 0 -> DepartureStatus.Expected(HHMM.format(std.plusMinutes(expectedDelay)))
            else -> DepartureStatus.OnTime
        }
        return Departure(
            scheduled = HHMM.format(std),
            status = status,
            destination = destination,
            via = via,
            platform = platform,
            operator = operator,
            callingPoints = calling.map { (name, after) ->
                val st = std.plusMinutes(after)
                CallingAt(
                    name = name,
                    crs = null,
                    scheduled = HHMM.format(st),
                    status = when (status) {
                        is DepartureStatus.Expected -> DepartureStatus.Expected(HHMM.format(st.plusMinutes(expectedDelay)))
                        else -> status
                    },
                )
            },
            cancelReason = cancelReason,
            delayReason = delayReason,
            serviceId = "SAMPLE${std.hour}${std.minute}",
            formation = formation,
        )
    }

    private fun formation(vararg coaches: Coach) = Formation(coaches.toList())

    private fun coach(
        number: String,
        coachClass: CoachClass = CoachClass.STANDARD,
        toilet: ToiletType = ToiletType.NONE,
        toiletStatus: ToiletStatus = if (toilet == ToiletType.NONE) ToiletStatus.UNKNOWN else ToiletStatus.IN_SERVICE,
        loading: Int? = null,
    ) = Coach(number, coachClass, Toilet(toilet, toiletStatus), loading)

    private companion object {
        val LONDON: ZoneId = ZoneId.of("Europe/London")
        val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
