package com.nikitanikitin.localllmexp.tool

import android.content.Context
import com.example.ondevicellm.tool.Tool
import com.nikitanikitin.localllmexp.utils.EventsUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class CreateEventTool(private val context: Context) : Tool {

    override val name = "createEvent"

    override val userFacingDescription = "Create calendar events (e.g. \"Schedule a meeting tomorrow at 10am\")"

    override val description: String
        get() {
            val today = dateFormatter().format(Date())
            return """
                Creates a calendar event for the user.
                Today's date and time is $today.
                Use this tool whenever the user asks to schedule, book, add, or create
                a calendar event or meeting.
                Always infer reasonable times if the user is vague (e.g. "tomorrow morning"
                → 09:00, "afternoon" → 14:00).
            """.trimIndent()
        }

    // endDate is optional in the schema — model may omit it or send null
    override fun schema(): String = """
        {
          "name": "$name",
          "description": "$description",
          "parameters": {
            "type": "object",
            "properties": {
              "title": {
                "type": "string",
                "description": "The title or subject of the calendar event."
              },
              "startDate": {
                "type": "string",
                "description": "Start date and time in format yyyy-MM-ddTHH:mm:ss (local time, 24h)."
              },
              "endDate": {
                "type": "string",
                "description": "Optional end date and time in format yyyy-MM-ddTHH:mm:ss (local time, 24h). If omitted, the event will last 1 hour."
              },
              "description": {
                "type": "string",
                "description": "Optional notes or description for the event."
              }
            },
            "required": ["title", "startDate"]
          }
        }
    """.trimIndent()

    override suspend fun call(arguments: Map<String, String>): String {
        val title = arguments["title"]
            ?: return "❌ Error: missing required argument 'title'"
        val startDateStr = arguments["startDate"]
            ?: return "❌ Error: missing required argument 'startDate'"

        // endDate is optional — null, missing, or the literal string "null" all mean "not provided"
        val endDateStr = arguments["endDate"]?.takeIf { it.isNotBlank() && it != "null" }
        val description = arguments["description"]?.takeIf { it.isNotBlank() && it != "null" }

        android.util.Log.d("CreateEventTool", "🔵 Called with title=$title start=$startDateStr end=$endDateStr")

        val formatter = dateFormatter()

        val startDate = try {
            formatter.parse(startDateStr)
                ?: return "❌ Error: could not parse startDate '$startDateStr'. Use format yyyy-MM-ddTHH:mm:ss"
        } catch (e: Exception) {
            return "❌ Error: invalid startDate '$startDateStr' — ${e.message}"
        }

        // Default endDate = startDate + 1 hour when not provided
        val endDate = if (endDateStr != null) {
            try {
                formatter.parse(endDateStr)
                    ?: return "❌ Error: could not parse endDate '$endDateStr'. Use format yyyy-MM-ddTHH:mm:ss"
            } catch (e: Exception) {
                return "❌ Error: invalid endDate '$endDateStr' — ${e.message}"
            }
        } else {
            android.util.Log.d("CreateEventTool", "ℹ️ No endDate provided — defaulting to 1 hour after start")
            Date(startDate.time + 60 * 60 * 1000)
        }

        return try {
            val eventsUtils = EventsUtils(context)
            val calendarId = eventsUtils.resolveCalendarId()
            val eventId = eventsUtils.createEvent(
                title = title,
                startMillis = startDate.time,
                endMillis = endDate.time,
                calendarId = calendarId,
                description = description
            )
            android.util.Log.d("CreateEventTool", "✅ Event created id=$eventId")
            "✅ Event '$title' created on ${formatter.format(startDate)} (ends ${formatter.format(endDate)}). Event ID: $eventId"
        } catch (e: Exception) {
            android.util.Log.e("CreateEventTool", "❌ Failed to create event", e)
            "❌ Failed to create event: ${e.message}"
        }
    }

    private fun dateFormatter() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }
}