package si.lukabencina.kilometrina.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.lukabencina.kilometrina.data.*
import java.io.File
import java.util.UUID

@Composable
fun ExpenseManagerDialog(
    tripId: Long,
    expenses: List<ExpenseEntity>,
    attachments: List<AttachmentSummary>,
    onSave: suspend (ExpenseEntity) -> Unit,
    onDelete: suspend (String) -> Unit,
    onReceipt: suspend (String, Long, String, String, ByteArray) -> Unit,
    onDismiss: () -> Unit,
    copyReceipt: (suspend (AttachmentSummary, java.io.OutputStream) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    var id by rememberSaveable(tripId) { mutableStateOf(UUID.randomUUID().toString()) }
    var kind by rememberSaveable(tripId) { mutableStateOf(ExpenseKinds.PARKING) }
    var amount by rememberSaveable(tripId) { mutableStateOf("") }
    var note by rememberSaveable(tripId) { mutableStateOf("") }
    var editing by rememberSaveable(tripId) { mutableStateOf(false) }
    var pendingReceipt by rememberSaveable(tripId) { mutableStateOf<String?>(null) }
    var cameraPath by rememberSaveable(tripId) { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var discardDraft by remember { mutableStateOf(false) }
    fun requestClose() {
        if (!busy) {
            if (amount.isNotBlank() || note.isNotBlank()) discardDraft = true else onDismiss()
        }
    }
    fun reset() {
        id = UUID.randomUUID().toString(); amount = ""; note = ""; editing = false
    }
    fun importReceipt(uri: Uri, expenseId: String, cameraFile: String? = null) {
        busy = true; error = null
        scope.launch {
            try {
                val receipt = withContext(Dispatchers.IO) {
                    val mime = if (cameraFile != null) "image/jpeg" else context.contentResolver.getType(uri).orEmpty()
                    val name = if (cameraFile != null) "racun-${UUID.randomUUID()}.jpg" else {
                        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                            if (it.moveToFirst()) it.getString(0) else null
                        } ?: "Račun"
                    }
                    val data = context.contentResolver.openInputStream(uri)?.use(AttachmentInput::read)
                        ?: throw IllegalStateException("Računa ni bilo mogoče prebrati.")
                    Triple(name, mime, data)
                }
                onReceipt(expenseId, tripId, receipt.first, receipt.second, receipt.third)
                feedback = "Račun je shranjen."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Računa ni bilo mogoče dodati. Strošek ostaja shranjen." }
            finally {
                cameraFile?.let { File(it).delete() }
                busy = false
            }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val expenseId = pendingReceipt
        pendingReceipt = null
        if (uri != null && expenseId != null) importReceipt(uri, expenseId)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val expenseId = pendingReceipt
        val path = cameraPath
        pendingReceipt = null; cameraPath = null
        if (success && expenseId != null && path != null) {
            importReceipt(FileProvider.getUriForFile(context, "${context.packageName}.files", File(path)), expenseId, path)
        } else path?.let { File(it).delete() }
    }
    val entries = expenses.filter { it.tripId == tripId }
    Dialog(onDismissRequest = ::requestClose) {
        Surface(shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.fillMaxWidth().heightIn(max = 720.dp).imePadding()
                .verticalScroll(scroll).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Stroški vožnje", style = MaterialTheme.typography.headlineSmall)
                Text("Skupaj ${formatMoney(entries.sumOf { it.amountCents.toLong() } / 100.0)} · ${entries.size} postavk")
                Text("Strošek shraniš najprej. Račun lahko dodaš tudi pozneje.", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text(if (editing) "Uredi strošek" else "Dodaj strošek", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == ExpenseKinds.PARKING, enabled = !busy,
                        onClick = { kind = ExpenseKinds.PARKING }, label = { Text("Parkirnina") })
                    FilterChip(selected = kind == ExpenseKinds.TOLL, enabled = !busy,
                        onClick = { kind = ExpenseKinds.TOLL }, label = { Text("Cestnina") })
                }
                OutlinedTextField(value = amount, onValueChange = { amount = it.take(20) }, enabled = !busy,
                    label = { Text("Znesek") }, suffix = { Text("€") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = note, onValueChange = { note = it.take(500) }, enabled = !busy,
                    label = { Text("Opomba (neobvezno)") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                feedback?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                    val cents = ExpenseRules.parseCents(amount)
                    if (cents == null) { error = "Vnesi znesek, večji od nič, z največ dvema decimalkama."; return@Button }
                    busy = true; error = null; feedback = null
                    val expense = ExpenseEntity(id, tripId, kind, cents, note.trim())
                    scope.launch {
                        try { onSave(expense); reset(); feedback = "Strošek je shranjen. Račun lahko dodaš na seznamu." }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { error = failure.message ?: "Shranjevanje ni uspelo." }
                        finally { busy = false }
                    }
                }) { Text(if (busy) "Shranjujem …" else "Shrani strošek") }
                if (editing) TextButton(enabled = !busy, onClick = { reset() }) { Text("Prekliči urejanje") }
                Text("Shranjeni stroški", style = MaterialTheme.typography.titleMedium)
                entries.forEach { expense ->
                    HorizontalDivider()
                    Text("${ExpenseKinds.label(expense.kind)} · ${formatMoney(expense.amountCents / 100.0)}", style = MaterialTheme.typography.titleSmall)
                    if (expense.note.isNotBlank()) Text(expense.note)
                    val receipt = attachments.firstOrNull { it.id == expense.attachmentId }
                    if (receipt != null && copyReceipt != null) TextButton(enabled = !busy, onClick = {
                        busy = true; error = null
                        scope.launch {
                            try {
                                val file = withContext(Dispatchers.IO) {
                                    val directory = File(context.cacheDir, "receipt-preview").apply { mkdirs() }
                                    val suffix = when (receipt.mimeType) {
                                        "application/pdf" -> ".pdf"; "image/png" -> ".png"; "image/webp" -> ".webp"
                                        "image/heic" -> ".heic"; "image/heif" -> ".heif"; "image/gif" -> ".gif"
                                        "image/bmp" -> ".bmp"; "image/tiff" -> ".tif"; "image/svg+xml" -> ".svg"
                                        "image/jpeg" -> ".jpg"; else -> ".bin"
                                    }
                                    File(directory, "receipt-${receipt.id}$suffix").also { target -> target.outputStream().use { copyReceipt(receipt, it) } }
                                }
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW)
                                    .setDataAndType(uri, receipt.mimeType).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { error = "Računa ni bilo mogoče odpreti. Preveri, ali imaš nameščen pregledovalnik." }
                            finally { busy = false }
                        }
                    }) { Text("Odpri račun: ${receipt.displayName}") }
                    else Text(receipt?.let { "Račun: ${it.displayName}" } ?: "Brez računa", style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(enabled = !busy, onClick = {
                            id = expense.id; kind = expense.kind
                            amount = java.math.BigDecimal.valueOf(expense.amountCents.toLong(), 2).toPlainString().replace('.', ',')
                            note = expense.note; editing = true; error = null
                            scope.launch { scroll.animateScrollTo(0) }
                        }) { Text("Uredi") }
                        TextButton(enabled = !busy, onClick = { deleteId = expense.id }) { Text("Izbriši") }
                    }
                    Row {
                        TextButton(enabled = !busy, onClick = {
                            if (pendingReceipt != null) return@TextButton
                            pendingReceipt = expense.id
                            try { picker.launch(arrayOf("image/*", "application/pdf")) }
                            catch (failure: Exception) { pendingReceipt = null; error = "Izbirnika datotek ni bilo mogoče odpreti." }
                        }) { Text(if (receipt == null) "Dodaj račun" else "Zamenjaj račun") }
                        TextButton(enabled = !busy, onClick = {
                            try {
                                if (pendingReceipt != null) return@TextButton
                                val directory = File(context.cacheDir, "expense-camera").apply { mkdirs() }
                                val file = File(directory, "${UUID.randomUUID()}.jpg")
                                pendingReceipt = expense.id; cameraPath = file.absolutePath
                                camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
                            } catch (failure: Exception) {
                                cameraPath?.let { File(it).delete() }; cameraPath = null; pendingReceipt = null
                                error = "Fotoaparata ni bilo mogoče odpreti. Račun lahko izbereš iz datotek."
                            }
                        }) { Text("Fotografiraj") }
                    }
                }
                TextButton(enabled = !busy, onClick = ::requestClose) { Text("Zapri") }
            }
        }
    }
    deleteId?.let { candidate ->
        AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("Izbrišem strošek?") },
            text = { Text("Znesek bo odstranjen iz seštevka. Morebitni račun ostane med prilogami vožnje.") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try { onDelete(candidate); if (id == candidate) reset(); deleteId = null }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message ?: "Brisanje ni uspelo."; deleteId = null }
                    finally { busy = false }
                }
            }) { Text("Izbriši") } }, dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Prekliči") } })
    }
    if (discardDraft) AlertDialog(onDismissRequest = { discardDraft = false },
        title = { Text("Zaprem brez shranjevanja?") },
        text = { Text("Neshranjeni vnos bo zavržen. Že shranjeni stroški ostanejo pri vožnji.") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zavrzi vnos") } },
        dismissButton = { TextButton(onClick = { discardDraft = false }) { Text("Nadaljuj vnos") } })
}
