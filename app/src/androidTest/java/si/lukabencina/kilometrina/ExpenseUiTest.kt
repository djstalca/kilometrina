package si.lukabencina.kilometrina

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import si.lukabencina.kilometrina.data.*
import si.lukabencina.kilometrina.ui.*

class ExpenseUiTest {
    @get:Rule val compose=createComposeRule()

    @Test fun savesSeparatePaymentsAndSupportsEditingAndDeletion() {
        val entries=mutableStateOf<List<ExpenseEntity>>(emptyList())
        compose.setContent { MaterialTheme { ExpenseManagerDialog(14,entries.value,emptyList(),
            onSave={ e->entries.value=entries.value.filterNot { it.id==e.id }+e },
            onDelete={ id->entries.value=entries.value.filterNot { it.id==id } },onReceipt={_,_,_,_,_->},onDismiss={}) } }
        compose.onNodeWithText("Cestnina").performClick()
        compose.onNodeWithText("Znesek").performTextInput("1,10")
        compose.onNodeWithText("Shrani strošek").performScrollTo().performClick()
        compose.waitUntil { entries.value.size==1 }
        compose.onNodeWithText("Znesek").performScrollTo().performTextInput("1.10")
        compose.onNodeWithText("Shrani strošek").performScrollTo().performClick()
        compose.waitUntil { entries.value.size==2 }
        compose.runOnIdle { assertEquals(220,entries.value.sumOf { it.amountCents });assertEquals(2,entries.value.map { it.id }.toSet().size) }
        compose.onAllNodesWithText("Uredi")[0].performScrollTo().performClick()
        compose.onNodeWithText("Znesek").performScrollTo().performTextReplacement("2,20")
        compose.onNodeWithText("Shrani strošek").performScrollTo().performClick()
        compose.waitUntil { entries.value.sumOf { it.amountCents }==330 }
        compose.onAllNodesWithText("Izbriši")[0].performScrollTo().performClick()
        // A destructive action is confirmed separately from the row action.
        compose.onNodeWithText("Izbrišem strošek?").assertExists()
        compose.onAllNodesWithText("Izbriši").onLast().performClick()
        compose.waitUntil { entries.value.size==1 }
    }

    @Test fun invalidInputAndFailedSaveKeepDraftVisible() {
        compose.setContent { MaterialTheme { ExpenseManagerDialog(14,emptyList(),emptyList(),
            onSave={ throw IllegalStateException("Preizkus napake") },onDelete={},onReceipt={_,_,_,_,_->},onDismiss={}) } }
        compose.onNodeWithText("Znesek").performTextInput("0")
        compose.onNodeWithText("Shrani strošek").performScrollTo().performClick()
        compose.onNodeWithText("Vnesi znesek, večji od nič, z največ dvema decimalkama.").assertExists()
        compose.onNodeWithText("Znesek").performScrollTo().performTextReplacement("2,20")
        compose.onNodeWithText("Shrani strošek").performScrollTo().performClick()
        compose.onNodeWithText("Preizkus napake").assertExists()
        compose.onNodeWithText("Znesek").assertTextContains("2,20")
        compose.onNodeWithText("Zapri").performScrollTo().performClick()
        compose.onNodeWithText("Zaprem brez shranjevanja?").assertExists()
    }

