package io.github.inputbiscuit.dotboard.core.model

import io.github.inputbiscuit.dotboard.core.ldbws.CallingPoint
import io.github.inputbiscuit.dotboard.core.ldbws.ServiceItem
import io.github.inputbiscuit.dotboard.core.ldbws.StationBoard
import kotlinx.serialization.Serializable
import java.time.LocalTime

/** Expected-time status, in the same vocabulary as a real UK departure board. */
@Serializable
sealed interface DepartureStatus {
    @Serializable data object OnTime : DepartureStatus
    @Serializable data object Delayed : DepartureStatus
    @Serializable data object Cancelled : DepartureStatus
    @Serializable data class Expected(val time: String) : DepartureStatus
    @Serializable data object Unknown : DepartureStatus

    /** Text for the right-hand column of the board: "On time", "Exp 22:14", "Delayed", "Cancelled". */
    val boardText: String get() = boardText(use24h = true)

    fun boardText(use24h: Boolean): String = when (this) {
        OnTime -> "On time"
        Delayed -> "Delayed"
        Cancelled -> "Cancelled"
        is Expected -> "Exp ${formatBoardTime(time, use24h)}"
        Unknown -> ""
    }

    companion object {
        private val TIME = Regex("""^\d{2}:\d{2}$""")

        fun from(scheduled: String?, expected: String?, cancelled: Boolean): DepartureStatus {
            if (cancelled) return Cancelled
            val e = expected?.trim().orEmpty()
            return when {
                e.equals("On time", ignoreCase = true) -> OnTime
                e.equals("Cancelled", ignoreCase = true) -> Cancelled
                e.equals("Delayed", ignoreCase = true) -> Delayed
                TIME.matches(e) -> if (e == scheduled) OnTime else Expected(e)
                else -> Unknown
            }
        }
    }
}

@Serializable
data class CallingAt(
    val name: String,
    val crs: String?,
    val scheduled: String?,
    val status: DepartureStatus,
)

@Serializable
data class Departure(
    val scheduled: String,
    val status: DepartureStatus,
    val destination: String,
    val via: String?,
    val platform: String?,
    val operator: String?,
    val callingPoints: List<CallingAt>,
    val cancelReason: String?,
    val delayReason: String?,
    val serviceId: String?,
    /** Coaches at this station, where known; null for the many services without formation data. */
    val formation: Formation? = null,
)

@Serializable
data class Board(
    val stationName: String,
    val crs: String,
    val filterName: String?,
    val generatedAt: String?,
    val departures: List<Departure>,
    val messages: List<String>,
)

fun StationBoard.toBoard(): Board = Board(
    stationName = locationName.orEmpty(),
    crs = crs.orEmpty(),
    filterName = filterLocationName,
    generatedAt = generatedAt,
    departures = trainServices.map { it.toDeparture() },
    messages = nrccMessages.mapNotNull { it.value?.let(::stripHtml)?.takeIf(String::isNotBlank) },
)

/**
 * Drops services that have already left by [now] (using the expected time when there is one), so a cached
 * board shown a few minutes later doesn't list departed trains. Times wrap at midnight: anything more than
 * 12 hours "ahead" is treated as earlier today.
 */
fun Board.withoutDeparted(now: LocalTime, graceMinutes: Int = 1): Board {
    val nowMin = now.hour * 60 + now.minute
    fun minutes(hhmm: String) = hhmm.takeIf { it.length == 5 && it[2] == ':' }
        ?.let { (it.substring(0, 2).toIntOrNull() ?: return null) * 60 + (it.substring(3).toIntOrNull() ?: return null) }

    val upcoming = departures.filter { d ->
        val time = (d.status as? DepartureStatus.Expected)?.time ?: d.scheduled
        val depMin = minutes(time) ?: return@filter true
        val ahead = Math.floorMod(depMin - nowMin, 24 * 60)
        val behind = 24 * 60 - ahead
        ahead <= 12 * 60 || behind <= graceMinutes
    }
    return if (upcoming.size == departures.size) this else copy(departures = upcoming)
}

fun ServiceItem.toDeparture(): Departure = Departure(
    scheduled = std.orEmpty(),
    status = DepartureStatus.from(std, etd, isCancelled),
    destination = destination.mapNotNull { it.locationName }.joinToString(" & "),
    via = destination.firstNotNullOfOrNull { it.via },
    platform = platform,
    operator = operator,
    // Only the main portion's calling points; split/joined portions come as extra lists.
    callingPoints = subsequentCallingPoints.firstOrNull()?.callingPoint.orEmpty().map { it.toCallingAt() },
    cancelReason = cancelReason,
    delayReason = delayReason,
    serviceId = serviceID,
    formation = boardFormation(),
)

/**
 * The formation at the board's station. The "WithDetails" boards document formation only per calling point,
 * so if the service item has none, use the next stop's, but only when the train doesn't split or join
 * (a single calling-point list), as otherwise the next stop may see a different set of coaches.
 */
private fun ServiceItem.boardFormation(): Formation? =
    formation?.toFormation()
        ?: subsequentCallingPoints.singleOrNull()?.callingPoint?.firstOrNull()?.formation?.toFormation()

private fun CallingPoint.toCallingAt() = CallingAt(
    name = locationName.orEmpty(),
    crs = crs,
    scheduled = st,
    status = DepartureStatus.from(st, et, isCancelled),
)

/**
 * Formats an API "HH:mm" time for the board. 12-hour mode drops the am/pm suffix, as there's no room for it
 * and departures are always within the next couple of hours. Anything that isn't "HH:mm" is returned as-is.
 */
fun formatBoardTime(hhmm: String, use24h: Boolean): String {
    if (use24h || hhmm.length != 5 || hhmm[2] != ':') return hhmm
    val h = hhmm.substring(0, 2).toIntOrNull() ?: return hhmm
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$h12${hhmm.substring(2)}"
}

private val TAG = Regex("<[^>]*>")
private val SPACE = Regex("\\s+")

private val NUMERIC_ENTITY = Regex("&#(x?)([0-9A-Fa-f]+);")
private val NAMED_ENTITIES = mapOf(
    "&nbsp;" to " ", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&apos;" to "'",
    "&ndash;" to "–", "&mdash;" to "—", "&pound;" to "£",
)

/**
 * NRCC messages are HTML, sometimes double-escaped (live data has "&amp;nbsp;"), so decode "&amp;" first,
 * then named and numeric entities.
 */
internal fun stripHtml(s: String): String {
    var text = s.replace(TAG, "").replace("&amp;", "&")
    NAMED_ENTITIES.forEach { (entity, value) -> text = text.replace(entity, value) }
    text = NUMERIC_ENTITY.replace(text) { m ->
        val code = m.groupValues[2].toIntOrNull(if (m.groupValues[1].isEmpty()) 10 else 16)
        code?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) } ?: m.value
    }
    return text.replace(SPACE, " ").trim()
}
