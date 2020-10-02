package map

import react.RBuilder

private val Map = ReactMapboxGl(object : ReactMapboxGlInit {
    override val accessToken: String = ""
})

fun RBuilder.mapboxGl(
    className: String? = null
) = Map {
    attrs {
        style =
            "https://tile.jawg.io/jawg-streets.json?access-token=10yBrWiMPNXLr9I8WJ04PuTgncs0OsbBgIMCqGHBw5Lzeqh7QnjoWw3nXO5yHyiH"
        if (className != null) this.className = className
    }
}
