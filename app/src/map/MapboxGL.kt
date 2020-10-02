@file:JsModule("react-mapbox-gl")
@file:JsNonModule

package map

import react.RClass
import react.RProps

external interface ReactMapboxGlInit {
    val accessToken: String
}

external interface MapboxGLProps : RProps {
    var style: String
    var className: String?
}

@JsName("default")
@Suppress("FunctionName")
external fun ReactMapboxGl(init: ReactMapboxGlInit): RClass<MapboxGLProps>
