package si.lukabencina.kilometrina

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import si.lukabencina.kilometrina.data.TripEntity
import si.lukabencina.kilometrina.ui.HomeScreen
import si.lukabencina.kilometrina.ui.HomeUiState

class HomeTransitionSafetyTest {
    @get:Rule val compose=createComposeRule()
    @Test fun finishingRecoveredTripKeepsOutgoingAnimatedContentValid() {
        val trip=TripEntity(id=1,startTime=1000,startLat=46.0,startLon=14.0,startAddress="Dom")
        val state=mutableStateOf(HomeUiState(activeTrip=trip,recoveryRequired=true))
        compose.setContent { MaterialTheme {
            HomeScreen(state.value,{_,_,_->},{},{},{},{},{},{})
        } }
        compose.onNodeWithText("Nedokončana vožnja").assertExists()
        compose.runOnIdle { state.value=HomeUiState(trips=listOf(trip.copy(endTime=2000))) }
        compose.onNodeWithText("Pripravljen").assertExists()
        // Exercise both directions, including another completion while animations run.
        repeat(3) {
            compose.runOnIdle { state.value=HomeUiState(activeTrip=trip) }
            compose.onNodeWithText("Vožnja v teku").assertExists()
            compose.runOnIdle { state.value=HomeUiState() }
            compose.onNodeWithText("Pripravljen").assertExists()
        }
    }
}
