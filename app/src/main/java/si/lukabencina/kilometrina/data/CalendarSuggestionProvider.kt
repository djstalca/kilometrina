package si.lukabencina.kilometrina.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

object CalendarSuggestionProvider {
    suspend fun eventTitleNearTrip(context: Context, startTime: Long, endTime: Long): String? =
        withContext(Dispatchers.IO) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                return@withContext null
            }
            val windowStart = startTime - 2 * 60 * 60 * 1000L
            val windowEnd = endTime + 2 * 60 * 60 * 1000L
            val projection = arrayOf(
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
            )
            val selection = CalendarContract.Events.DTSTART + " <= ? AND (" +
                CalendarContract.Events.DTEND + " IS NULL OR " + CalendarContract.Events.DTEND + " >= ?)"
            val args = arrayOf(windowEnd.toString(), windowStart.toString())

            runCatching {
                context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    projection,
                    selection,
                    args,
                    CalendarContract.Events.DTSTART + " ASC",
                )?.use { cursor ->
                    val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                    val startIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                    val candidates = mutableListOf<Pair<String, Long>>()
                    while (cursor.moveToNext()) {
                        val title = cursor.getString(titleIndex)?.trim().orEmpty()
                        if (title.isBlank()) continue
                        val eventStart = cursor.getLong(startIndex)
                        candidates += title.take(120) to abs(eventStart - startTime)
                    }
                    candidates.minByOrNull { it.second }?.first
                }
            }.getOrNull()
        }
}
