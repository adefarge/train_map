package io.adefarge.project

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.LocalTime
import kotlin.streams.asSequence

data class Link(
    val duration: Long,
    val stops: List<String>
)

class StopWithLinks(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double
) {
    val links = mutableMapOf<String, Link>()

    companion object {
        fun from(line: String): StopWithLinks? {
            val (id, name, _, lat, lon) = line.split(',')
            if (lat.isEmpty() || lon.isEmpty()) return null

            return StopWithLinks(
                id = id,
                name = name.removeSurrounding("\"").removePrefix("Gare de "),
                lat = lat.toDouble(),
                lon = lon.toDouble()
            )
        }
    }
}

data class LinkStop(val tripId: String, val id: String, val arrival: LocalTime, val departure: LocalTime) :
    Comparable<LinkStop> {

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

    companion object {
        fun from(line: String): LinkStop? {
            val (tripId, arrivalTime, departureTime, stopId) = line.split(',')

            return try {
                val arrival = LocalTime.parse(arrivalTime)
                val departure = LocalTime.parse(departureTime)

                LinkStop(
                    tripId = tripId,
                    id = stopId,
                    arrival = arrival,
                    departure = departure
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

fun main() {
    val stopsWithLinks = Files.newBufferedReader(Path.of("data/gtfs/stops.txt")).use { reader ->
        reader.lines().asSequence()
            .drop(1)
            .filter { it.contains("OCETrain") }
            .mapNotNull { StopWithLinks.from(it) }
            .associateBy { it.id }
    }

    Files.newBufferedReader(Path.of("data/gtfs/stop_times.txt")).use { reader ->
        reader.lines()
            .asSequence()
            .drop(1)
            .mapNotNull { LinkStop.from(it) }
            .groupBy { it.tripId }
            .values
            .asSequence()
            .map { list -> list.sorted() }
            .forEach { stops ->
                for (i in stops.indices) {
                    for (j in i + 1 until stops.size) {
                        val stop1 = stops[i]
                        val stop2 = stops[j]
                        val duration = stop1.durationToAsMinutes(stop2)

                        stopsWithLinks[stop1.id]?.links?.compute(stop2.id) { _, previousLink ->
                            if (previousLink == null || duration < previousLink.duration) {
                                Link(duration = duration, stops = stops.subList(i + 1, j).map { it.id })
                            } else {
                                previousLink
                            }
                        }
                    }
                }
            }
    }

    Files.newBufferedWriter(Path.of("app/data.json")).use { os ->
        val mapper = jacksonObjectMapper().registerKotlinModule()
        mapper.writeValue(os, stopsWithLinks)
    }
}
