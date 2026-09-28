package si.lukabencina.kilometrina.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

private const val BACKUP_FORMAT = "kilometrina-backup"
private const val BACKUP_SCHEMA_VERSION = 3
private const val MIN_SUPPORTED_SCHEMA_VERSION = 1
private const val MAX_TRIPS = 50_000
private const val MAX_POINTS = 750_000
private const val MAX_PLACES = 2_000
private const val MAX_VEHICLES = 100
private const val MAX_ATTACHMENTS = 5_000
private const val MAX_ATTACHMENT_BYTES = 12 * 1024 * 1024

data class BackupData(
    val generatedAt: Long,
    val settings: AppSettings,
    val savedPlaces: List<SavedPlace>,
    val vehicles: VehicleState = VehicleState(),
    val trips: List<TripEntity>,
    val points: List<LocationPointEntity>,
    val attachments: List<TripAttachmentEntity> = emptyList(),
)

object BackupCodec {
    fun encode(data: BackupData): String {
        val root = JSONObject()
            .put("format", BACKUP_FORMAT)
            .put("schemaVersion", BACKUP_SCHEMA_VERSION)
            .put("generatedAt", data.generatedAt)
            .put("settings", settingsToJson(data.settings))
            .put("savedPlaces", JSONArray().apply { data.savedPlaces.forEach { put(placeToJson(it)) } })
            .put("vehicles", vehicleStateToJson(data.vehicles))
            .put("trips", JSONArray().apply { data.trips.forEach { put(tripToJson(it)) } })
            .put("points", JSONArray().apply { data.points.forEach { put(pointToJson(it)) } })
            .put("attachments", JSONArray().apply { data.attachments.forEach { put(attachmentToJson(it)) } })
        return root.toString(2)
    }

    fun decode(raw: String): BackupData {
        require(raw.length <= 64 * 1024 * 1024) { "Varnostna kopija je prevelika." }
        val root = JSONObject(raw)
        require(root.optString("format") == BACKUP_FORMAT) { "Datoteka ni varnostna kopija aplikacije Kilometrina." }
        val schema = root.optInt("schemaVersion", -1)
        require(schema in MIN_SUPPORTED_SCHEMA_VERSION..BACKUP_SCHEMA_VERSION) { "Ta verzija varnostne kopije ni podprta." }

        val tripsArray = root.getJSONArray("trips")
        val pointsArray = root.getJSONArray("points")
        val placesArray = root.getJSONArray("savedPlaces")
        val attachmentsArray = if (schema >= 3) root.optJSONArray("attachments") ?: JSONArray() else JSONArray()
        require(tripsArray.length() <= MAX_TRIPS) { "Varnostna kopija vsebuje preveč voženj." }
        require(pointsArray.length() <= MAX_POINTS) { "Varnostna kopija vsebuje preveč GPS točk." }
        require(placesArray.length() <= MAX_PLACES) { "Varnostna kopija vsebuje preveč priljubljenih lokacij." }
        require(attachmentsArray.length() <= MAX_ATTACHMENTS) { "Varnostna kopija vsebuje preveč prilog." }

        val settings = settingsFromJson(root.getJSONObject("settings"))
        val vehicles = if (schema >= 2 && root.has("vehicles")) {
            vehicleStateFromJson(root.getJSONObject("vehicles"))
        } else {
            legacyVehicleState(settings)
        }
        require(vehicles.vehicles.size <= MAX_VEHICLES) { "Varnostna kopija vsebuje preveč vozil." }

        val fallbackVehicle = vehicles.defaultVehicle
        val trips = List(tripsArray.length()) { tripFromJson(tripsArray.getJSONObject(it)) }
            .map { trip ->
                if (trip.vehicleName.isBlank() && fallbackVehicle != null) {
                    trip.copy(
                        vehicleId = fallbackVehicle.id,
                        vehicleName = fallbackVehicle.name,
                        registrationPlate = fallbackVehicle.registrationPlate,
                    )
                } else trip
            }
        val points = List(pointsArray.length()) { pointFromJson(pointsArray.getJSONObject(it)) }
        val places = List(placesArray.length()) { placeFromJson(placesArray.getJSONObject(it)) }
        val attachments = List(attachmentsArray.length()) { attachmentFromJson(attachmentsArray.getJSONObject(it)) }
        validate(settings, trips, points, places, vehicles, attachments)

        return BackupData(
            generatedAt = root.optLong("generatedAt", 0L),
            settings = settings,
            savedPlaces = places,
            vehicles = vehicles,
            trips = trips,
            points = points,
            attachments = attachments,
        )
    }

