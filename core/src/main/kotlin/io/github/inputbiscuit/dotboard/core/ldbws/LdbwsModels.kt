package io.github.inputbiscuit.dotboard.core.ldbws

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/*
 * Wire models for the LDBWS JSON API (Rail Data Marketplace, version 20220120).
 * Every field is optional/defaulted: the API omits fields freely and we decode with ignoreUnknownKeys.
 */

@Serializable
data class StationBoard(
    val generatedAt: String? = null,
    val locationName: String? = null,
    val crs: String? = null,
    val filterLocationName: String? = null,
    val filtercrs: String? = null,
    val filterType: String? = null,
    val nrccMessages: List<NrccMessage> = emptyList(),
    val platformAvailable: Boolean = false,
    val areServicesAvailable: Boolean = true,
    val trainServices: List<ServiceItem> = emptyList(),
    val busServices: List<ServiceItem> = emptyList(),
    val ferryServices: List<ServiceItem> = emptyList(),
)

@Serializable
data class NrccMessage(
    @SerialName("Value") val value: String? = null,
)

@Serializable
data class ServiceItem(
    val std: String? = null,
    val etd: String? = null,
    val sta: String? = null,
    val eta: String? = null,
    val platform: String? = null,
    val operator: String? = null,
    val operatorCode: String? = null,
    val serviceType: String? = null,
    val serviceID: String? = null,
    val isCancelled: Boolean = false,
    val isCircularRoute: Boolean = false,
    val filterLocationCancelled: Boolean = false,
    val cancelReason: String? = null,
    val delayReason: String? = null,
    val length: Int = 0,
    val origin: List<ServiceLocation> = emptyList(),
    val destination: List<ServiceLocation> = emptyList(),
    val subsequentCallingPoints: List<CallingPointList> = emptyList(),
    val previousCallingPoints: List<CallingPointList> = emptyList(),
    /** Documented on the non-"WithDetails" boards' service items; read here in case the REST API includes it. */
    val formation: FormationData? = null,
)

/** The train's coaches at a location, where the operator provides it (many services have none). */
@Serializable
data class FormationData(
    val loadingCategory: LoadingCategory? = null,
    val coaches: List<CoachData> = emptyList(),
)

@Serializable
data class LoadingCategory(val code: String? = null, val colour: String? = null)

@Serializable
data class CoachData(
    /** "A" or "12" style identifier, up to two characters. */
    val number: String? = null,
    /** "First", "Mixed" or "Standard" (others may be added). */
    val coachClass: String? = null,
    val toilet: ToiletAvailability? = null,
    /** 0-100 when [loadingSpecified]. */
    val loading: Int? = null,
    val loadingSpecified: Boolean = false,
)

/**
 * XML `<toilet status="InService">Accessible</toilet>`. The JSON conversion may name the text "Value" (as it
 * does for NRCC messages) or "value", so accept both.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ToiletAvailability(
    /** "Unknown", "InService" or "NotInService". */
    val status: String? = null,
    /** "Unknown", "None", "Standard" or "Accessible". */
    @JsonNames("Value") val value: String? = null,
)

@Serializable
data class ServiceLocation(
    val locationName: String? = null,
    val crs: String? = null,
    val via: String? = null,
    val futureChangeTo: String? = null,
    val assocIsCancelled: Boolean = false,
)

@Serializable
data class CallingPointList(
    val callingPoint: List<CallingPoint> = emptyList(),
    val serviceType: String? = null,
    val serviceChangeRequired: Boolean = false,
    val assocIsCancelled: Boolean = false,
)

@Serializable
data class CallingPoint(
    val locationName: String? = null,
    val crs: String? = null,
    val st: String? = null,
    val et: String? = null,
    val at: String? = null,
    val isCancelled: Boolean = false,
    val length: Int = 0,
    /** The train's formation at this calling point (documented on the "WithDetails" boards). */
    val formation: FormationData? = null,
)

/** Error body returned by the API gateway, e.g. for a missing or invalid key. */
@Serializable
internal data class FaultResponse(val fault: Fault? = null) {
    @Serializable
    data class Fault(val faultstring: String? = null, val detail: Detail? = null)

    @Serializable
    data class Detail(val errorcode: String? = null)
}
