#!/usr/bin/env kscript

@file:CompilerOpts("-jvm-target 1.8")

@file:DependsOn("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.4.0")
@file:DependsOn("com.fasterxml.jackson.core:jackson-databind:2.9.4")
@file:DependsOn("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.4")

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalTime
import kotlin.streams.asSequence

data class Link(val duration: LinkDuration, val stops: List<StationId>, val type: String)
typealias LinkDuration = Long
typealias StationId = String

val idRegex = """.*[^\d](\d+)""".toRegex()
fun parseId(id: String): String = idRegex.matchEntire(id)?.groupValues?.getOrNull(1) ?: error("id pattern error: $id")

val types = listOf("OCETrain", "OCETGV", "OCEOUIGO")
fun Station.Companion.from(line: String): Station? {
    val (id, name, _, lat, lon) = line.split(',')
    if (lat.isEmpty() || lon.isEmpty()) return null
    if (types.none { it in id }) return null

    return Station(
            id = parseId(id),
            name = name.removeSurrounding("\"").removePrefix("Gare de "),
            lat = lat.toDouble(),
            lon = lon.toDouble()
    )
}

fun LinkStop.Companion.from(line: String, validStops: Set<StationId>): LinkStop? {
    val (tripId, arrivalTime, departureTime, stopId) = line.split(',')
    val id = parseId(stopId)
    if (id !in validStops) return null

    return try {
        val arrival = LocalTime.parse(arrivalTime)
        val departure = LocalTime.parse(departureTime)

        LinkStop(
                tripId = tripId,
                id = id,
                arrival = arrival,
                departure = departure
        )
    } catch (e: Exception) {
        null
    }
}

data class Station(
    val id: StationId,
    val name: String,
    val lat: Double,
    val lon: Double
) {
    companion object
}

data class LinkStop(
    val tripId: String,
    val id: StationId,
    val arrival: LocalTime,
    val departure: LocalTime
) : Comparable<LinkStop> {

    override fun compareTo(other: LinkStop): Int {
        return when {
            departure > LocalTime.of(20, 0) && other.departure < LocalTime.of(4, 0) -> -1
            other.departure > LocalTime.of(20, 0) && departure < LocalTime.of(4, 0) -> 1
            else -> Duration.between(other.departure, departure).toMinutes().toInt()
        }
    }

    fun durationToAsMinutes(other: LinkStop): Long {
        return if (departure > LocalTime.of(20, 0) && other.departure < LocalTime.of(4, 0)) {
            (Duration.ofHours(24) - Duration.between(other.arrival, departure)).toMinutes()
        } else {
            Duration.between(departure, other.arrival).toMinutes()
        }
    }

    companion object
}

data class Export(val stopsFile: Path, val stopTimesFile: Path, val type: String)
data class ExportResult(val stations: Map<StationId, Station>, val linkMatrix: Map<StationId, Map<StationId, Link>>)

fun <K, V> MutableMap<K, MutableMap<K, V>>.computeDouble(from: K, to: K, compute: (V?) -> V) {
    val map = computeIfAbsent(from) { mutableMapOf() }
    map.compute(to) { _, previousLink -> compute(previousLink) }
}

fun Export.export(): ExportResult {
    val stations = Files.newBufferedReader(stopsFile).use { reader ->
        reader.lines().asSequence()
            .drop(1)
            .mapNotNull { Station.from(it) }
            .associateBy { it.id }
    }
    val validStops = stations.keys

    val linkMatrix = mutableMapOf<StationId, MutableMap<StationId, Link>>()
    Files.newBufferedReader(stopTimesFile).use { reader ->
        reader.lines()
            .asSequence()
            .drop(1)
            .mapNotNull { LinkStop.from(it, validStops) }
            .groupBy { it.tripId }
            .values
            .asSequence()
            .map { list -> list.sorted() }
            .forEach { stops ->
                for (i in stops.indices) {
                    for (j in i + 1 until stops.size) {
                        val stop1 = stops[i]
                        val stop2 = stops[j]
                        val linkDuration = stop1.durationToAsMinutes(stop2)

                        linkMatrix.computeDouble(stop1.id, stop2.id) { currentLink ->
                            when {
                                currentLink == null || linkDuration < currentLink.duration ->
                                    Link(
                                        duration = linkDuration,
                                        stops = stops.subList(i + 1, j).map { it.id },
                                        type = type
                                    )
                                else -> currentLink
                            }
                        }
                    }
                }
            }
    }

    return ExportResult(stations, linkMatrix)
}

fun mergeStations(ter: Map<StationId, Station>, tgv: Map<StationId, Station>): Map<StationId, Station> {
    val keys = ter.keys + tgv.keys
    return keys.associateWith { stationId ->
        ter[stationId] ?: tgv.getValue(stationId)
    }
}

fun mergeLinkMatrices(
    ter: Map<StationId, Map<StationId, Link>>,
    tgv: Map<StationId, Map<StationId, Link>>
): Map<StationId, Map<StationId, Link>> {
    val result = mutableMapOf<StationId, MutableMap<StationId, Link>>()
    for ((from, map) in tgv) {
        for ((to, tgvLink) in map) {
            result.computeDouble(from, to) { tgvLink }
        }
    }

    for ((from, map) in ter) {
        for ((to, terLink) in map) {
            result.computeDouble(from, to) { tgvLink -> tgvLink ?: terLink }
        }
    }
    return result
}

fun writeToFile(data: Any, path: Path) {
    Files.newBufferedWriter(path).use { os ->
        val mapper = jacksonObjectMapper().registerKotlinModule()
        mapper.writeValue(os, data)
    }
}

val terExport = Export(
    stopsFile = Paths.get("data/gtfs/ter/stops.txt"),
    stopTimesFile = Paths.get("data/gtfs/ter/stop_times.txt"),
    type = "TER"
).export()

val tgvExport = Export(
    stopsFile = Paths.get("data/gtfs/tgv/stops.txt"),
    stopTimesFile = Paths.get("data/gtfs/tgv/stop_times.txt"),
    type = "TGV"
).export()

val stations = mergeStations(terExport.stations, tgvExport.stations)
val linkMatrix = mergeLinkMatrices(terExport.linkMatrix, tgvExport.linkMatrix)

writeToFile(stations, Paths.get("data/gtfs/stations.json"))
writeToFile(linkMatrix, Paths.get("data/gtfs//links.json"))