    @Test fun activeTripCanOpenCostsWithoutStoppingAndFinishWhileDialogRemains() {
        val trip=TripEntity(id=14,startTime=1000,startLat=46.0,startLon=14.0,startAddress="Dom")
        val state=mutableStateOf(HomeUiState(activeTrip=trip,recoveryRequired=true))
        val opened=mutableStateOf<Long?>(null);var stopped=false
        val saved=mutableStateOf<ExpenseEntity?>(null)
        compose.setContent { MaterialTheme {
            HomeScreen(state.value,{_,_,_->},{stopped=true},{},{},{},{},{},onOpenExpenses={opened.value=it})
            opened.value?.let { id -> ExpenseManagerDialog(id,emptyList(),emptyList(),
                onSave={saved.value=it},onDelete={},onReceipt={_,_,_,_,_->},onDismiss={opened.value=null}) }
        } }
        compose.onNodeWithText("Dodaj strošek").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(14L,opened.value);assertFalse(stopped);state.value=HomeUiState(trips=listOf(trip.copy(endTime=2000))) }
        compose.onNodeWithText("Znesek").performTextInput("2,20")
        compose.onNodeWithText("Shrani strošek").performScrollTo().performClick()
        compose.waitUntil { saved.value!=null }
        compose.runOnIdle { assertEquals(14L,saved.value!!.tripId) }
        compose.onNodeWithText("Zapri").performScrollTo().performClick()
        compose.onNodeWithText("Pripravljen").assertExists()
    }

    @Test fun cancellingFilePickerAndCameraKeepsSavedExpense() {
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val entries=listOf(ExpenseEntity("saved",14,ExpenseKinds.PARKING,600))
        compose.setContent { MaterialTheme { ExpenseManagerDialog(14,entries,emptyList(),
            onSave={},onDelete={},onReceipt={_,_,_,_,_->fail("Cancelled receipt must not be saved")},onDismiss={}) } }
        for((action,label) in listOf(android.content.Intent.ACTION_OPEN_DOCUMENT to "Dodaj račun", "android.media.action.IMAGE_CAPTURE" to "Fotografiraj")) {
            val monitor=object:android.app.Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent:android.content.Intent):android.app.Instrumentation.ActivityResult? =
                    if(intent.action==action) android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED,null) else null
            }
            instrumentation.addMonitor(monitor)
            try {
                compose.onNodeWithText(label).performScrollTo().performClick()
                compose.waitForIdle()
                compose.onNodeWithText("Brez računa").assertExists()
                assertEquals(600,entries.single().amountCents)
            } finally { instrumentation.removeMonitor(monitor) }
        }
    }

    @Test fun fileAndCameraResultsAttachToSavedExpense() {
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val captured=java.util.concurrent.atomic.AtomicReference<Pair<String,ByteArray>?>(null)
        val directory=java.io.File(context.cacheDir,"expense-camera").apply { mkdirs() }
        val pdf=java.io.File(directory,"test-receipt.pdf").apply { writeBytes("%PDF-test-receipt".toByteArray()) }
        val pdfUri=androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.files",pdf)
        val monitor=object:android.app.Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent:android.content.Intent):android.app.Instrumentation.ActivityResult? {
                if(intent.action==android.content.Intent.ACTION_OPEN_DOCUMENT)
                    return android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK,android.content.Intent().setData(pdfUri))
                if(intent.action=="android.media.action.IMAGE_CAPTURE") {
                    @Suppress("DEPRECATION")
                    val uri=intent.getParcelableExtra<android.net.Uri>(android.provider.MediaStore.EXTRA_OUTPUT)!!
                    val bitmap=android.graphics.Bitmap.createBitmap(16,16,android.graphics.Bitmap.Config.ARGB_8888)
                    context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,90,it) }
                    bitmap.recycle()
                    return android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK,null)
                }
                return null
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            compose.setContent { MaterialTheme { ExpenseManagerDialog(14,listOf(ExpenseEntity("saved",14,ExpenseKinds.TOLL,110)),emptyList(),
                onSave={},onDelete={},onReceipt={id,trip,_,mime,bytes->
                    assertEquals("saved",id);assertEquals(14L,trip);assertTrue(bytes.isNotEmpty());captured.set(mime to bytes)
                },onDismiss={}) } }
            compose.onNodeWithText("Dodaj račun").performScrollTo().performClick()
            compose.waitUntil(10000) { captured.get()!=null }
            assertEquals("application/pdf",captured.get()!!.first)
            assertArrayEquals(pdf.readBytes(),captured.get()!!.second)
            captured.set(null)
            compose.onNodeWithText("Fotografiraj").performScrollTo().performClick()
            compose.waitUntil(10000) { captured.get()!=null }
            assertEquals("image/jpeg",captured.get()!!.first)
        } finally { instrumentation.removeMonitor(monitor);pdf.delete() }
    }
}
