package si.lukabencina.kilometrina.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class MainTab(val label: String) {
    Home("Domov"),
    Trips("Vožnje"),
    Reports("Poročila"),
    Data("Podatki"),
    Settings("Nastavitve"),
}

@Composable
fun KilometrinaApp(viewModel: HomeViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTabName by rememberSaveable { mutableStateOf(MainTab.Home.name) }
    val selectedTab = MainTab.valueOf(selectedTabName)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    val icon = when (tab) {
                        MainTab.Home -> Icons.Outlined.Home
                        MainTab.Trips -> Icons.Outlined.ReceiptLong
                        MainTab.Reports -> Icons.Outlined.Assessment
                        MainTab.Data -> Icons.Outlined.Storage
                        MainTab.Settings -> Icons.Outlined.Settings
                    }
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTabName = tab.name },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            when (selectedTab) {
                MainTab.Home -> HomeScreen(
                    uiState = uiState,
                    onStart = viewModel::startTrip,
                    onStop = viewModel::stopTrip,
                    onResumeRecovered = viewModel::resumeRecoveredTrip,
                    onFinishRecovered = viewModel::finishRecoveredTrip,
                    onOpenTrips = { selectedTabName = MainTab.Trips.name },
                )
                MainTab.Trips -> TripsScreen(
                    trips = uiState.trips,
                    defaultRatePerKm = uiState.settings.ratePerKm,
                    savedPlaces = uiState.savedPlaces,
                    recentLocations = uiState.recentLocations,
                    vehicleState = uiState.vehicleState,
                    onDeleteTrip = viewModel::deleteTrip,
                    onUpdateTrip = viewModel::updateTrip,
                    onAddManualTrip = viewModel::addManualTrip,
                )
                MainTab.Reports -> ReportsScreen(
                    trips = uiState.trips,
                    settings = uiState.settings,
                )
                MainTab.Data -> BackupScreen(
                    tripCount = uiState.trips.count { it.endTime != null },
                    savedPlaceCount = uiState.savedPlaces.size,
                    hasActiveTrip = uiState.activeTrip != null,
                )
                MainTab.Settings -> SettingsScreen(
                    settings = uiState.settings,
                    savedPlaces = uiState.savedPlaces,
                    vehicleState = uiState.vehicleState,
                    onSave = viewModel::saveSettings,
                    onAutoDetectionEnabled = viewModel::setAutoDetectionEnabled,
                    onSavePlace = viewModel::savePlace,
                    onDeletePlace = viewModel::deletePlace,
                    onSaveVehicle = viewModel::saveVehicle,
                    onDeleteVehicle = viewModel::deleteVehicle,
                    onSetDefaultVehicle = viewModel::setDefaultVehicle,
                )
            }
        }
    }
}
