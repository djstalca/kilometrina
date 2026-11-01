package si.lukabencina.kilometrina.data

/** Formats newly resolved addresses only; never rewrites historical trip text. */
fun preciseLocationLabel(
    street: String?,
    houseNumber: String?,
    city: String?,
    district: String? = null,
    addressLine: String? = null,
    countryCode: String? = null,
    countryName: String? = null,
): String? {
    fun String?.clean() = this?.trim()?.takeIf { it.isNotEmpty() }
    val road = street.clean()
    val number = houseNumber.clean()
    val town = city.clean() ?: district.clean()
    val streetAddress = road?.let {
        if (number != null && !it.equals(number, true) && !it.endsWith(" $number", true)) "$it $number" else it
    }
    // Providers occasionally supply a full line but omit the separate street fields.
    if (streetAddress == null) return addressLine.clean()?.takeUnless {
        it.equals(countryName.clean(), ignoreCase = true)
    } ?: town
    val parts = listOfNotNull(streetAddress, town,
        countryName.clean()?.takeIf { !countryCode.isNullOrBlank() && !countryCode.equals("SI", true) })
    return parts.distinct().joinToString(", ")
}
