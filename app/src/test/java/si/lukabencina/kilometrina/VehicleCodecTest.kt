package si.lukabencina.kilometrina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import si.lukabencina.kilometrina.data.Vehicle
import si.lukabencina.kilometrina.data.VehicleCodec

class VehicleCodecTest {
    @Test
    fun roundTripKeepsSlovenianVehicleData() {
        val vehicle = Vehicle(id = "avto-1", name = "Škodin službeni avto", registrationPlate = "LJ-ČŽ 123")
        assertEquals(vehicle, VehicleCodec.decode(VehicleCodec.encode(vehicle)))
    }

    @Test
    fun malformedValueIsRejected() {
        assertNull(VehicleCodec.decode("broken"))
    }
}
