package com.nikitanikitin.localllmexp.tool

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.ondevicellm.tool.Tool
import com.nikitanikitin.localllmexp.LlmManager

/**
 * Creates a note via Android's ACTION_SEND intent.
 *
 * Two modes depending on what the user asks:
 * - "save a note: ..."         → saves the provided content directly
 * - "summarize our chat"       → calls LlmManager.summarize() on the chat history first,
 *                                 then opens Keep with the generated summary
 *
 * @param context            Android context for launching intents
 * @param llmManager         Used for on-device summarization
 * @param chatHistoryProvider Lambda returning current chat messages as (role, content) pairs.
 *                            SYSTEM messages are filtered out inside this tool.
 */
class CreateNoteTool(
    private val context: Context,
    private val llmManager: LlmManager,
    private val chatHistoryProvider: () -> List<Pair<String, String>>
) : Tool {

    override val name = "createNote"
    override val skipFollowUp = true

    override val userFacingDescription =
        "Save a note to Keep Notes, or summarize this conversation into a note"

    override val description get() = """
        Creates a new note in Keep Notes.
        Use this when the user asks to:
        - Note something down, remember something, jot something, save a reminder as text
        - Summarize the conversation / chat history into a note
        Do NOT use this for calendar events with a specific date/time — use createEvent instead.
        For summaries, set summarize=true and leave content empty.
    """.trimIndent()

    override fun schema(): String = """
        {
          "name": "$name",
          "description": "$description",
          "parameters": {
            "type": "object",
            "properties": {
              "title": {
                "type": "string",
                "description": "Optional short title for the note."
              },
              "content": {
                "type": "string",
                "description": "The body text of the note. Leave empty if summarize is true."
              },
              "summarize": {
                "type": "string",
                "description": "Set to 'true' to summarize the conversation history into the note instead of using content."
              }
            },
            "required": []
          }
        }
    """.trimIndent()

    override suspend fun call(arguments: Map<String, String>): String {
        val title = arguments["title"]?.takeIf { it.isNotBlank() && it != "null" }
        val rawContent = arguments["content"]?.takeIf { it.isNotBlank() && it != "null" }
        val shouldSummarize = arguments["summarize"]?.trim()?.lowercase() == "true"

        android.util.Log.d("CreateNoteTool", "🔵 Called — title=$title summarize=$shouldSummarize content=$rawContent")

        // Determine note body: summarize history or use provided content
        val noteBody: String = when {
            shouldSummarize -> {
                android.util.Log.d("CreateNoteTool", "🔵 Summarizing chat history...")
                val history = chatHistoryProvider()
                    .filter { (role, _) -> role != "SYSTEM" }
                if (history.isEmpty()) return "❌ No conversation history to summarize yet."
                llmManager.summarize(history)
            }
            rawContent != null -> rawContent
            else -> return "❌ Error: provide either 'content' or set 'summarize' to true."
        }

        android.util.Log.d("CreateNoteTool", "🔵 Note body: $noteBody")

        return try {
            if (isKeepInstalled()) {
                openInKeep(title, noteBody)
                val label = if (title != null) "\"$title\"" else "note"
                if (shouldSummarize) {
                    "✅ Chat summary saved to Google Keep. Tap Save in Keep to confirm."
                } else {
                    "✅ Opened Google Keep with $label. Tap Save in Keep to confirm."
                }
            } else {
                openShareSheet(title, noteBody)
                "⚠️ Google Keep isn't installed. Opened share sheet — choose an app to save your note."
            }
        } catch (e: Exception) {
            android.util.Log.e("CreateNoteTool", "❌ Failed to open Keep", e)
            "❌ Failed to open Google Keep: ${e.message}"
        }
    }

    // ─── Keep Intent ─────────────────────────────────────────────────────────

    private fun openInKeep(title: String?, content: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage("com.google.android.keep")
            putExtra(Intent.EXTRA_TEXT, content)
            if (title != null) putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun openShareSheet(title: String?, content: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, content)
            if (title != null) putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Save note with…").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun isKeepInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.google.android.keep", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}