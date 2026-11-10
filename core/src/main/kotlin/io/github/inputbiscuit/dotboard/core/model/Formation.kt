package io.github.inputbiscuit.dotboard.core.model

import io.github.inputbiscuit.dotboard.core.ldbws.CoachData
import io.github.inputbiscuit.dotboard.core.ldbws.FormationData
import kotlinx.serialization.Serializable

/**
 * A train's coaches at the board's station, in running order, where the operator provides it.
 * Bike and wheelchair spaces aren't in the data.
 */
@Serializable
data class Formation(val coaches: List<Coach>) {
    /** Average loading across coaches that report it, 0-100, or null if none do. */
    val averageLoading: Int? get() = coaches.mapNotNull { it.loading }.takeIf { it.isNotEmpty() }?.average()?.toInt()
}

@Serializable
data class Coach(
    /** As shown on the train, e.g. "A" or "12". */
    val number: String,
    val coachClass: CoachClass,
    val toilet: Toilet,
    /** How busy the coach is, 0 (empty) to 100 (full), or null if unknown. */
    val loading: Int? = null,
)

@Serializable
enum class CoachClass { FIRST, STANDARD, MIXED, UNKNOWN }

@Serializable
data class Toilet(val type: ToiletType, val status: ToiletStatus = ToiletStatus.UNKNOWN)

@Serializable
enum class ToiletType { NONE, STANDARD, ACCESSIBLE, UNKNOWN }

@Serializable
enum class ToiletStatus { IN_SERVICE, NOT_IN_SERVICE, UNKNOWN }

/** Null when there are no coaches, so "no formation" is always just null. */
internal fun FormationData.toFormation(): Formation? =
    coaches.mapIndexed { i, c -> c.toCoach(i) }.takeIf { it.isNotEmpty() }?.let(::Formation)

private fun CoachData.toCoach(index: Int) = Coach(
    number = number?.trim()?.takeIf { it.isNotEmpty() } ?: (index + 1).toString(),
    coachClass = when (coachClass?.trim()?.lowercase()) {
        "first" -> CoachClass.FIRST
        "standard" -> CoachClass.STANDARD
        "mixed" -> CoachClass.MIXED
        else -> CoachClass.UNKNOWN
    },
    toilet = Toilet(
        type = when (toilet?.value?.trim()?.lowercase()) {
            "none" -> ToiletType.NONE
            "standard" -> ToiletType.STANDARD
            "accessible" -> ToiletType.ACCESSIBLE
            else -> ToiletType.UNKNOWN
        },
        status = when (toilet?.status?.trim()?.lowercase()) {
            "inservice" -> ToiletStatus.IN_SERVICE
            "notinservice" -> ToiletStatus.NOT_IN_SERVICE
            else -> ToiletStatus.UNKNOWN
        },
    ),
    loading = loading?.takeIf { loadingSpecified }?.coerceIn(0, 100),
)
