package si.lukabencina.kilometrina.data

import org.json.JSONArray

object TripRouteCodec {
    const val MAX_STOPS = 20
    const val MAX_ADDRESS_LENGTH = 200

    fun decode(raw: String): List<String> = runCatching {
        val array = JSONArray(raw.ifBlank { "[]" })
        buildList {
            for (index in 0 until minOf(array.length(), MAX_STOPS)) {
                val value = array.optString(index).trim()
                if (value.isNotBlank()) add(value.take(MAX_ADDRESS_LENGTH))
            }
        }
    }.getOrDefault(emptyList())

    fun encode(stops: List<String>): String {
        val clean = sanitize(stops)
        return JSONArray().apply { clean.forEach { put(it) } }.toString()
    }

    fun normalize(raw: String): String = encode(decode(raw))

    fun sanitize(stops: List<String>): List<String> =
        stops.asSequence()
            .map { it.trim().replace(Regex("\\s+"), " ").take(MAX_ADDRESS_LENGTH) }
            .filter(String::isNotBlank)
            .take(MAX_STOPS)
            .toList()
}

fun TripEntity.routeStops(): List<String> = TripRouteCodec.decode(routeStopsJson)

fun TripEntity.routeAddresses(): List<String> = buildList {
    startAddress.trim().takeIf(String::isNotBlank)?.let(::add)
    addAll(routeStops())
    endAddress?.trim()?.takeIf(String::isNotBlank)?.let(::add)
}
