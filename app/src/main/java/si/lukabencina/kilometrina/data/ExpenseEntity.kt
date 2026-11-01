package si.lukabencina.kilometrina.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

object ExpenseKinds {
    const val PARKING = "PARKING"
    const val TOLL = "TOLL"
    fun label(kind: String) = if (kind == PARKING) "Parkirnina" else "Cestnina"
}

@Entity(tableName = "expenses", foreignKeys = [
    ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = AttachmentEntity::class, parentColumns = ["id"], childColumns = ["attachmentId"], onDelete = ForeignKey.SET_NULL),
], indices = [Index("tripId"), Index(value = ["attachmentId"], unique = true)])
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val tripId: Long,
    val kind: String,
    val amountCents: Int,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val attachmentId: Long? = null,
)

object ExpenseRules {
    fun parseCents(raw: String): Int? = runCatching {
        val clean = raw.trim().replace(',', '.')
        require(Regex("[0-9]+(?:\\.[0-9]{1,2})?").matches(clean))
        BigDecimal(clean).movePointRight(2).intValueExact().also { require(it > 0) }
    }.getOrNull()

    fun validate(expense: ExpenseEntity) {
        require(expense.id.isNotBlank() && expense.id.length <= 100) { "Neveljavna oznaka stroška." }
        require(expense.kind == ExpenseKinds.PARKING || expense.kind == ExpenseKinds.TOLL) { "Izberi vrsto stroška." }
        require(expense.amountCents > 0) { "Vnesi znesek, večji od nič, z največ dvema decimalkama." }
        require(expense.note.length <= 500) { "Opomba je predolga." }
    }

    fun legacy(trips: List<TripEntity>): List<ExpenseEntity> = trips.flatMap { trip ->
        listOf(ExpenseKinds.PARKING to trip.parkingCents, ExpenseKinds.TOLL to trip.tollsCents)
            .filter { it.second > 0 }.map { (kind, amount) ->
                ExpenseEntity("legacy-${trip.id}-$kind", trip.id, kind, amount, "Preneseni skupni stroški", trip.startTime)
            }
    }
}

data class ReportExportSnapshot(
    val trips: List<TripEntity>,
    val attachments: List<AttachmentSummary>,
    val expenses: List<ExpenseEntity>,
)
