package si.lukabencina.kilometrina

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import si.lukabencina.kilometrina.data.*

class ExpenseRulesTest {
    private val trip = TripEntity(id=1, startTime=1000, endTime=2000, startLat=46.0, startLon=14.0,
        startAddress="Dom", parkingCents=220, tollsCents=110)
    private fun data() = BackupData(2000, AppSettings(), emptyList(), trips=listOf(trip), points=emptyList())

    @Test fun decimalInputIsExactAndBounded() {
        assertEquals(220, ExpenseRules.parseCents("2,20")); assertEquals(110, ExpenseRules.parseCents("1.10"))
        assertEquals(Int.MAX_VALUE, ExpenseRules.parseCents("21474836,47"))
        listOf("", "0", "-1", "NaN", "Infinity", "2,201", "1e2", "21474836,48", "1.000,00", "1,2,3").forEach {
            assertNull("Input $it", ExpenseRules.parseCents(it))
        }
    }

    @Test fun legacyBackupKeepsOriginalTotalsExactlyOnce() {
        val raw=JSONObject(BackupCodec.encode(data())).put("schemaVersion",6).apply { remove("expenses") }
        val decoded=BackupCodec.decode(raw.toString())
        assertEquals(2,decoded.expenses.size); assertEquals(330,decoded.expenses.sumOf { it.amountCents })
        assertEquals(decoded,BackupCodec.decode(BackupCodec.encode(decoded)))
    }

    @Test fun detailedBackupKeepsReceiptLinksAndNotes() {
        val file=AttachmentEntity(id=10,tripId=1,displayName="račun.pdf",mimeType="application/pdf",data=byteArrayOf(1,2))
        val original=data().copy(attachments=listOf(file),expenses=ExpenseRules.legacy(listOf(trip)).map {
            if(it.kind==ExpenseKinds.PARKING) it.copy(attachmentId=10,note="Garaža ČŠŽ") else it
        })
        val decoded=BackupCodec.decode(BackupCodec.encode(original))
        assertEquals(original.expenses,decoded.expenses)
        assertArrayEquals(file.data,decoded.attachments.single().data)
    }

    @Test fun damagedLedgerIsRejectedInsteadOfDroppingCosts() {
        val mutations: List<(JSONObject)->Unit> = listOf(
            { it.remove("expenses") },
            { it.put("expenses","invalid") },
            { it.getJSONArray("expenses").put(it.getJSONArray("expenses").getJSONObject(0)) },
            { it.getJSONArray("expenses").getJSONObject(0).put("amountCents",1) },
            { it.getJSONArray("expenses").getJSONObject(0).put("amountCents",1.5) },
            { it.getJSONArray("expenses").getJSONObject(0).put("attachmentId",99) },
            { it.getJSONArray("expenses").getJSONObject(0).put("tripId",999) },
            { it.getJSONArray("expenses").getJSONObject(0).put("kind","UNKNOWN") },
        )
        mutations.forEach { mutate ->
            val json=JSONObject(BackupCodec.encode(data())); mutate(json)
            try { BackupCodec.decode(json.toString()); fail("Invalid ledger was accepted") } catch(expected:Exception) { }
        }
    }
}
