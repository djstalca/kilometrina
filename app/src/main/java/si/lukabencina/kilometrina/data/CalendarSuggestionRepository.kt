package si.lukabencina.kilometrina.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class CalendarSuggestion(
    val eventId: Long,
    val title: String,
    val location: String,
    val begin: Long,
    val end: Long,
)

class CalendarSuggestionRepository(private val context: Context) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun findBestSuggestion(startTime: Long, endTime: Long): CalendarSuggestion? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        val windowStart = startTime - 2 * 60 * 60 * 1000L
        val windowEnd = endTime + 2 * 60 * 60 * 1000L
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(windowStart.toString())
            .appendPath(windowEnd.toString())
            .build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )
        runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val eventIdIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val locationIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
                val beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val allDayIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                buildList {
                    while (cursor.moveToNext()) {
                        if (cursor.getInt(allDayIndex) != 0) continue
                        val title = cursor.getString(titleIndex)?.trim().orEmpty()
                        if (title.isBlank()) continue
                        add(
                            CalendarSuggestion(
                                eventId = cursor.getLong(eventIdIndex),
                                title = title.take(120),
                                location = cursor.getString(locationIndex)?.trim().orEmpty().take(180),
                                begin = cursor.getLong(beginIndex),
                                end = cursor.getLong(endIndex),
                            ),
                        )
                    }
                }.minByOrNull { event ->
                    val overlapPenalty = if (event.end >= startTime && event.begin <= endTime) 0L else 6 * 60 * 60 * 1000L
                    overlapPenalty + abs(event.begin - startTime)
                }
            }
        }.getOrNull()
    }
}
