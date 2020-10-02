package data

import kotlinext.js.Object

interface JsonMap<T : Any> {
    data class Entry<T : Any>(val key: String, val value: T)
}

operator fun <T : Any> JsonMap<T>.get(key: String): T = this.asDynamic()[key] as T

fun JsonMap<*>.keys(): Array<String> {
    return Object.keys(this)
}


fun <T : Any> JsonMap<T>.entries(): Iterable<JsonMap.Entry<T>> {
    return keys().map { key -> JsonMap.Entry(key, this[key]) }
}
