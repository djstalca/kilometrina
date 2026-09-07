package si.lukabencina.kilometrina.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate

@Composable
fun BackupScreen(
    tripCount: Int,
    savedPlaceCount: Int,
    hasActiveTrip: Boolean,
    viewModel: BackupViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) viewModel.exportTo(uri) }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) pendingRestore = uri }

    val actionsEnabled = !state.working && !hasActiveTrip

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Podatki", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                Text("Varnostna kopija in obnova", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Lokalni podatki", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    DataLine("Zaključene vožnje", tripCount.toString())
                    DataLine("Priljubljene lokacije", savedPlaceCount.toString())
                    HorizontalDivider()
                    Text(
                        "Backup vključuje tudi nastavitve, podatke za poročila in surove GPS točke posameznih voženj.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (hasActiveTrip) {
            item {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large) {
                    Text(
                        "Za backup ali obnovitev najprej zaključi trenutno vožnjo.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        state.message?.let { message ->
            item {
                Surface(
                    color = if (state.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            message,
                            color = if (state.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        TextButton(onClick = viewModel::clearMessage) { Text("Zapri") }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        exportLauncher.launch("kilometrina-backup-${LocalDate.now()}.json")
                    },
                    enabled = actionsEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.working) "Obdelujem …" else "Izvozi varnostno kopijo")
                }
                OutlinedButton(
                    onClick = { restoreLauncher.launch(arrayOf("application/json", "text/json", "text/plain", "application/octet-stream")) },
                    enabled = actionsEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Obnovi iz varnostne kopije")
                }
            }
        }

        item {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.extraLarge) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Samodejni Android backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Aplikacija dovoljuje Android Auto Backup in prenos podatkov na nov telefon za lokalno bazo ter nastavitve. Ročni JSON backup ostaja priporočljiv pred večjimi spremembami ali menjavo telefona.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Text(
                "Varnostna kopija vsebuje zgodovino lokacij. Hrani jo na mestu, do katerega nima dostopa nekdo, ki mu teh podatkov ne želiš razkriti.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Obnovim varnostno kopijo?") },
            text = {
                Text("Obstoječe vožnje, GPS točke, priljubljene lokacije in nastavitve bodo zamenjane s podatki iz izbrane kopije. Dejanja brez druge varnostne kopije ni mogoče razveljaviti.")
            },
            confirmButton = {
                Button(onClick = {
                    pendingRestore = null
                    viewModel.restoreFrom(uri)
                }) { Text("Obnovi podatke") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("Prekliči") }
            },
        )
    }
}

@Composable
private fun DataLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
