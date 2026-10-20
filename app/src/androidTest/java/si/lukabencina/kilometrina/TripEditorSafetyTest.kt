package si.lukabencina.kilometrina

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import si.lukabencina.kilometrina.data.*
import si.lukabencina.kilometrina.ui.EditTripDialog

class TripEditorSafetyTest {
    @get:Rule val compose=createComposeRule()
    private val trip=TripEntity(id=1,startTime=1790841600000,endTime=1790845200000,
        startLat=46.0,startLon=14.0,startAddress="Dom",endAddress="Prodent",distanceMeters=12345.0,
        ratePerKm=0.4321,vehicleId="car",vehicleName="Old Škoda",registrationPlate="LJ-OLD")
    private val updated=Vehicle("car","Updated Škoda","LJ-NEW")
    @Test fun savingWithoutChangesPreservesExactMeasurementsAndVehicleSnapshot() {
        var saved:TripEntity?=null
        compose.setContent { MaterialTheme {
            EditTripDialog(trip,emptyList(),emptyList(),VehicleState(listOf(updated),updated.id),{}, { saved=it })
        } }
        compose.onNodeWithText("Shrani").performClick()
        compose.runOnIdle {
            val result=requireNotNull(saved)
            assertEquals(trip.distanceMeters,result.distanceMeters,0.0)
            assertEquals(trip.ratePerKm,result.ratePerKm,0.0)
            assertEquals(trip.vehicleId,result.vehicleId)
            assertEquals(trip.vehicleName,result.vehicleName)
            assertEquals(trip.registrationPlate,result.registrationPlate)
        }
    }
    @Test fun explicitlySelectingUpdatedVehicleChangesOnlyVehicleSnapshot() {
        var saved:TripEntity?=null
        compose.setContent { MaterialTheme {
            EditTripDialog(trip,emptyList(),emptyList(),VehicleState(listOf(updated),updated.id),{}, { saved=it })
        } }
        // First scroll the outer form; scrolling a chip only moves its LazyRow.
        compose.onNodeWithText("Kilometri").performScrollTo()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNodeWithText("Updated Škoda • LJ-NEW").assertIsDisplayed().performClick().assertIsSelected()
        compose.onNodeWithText("Shrani").performClick()
        compose.runOnIdle {
            val result=requireNotNull(saved)
            assertEquals(trip.distanceMeters,result.distanceMeters,0.0)
            assertEquals(updated.id,result.vehicleId)
            assertEquals(updated.name,result.vehicleName)
            assertEquals(updated.registrationPlate,result.registrationPlate)
        }
    }
    @Test fun explicitlyEditingDistanceAndRateSavesTheEnteredValues() {
        var saved:TripEntity?=null
        compose.setContent { MaterialTheme {
            EditTripDialog(trip,emptyList(),emptyList(),VehicleState(listOf(updated),updated.id),{}, { saved=it })
        } }
        compose.onNodeWithText("12.3").performScrollTo().performTextReplacement("14.567")
        compose.onNodeWithText("0.43").performScrollTo().performTextReplacement("0.5")
        compose.onNodeWithText("Shrani").performClick()
        compose.runOnIdle {
            val result=requireNotNull(saved)
            assertEquals(14567.0,result.distanceMeters,0.0001)
            assertEquals(0.5,result.ratePerKm,0.0)
            assertEquals(trip.vehicleName,result.vehicleName)
            assertEquals(trip.registrationPlate,result.registrationPlate)
        }
    }
}