    private fun validate(
        settings: AppSettings,
        trips: List<TripEntity>,
        points: List<LocationPointEntity>,
        places: List<SavedPlace>,
        vehicles: VehicleState,
        attachments: List<TripAttachmentEntity>,
    ) {
        require(settings.ratePerKm.isFinite() && settings.ratePerKm in 0.0..10.0) { "Neveljavna postavka v backupu." }
        val vehicleIds = vehicles.vehicles.map { it.id }
        require(vehicleIds.all { it.isNotBlank() } && vehicleIds.toSet().size == vehicleIds.size) { "Neveljavni ID-ji vozil." }
        vehicles.vehicles.forEach { vehicle ->
            require(vehicle.name.isNotBlank() && vehicle.name.length <= 80) { "Neveljavno ime vozila." }
            require(vehicle.registrationPlate.isNotBlank() && vehicle.registrationPlate.length <= 24) { "Neveljavna registracija vozila." }
        }
        require(vehicles.defaultVehicleId.isBlank() || vehicles.defaultVehicleId in vehicleIds) { "Privzeto vozilo v backupu ne obstaja." }

        val tripIds = trips.map { it.id }
        require(tripIds.all { it > 0L } && tripIds.toSet().size == tripIds.size) { "Neveljavni ID-ji voženj." }
        trips.forEach { trip ->
            require(trip.endTime != null && trip.endTime >= trip.startTime) { "Backup vsebuje nedokončano ali časovno neveljavno vožnjo." }
            require(validLat(trip.startLat) && validLon(trip.startLon)) { "Neveljavna začetna GPS lokacija." }
            require(trip.endLat == null || validLat(trip.endLat)) { "Neveljavna končna GPS lokacija." }
            require(trip.endLon == null || validLon(trip.endLon)) { "Neveljavna končna GPS lokacija." }
            require(trip.distanceMeters.isFinite() && trip.distanceMeters >= 0.0) { "Neveljavna razdalja v backupu." }
            require(trip.ratePerKm.isFinite() && trip.ratePerKm in 0.0..10.0) { "Neveljavna postavka vožnje." }
            require(trip.parkingCents >= 0 && trip.tollsCents >= 0) { "Neveljavni dodatni stroški." }
            require(trip.startAddress.length <= 500 && (trip.endAddress?.length ?: 0) <= 500 && trip.purpose.length <= 200) { "Predolgo besedilo v vožnji." }
            require(trip.vehicleId.length <= 100 && trip.vehicleName.length <= 80 && trip.registrationPlate.length <= 24) { "Neveljavni podatki vozila v vožnji." }
        }

        val validTripIds = tripIds.toSet()
        val pointIds = points.map { it.id }
        require(pointIds.all { it > 0L } && pointIds.toSet().size == pointIds.size) { "Neveljavni ID-ji GPS točk." }
        points.forEach { point ->
            require(point.tripId in validTripIds) { "GPS točka nima pripadajoče vožnje." }
            require(validLat(point.lat) && validLon(point.lon)) { "Neveljavna GPS točka." }
            require(point.accuracyMeters.isFinite() && point.accuracyMeters >= 0f) { "Neveljavna natančnost GPS točke." }
            require(point.segmentMeters.isFinite() && point.segmentMeters >= 0.0) { "Neveljaven GPS odsek." }
        }

        val attachmentIds = attachments.map { it.id }
        require(attachmentIds.all { it > 0L } && attachmentIds.toSet().size == attachmentIds.size) { "Neveljavni ID-ji prilog." }
        attachments.forEach { attachment ->
            require(attachment.tripId in validTripIds) { "Priloga nima pripadajoče vožnje." }
            require(attachment.displayName.isNotBlank() && attachment.displayName.length <= 160) { "Neveljavno ime priloge." }
            require(attachment.mimeType.length <= 120) { "Neveljaven tip priloge." }
            val bytes = runCatching { Base64.getDecoder().decode(attachment.contentBase64) }.getOrNull()
                ?: error("Priloga ni veljavno kodirana.")
            require(bytes.size <= MAX_ATTACHMENT_BYTES) { "Posamezna priloga je prevelika." }
        }

        val placeIds = places.map { it.id }
        require(placeIds.all { it.isNotBlank() } && placeIds.toSet().size == placeIds.size) { "Neveljavni ID-ji lokacij." }
        places.forEach { place ->
            require(place.name.isNotBlank() && place.name.length <= 60) { "Neveljavno ime priljubljene lokacije." }
            require(place.address.isNotBlank() && place.address.length <= 160) { "Neveljaven naslov priljubljene lokacije." }
            require(place.defaultPurpose.length <= 80) { "Predolg privzeti namen poti." }
            require(place.lat == null || validLat(place.lat)) { "Neveljavna širina priljubljene lokacije." }
            require(place.lon == null || validLon(place.lon)) { "Neveljavna dolžina priljubljene lokacije." }
            require(place.matchRadiusMeters in 100..1500) { "Neveljaven radij priljubljene lokacije." }
        }
    }

