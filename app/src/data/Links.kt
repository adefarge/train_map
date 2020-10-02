package data

interface Link : JsonMap<LinkDestination>

interface LinkDestination {
    val duration: Int
    val stops: Array<StationId>
    val type: String
}

@JsModule("src/data/links.json")
external val linkMatrix: JsonMap<Link>

data class Train(
    val type: String,
    val duration: Int,
    val stops: Array<StationId>
) {
    override fun toString(): String =
        "Train(type=$type, duration=$duration, stops=[${stops.joinToString(", ")}])"
}

data class ComputedLink(
    val destination: StationId,
    val trains: Array<Train>
) {
    val hops: Int get() = trains.size - 1
    val totalDuration: Int get() = trains.sumOf { it.duration }
    override fun toString(): String =
        "ComputedLink(destination=$destination, totalDuration=$totalDuration, trains=[${trains.joinToString(", ")}])"
}

typealias Hops = List<StationId>

private fun buildComputedLink(hopLocations: List<StationId>): ComputedLink {
    val trains = mutableListOf<Train>()

    for (i in hopLocations.dropLast(1).indices) {
        val start = hopLocations[i]
        val end = hopLocations[i + 1]
        val link = linkMatrix[start][end]
        trains += Train(
            type = link.type,
            stops = link.stops.copyOf(),
            duration = link.duration
        )
    }

    return ComputedLink(
        destination = hopLocations.last(),
        trains = trains.toTypedArray()
    )
}

fun Station.getLinks(maxHops: Int = 2): List<ComputedLink> {
    val start = this.id
    val explored = mutableSetOf<StationId>()
    val queue = ArrayDeque<Hops>()
    val result = mutableListOf<ComputedLink>()

    queue.addLast(listOf(start))

    while (queue.isNotEmpty()) {
        val hops = queue.removeFirst()
        val stationId = hops.last()
        val successors = linkMatrix[stationId].keys()
        for (successor in successors) {
            if (successor in explored) continue
            val newHops = hops + successor
            result += buildComputedLink(newHops)
            explored += successor
            if (newHops.size - 2 < maxHops) queue.addLast(newHops)
        }
    }

    return result
}
