package data

typealias StationId = String

interface Station {
    val id: StationId
    val name: String
    val lat: Double
    val lon: Double
}

@JsModule("src/data/stations.json")
external val stations: JsonMap<Station>