    private fun legacyVehicleState(settings: AppSettings): VehicleState {
        if (settings.vehicleName.isBlank() || settings.registrationPlate.isBlank()) return VehicleState()
        val vehicle = Vehicle(
            id = "legacy-default",
            name = settings.vehicleName,
            registrationPlate = settings.registrationPlate,
        )
        return VehicleState(listOf(vehicle), vehicle.id)
    }

    private fun settingsToJson(value: AppSettings) = JSONObject()
        .put("ratePerKm", value.ratePerKm)
        .put("defaultPurpose", value.defaultPurpose)
        .put("driverName", value.driverName)
        .put("companyName", value.companyName)
        .put("vehicleName", value.vehicleName)
        .put("registrationPlate", value.registrationPlate)
        .put("tripType", value.tripType)
        .put("gpsQuality", value.gpsQuality)
        .put("autoDetectionEnabled", value.autoDetectionEnabled)

    private fun settingsFromJson(obj: JSONObject) = AppSettings(
        ratePerKm = obj.getDouble("ratePerKm"),
        defaultPurpose = obj.optString("defaultPurpose", "Službena pot"),
        driverName = obj.optString("driverName", ""),
        companyName = obj.optString("companyName", ""),
        vehicleName = obj.optString("vehicleName", ""),
        registrationPlate = obj.optString("registrationPlate", ""),
        autoDetectionEnabled = obj.optBoolean("autoDetectionEnabled", false),
    )

    private fun vehicleStateToJson(value: VehicleState) = JSONObject()
        .put("defaultVehicleId", value.defaultVehicleId)
        .put("items", JSONArray().apply { value.vehicles.forEach { put(vehicleToJson(it)) } })

    private fun vehicleStateFromJson(obj: JSONObject): VehicleState {
        val array = obj.optJSONArray("items") ?: JSONArray()
        val vehicles = List(array.length()) { vehicleFromJson(array.getJSONObject(it)) }
        val requested = obj.optString("defaultVehicleId", "")
        return VehicleState(
            vehicles = vehicles,
            defaultVehicleId = requested.takeIf { id -> vehicles.any { it.id == id } } ?: vehicles.firstOrNull()?.id.orEmpty(),
        )
    }

    private fun vehicleToJson(value: Vehicle) = JSONObject()
        .put("id", value.id)
        .put("name", value.name)
        .put("registrationPlate", value.registrationPlate)

    private fun vehicleFromJson(obj: JSONObject) = Vehicle(
        id = obj.getString("id"),
        name = obj.getString("name"),
        registrationPlate = obj.getString("registrationPlate"),
    )

    private fun placeToJson(value: SavedPlace) = JSONObject()
        .put("id", value.id)
        .put("name", value.name)
        .put("address", value.address)
        .put("defaultPurpose", value.defaultPurpose)
        .putNullable("lat", value.lat)
        .putNullable("lon", value.lon)
        .put("matchRadiusMeters", value.matchRadiusMeters)

    private fun placeFromJson(obj: JSONObject) = SavedPlace(
        id = obj.getString("id"),
        name = obj.getString("name"),
        address = obj.getString("address"),
        defaultPurpose = obj.optString("defaultPurpose", ""),
        lat = obj.nullableDouble("lat"),
        lon = obj.nullableDouble("lon"),
        matchRadiusMeters = obj.optInt("matchRadiusMeters", 250).coerceIn(100, 1500),
    )

