package com.nikitanikitin.localllmexp.utils

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone

sealed class CalendarError(message: String) : Exception(message) {
    class Unauthorized : CalendarError("Calendar permission not granted")
    class NoWritableCalendar : CalendarError("No writable calendar found")
    class EventSaveFailed(cause: Throwable) : CalendarError("Failed to save event: ${cause.message}")
    class InvalidDateRange : CalendarError("End date must be after start date")
}

class EventsUtils(private val context: Context) {

    companion object {
        private const val TAG = "EventsUtils"
    }

    fun hasPermission(): Boolean {
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
        val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
        return read == PackageManager.PERMISSION_GRANTED &&
                write == PackageManager.PERMISSION_GRANTED
    }

    suspend fun resolveCalendarId(): Long = withContext(Dispatchers.IO) {
        if (!hasPermission()) throw CalendarError.Unauthorized()

        val calendarId = findPrimaryGoogleCalendar()
            ?: findAnyWritableCalendar()
            ?: throw CalendarError.NoWritableCalendar()

        android.util.Log.d(TAG, "✅ Using calendar id=$calendarId")
        calendarId
    }

    private fun findPrimaryGoogleCalendar(): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE
        )
        val selection = "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND " +
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND " +
                "${CalendarContract.Calendars.VISIBLE} = ?"
        val args = arrayOf(
            "com.google",
            CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString(),
            "1"
        )

        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection, selection, args, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
                val accountName = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME))
                val ownerAccount = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Calendars.OWNER_ACCOUNT))

                // Primary calendar: the account owns itself (not a shared/delegated calendar)
                if (accountName != null && accountName == ownerAccount) {
                    android.util.Log.d(TAG, "✅ Found primary Google calendar id=$id account=$accountName")
                    return id
                }
            }
        }
        return null
    }

    private fun findAnyWritableCalendar(): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE
        )
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())

        data class Cal(val id: Long, val accountType: String)
        val candidates = mutableListOf<Cal>()

        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection, selection, args, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
                val type = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)) ?: ""
                candidates.add(Cal(id, type))
            }
        }

        return candidates
            .sortedBy { if (it.accountType == "com.google") 0 else 1 }
            .firstOrNull()
            ?.also { android.util.Log.d(TAG, "⚠️ Falling back to calendar id=${it.id} type=${it.accountType}") }
            ?.id
    }

    // ─── Event Creation ───────────────────────────────────────────────────────

    suspend fun createEvent(
        title: String,
        startMillis: Long,
        endMillis: Long,
        calendarId: Long,
        description: String? = null
    ): Long = withContext(Dispatchers.IO) {
        if (!hasPermission()) throw CalendarError.Unauthorized()
        if (endMillis <= startMillis) throw CalendarError.InvalidDateRange()

        android.util.Log.d(TAG, "🔵 Creating event: title=$title start=$startMillis end=$endMillis calendarId=$calendarId")

        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            if (description != null) {
                put(CalendarContract.Events.DESCRIPTION, description)
            }
        }

        val uri = try {
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                ?: throw CalendarError.EventSaveFailed(Exception("ContentResolver returned null"))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Failed to insert event", e)
            throw CalendarError.EventSaveFailed(e)
        }

        val eventId = uri.lastPathSegment?.toLong()
            ?: throw CalendarError.EventSaveFailed(Exception("Could not parse event ID from URI"))

        android.util.Log.d(TAG, "✅ Event created with id=$eventId")
        eventId
    }
}