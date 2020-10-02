package app

import data.get
import data.getLinks
import data.stations
import map.mapboxGl
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState


class App : RComponent<RProps, RState>() {
    override fun RBuilder.render() {
        val start = "87317362"
        val links = stations[start].getLinks(1)
        for (link in links) {
            println("Link ${stations[link.destination].name}: $link")
        }
        mapboxGl("Map")
    }
}

fun RBuilder.app() = child(App::class) {}