    private fun tripToJson(value: TripEntity) = JSONObject()
        .put("id", value.id)
        .put("startTime", value.startTime)
        .putNullable("endTime", value.endTime)
        .put("startLat", value.startLat)
        .put("startLon", value.startLon)
        .put("startAddress", value.startAddress)
        .putNullable("endLat", value.endLat)
        .putNullable("endLon", value.endLon)
        .putNullable("endAddress", value.endAddress)
        .put("distanceMeters", value.distanceMeters)
        .put("purpose", value.purpose)
        .put("ratePerKm", value.ratePerKm)
        .put("tollsCents", value.tollsCents)
        .put("parkingCents", value.parkingCents)
        .put("vehicleId", value.vehicleId)
        .put("vehicleName", value.vehicleName)
        .put("registrationPlate", value.registrationPlate)

    private fun tripFromJson(obj: JSONObject) = TripEntity(
        id = obj.getLong("id"),
        startTime = obj.getLong("startTime"),
        endTime = obj.nullableLong("endTime"),
        startLat = obj.getDouble("startLat"),
        startLon = obj.getDouble("startLon"),
        startAddress = obj.getString("startAddress"),
        endLat = obj.nullableDouble("endLat"),
        endLon = obj.nullableDouble("endLon"),
        endAddress = obj.nullableString("endAddress"),
        distanceMeters = obj.getDouble("distanceMeters"),
        purpose = obj.getString("purpose"),
        ratePerKm = obj.getDouble("ratePerKm"),
        tollsCents = obj.optInt("tollsCents", 0),
        parkingCents = obj.optInt("parkingCents", 0),
        vehicleId = obj.optString("vehicleId", ""),
        vehicleName = obj.optString("vehicleName", ""),
        registrationPlate = obj.optString("registrationPlate", ""),
        tripType = obj.optString("tripType", "BUSINESS").ifBlank { "BUSINESS" },
        gpsQuality = obj.optString("gpsQuality", ""),
    )

    private fun pointToJson(value: LocationPointEntity) = JSONObject()
        .put("id", value.id)
        .put("tripId", value.tripId)
        .put("timestamp", value.timestamp)
        .put("lat", value.lat)
        .put("lon", value.lon)
        .put("accuracyMeters", value.accuracyMeters.toDouble())
        .put("segmentMeters", value.segmentMeters)

    private fun pointFromJson(obj: JSONObject) = LocationPointEntity(
        id = obj.getLong("id"),
        tripId = obj.getLong("tripId"),
        timestamp = obj.getLong("timestamp"),
        lat = obj.getDouble("lat"),
        lon = obj.getDouble("lon"),
        accuracyMeters = obj.getDouble("accuracyMeters").toFloat(),
        segmentMeters = obj.getDouble("segmentMeters"),
    )

    private fun attachmentToJson(value: TripAttachmentEntity) = JSONObject()
        .put("id", value.id)
        .put("tripId", value.tripId)
        .put("displayName", value.displayName)
        .put("mimeType", value.mimeType)
        .put("contentBase64", value.contentBase64)
        .put("addedAt", value.addedAt)

    private fun attachmentFromJson(obj: JSONObject) = TripAttachmentEntity(
        id = obj.getLong("id"),
        tripId = obj.getLong("tripId"),
        displayName = obj.getString("displayName"),
        mimeType = obj.optString("mimeType", "application/octet-stream"),
        contentBase64 = obj.getString("contentBase64"),
        addedAt = obj.optLong("addedAt", 0L),
    )

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)
    private fun JSONObject.nullableLong(key: String): Long? = if (!has(key) || isNull(key)) null else getLong(key)
    private fun JSONObject.nullableDouble(key: String): Double? = if (!has(key) || isNull(key)) null else getDouble(key)
    private fun JSONObject.nullableString(key: String): String? = if (!has(key) || isNull(key)) null else getString(key)
    private fun validLat(value: Double): Boolean = value.isFinite() && value in -90.0..90.0
    private fun validLon(value: Double): Boolean = value.isFinite() && value in -180.0..180.0
}
