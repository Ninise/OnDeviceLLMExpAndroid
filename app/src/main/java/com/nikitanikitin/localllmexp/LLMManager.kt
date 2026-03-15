package com.nikitanikitin.localllmexp

import android.content.Context
import com.example.ondevicellm.tool.Tool
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class LlmManager(private val context: Context) {

    private var generativeModel: GenerativeModel? = null
    private val tools = mutableListOf<Tool>()

    // ─── Setup ────────────────────────────────────────────────────────────────

    suspend fun initialize() = withContext(Dispatchers.IO) {
        android.util.Log.d("LlmManager", "🔵 Initializing ML Kit Prompt API...")

        val model = Generation.getClient()
        generativeModel = model

        val status: Int = model.checkStatus()
        android.util.Log.d("LlmManager", "🔵 Gemini Nano status: $status")

        when (status) {
            FeatureStatus.AVAILABLE -> {
                android.util.Log.d("LlmManager", "✅ Gemini Nano is ready")
            }
            FeatureStatus.DOWNLOADABLE -> {
                android.util.Log.d("LlmManager", "🔵 Triggering Gemini Nano download/prep via AICore...")
                model.download()
                android.util.Log.d("LlmManager", "✅ Download/prep complete")
            }
            FeatureStatus.DOWNLOADING -> {
                android.util.Log.d("LlmManager", "🔵 Gemini Nano already downloading, first inference will wait")
            }
            FeatureStatus.UNAVAILABLE -> {
                throw Exception(
                    "Gemini Nano unavailable. Ensure Android AICore is installed and up to date, " +
                            "and that you're running on a supported device (Pixel 9+)."
                )
            }
            else -> throw Exception("Unexpected Gemini Nano status: $status")
        }
    }

    fun addTool(tool: Tool) {
        tools.add(tool)
        android.util.Log.d("LlmManager", "🔵 Registered tool: ${tool.name}")
    }

    // ─── Chat with tool support ───────────────────────────────────────────────

    suspend fun respond(userMessage: String): String = withContext(Dispatchers.IO) {
        val model = generativeModel ?: return@withContext "❌ LLM not initialized. Call initialize() first."

        // 1. Build the full prompt — system instructions + current date + user message
        val promptText = buildPrompt(userMessage)
        android.util.Log.d("LlmManager", "🔵 Sending prompt (${promptText.length} chars)")

        // 2. Run model
        val request = GenerateContentRequest.builder(TextPart(promptText)).build()
        val rawResponse = model.generateContent(request).candidates.firstOrNull()?.text.orEmpty()
        android.util.Log.d("LlmManager", "🔵 Raw response: $rawResponse")

        // 3. Check if the model produced a tool call
        val toolCall = parseToolCall(rawResponse)
        if (toolCall != null) {
            android.util.Log.d("LlmManager", "🔵 Tool call detected: ${toolCall.toolName}")

            val tool = tools.find { it.name == toolCall.toolName }

            // Unknown tool name — log and fall through to plain text response
            if (tool == null) {
                android.util.Log.w("LlmManager", "⚠️ Unknown tool '${toolCall.toolName}' — treating as plain text")
                return@withContext rawResponse.sanitize()
            }

            // 4. Execute the real tool
            val toolResult = tool.call(toolCall.arguments)
            android.util.Log.d("LlmManager", "🔵 Tool result: $toolResult")

            // 5. For informational tools (like describe_tools) the result IS the response
            if (tool.skipFollowUp) {
                android.util.Log.d("LlmManager", "✅ Final response (direct): $toolResult")
                return@withContext toolResult.sanitize()
            }

            // 6. For action tools, feed the result back for a natural-language confirmation
            val followUpRequest = GenerateContentRequest.builder(TextPart(buildFollowUpPrompt(userMessage, toolCall, toolResult))).build()
            val finalResponse = model.generateContent(followUpRequest).candidates.firstOrNull()?.text.orEmpty()
            android.util.Log.d("LlmManager", "✅ Final response: $finalResponse")
            return@withContext finalResponse.sanitize()
        }

        rawResponse.sanitize()
    }

    // ─── Summarization ───────────────────────────────────────────────────────────

    /**
     * Summarizes a conversation history into a compact, readable note.
     *
     * @param messages List of (role, content) pairs — USER / ASSISTANT only (SYSTEM filtered out)
     * @return A concise summary string ready to be saved as a note
     */
    suspend fun summarize(messages: List<Pair<String, String>>): String = withContext(Dispatchers.IO) {
        val model = generativeModel ?: return@withContext "Could not summarize: LLM not initialized."

        if (messages.isEmpty()) return@withContext "Empty conversation."

        val history = messages.joinToString("") { (role, content) -> "$role: $content" }

        val prompt = """
            <start_of_turn>user
            Summarize the following conversation into a concise note.
            - Use plain text, no markdown, no bullet symbols.
            - Keep it short: 3-5 sentences maximum.
            - Focus on what was decided, created, or discussed.
            - Do not include greetings or meta-commentary.
            
            Conversation:
            $history
            <end_of_turn>
            <start_of_turn>model
        """.trimIndent()

        android.util.Log.d("LlmManager", "🔵 Summarizing ${messages.size} messages")

        val request = GenerateContentRequest.builder(TextPart(prompt)).build()
        val summary = model.generateContent(request).candidates.firstOrNull()?.text.orEmpty().sanitize()

        android.util.Log.d("LlmManager", "✅ Summary: $summary")
        summary.ifBlank { "No summary available." }
    }

    // ─── Prompt Construction ──────────────────────────────────────────────────

    /**
     * Injects the current date and time into the system prompt so the model:
     * 1. Never generates stale years
     * 2. Can resolve vague expressions like "tomorrow", "next Monday", "this afternoon"
     */
    private fun buildPrompt(userMessage: String): String {
        val now = Date()
        val dateFmt = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        val isoDateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        val currentDate = dateFmt.format(now)   // e.g. "Sunday, March 15, 2026"
        val currentTime = timeFmt.format(now)   // e.g. "14:35"

        // Pre-calculate the next 14 days so the model never has to do day-of-week math itself.
        // It badly miscalculates days... so decided to precalculate
        val cal = java.util.Calendar.getInstance()
        val dateReference = buildString {
            appendLine("Date reference (use these exact dates — do NOT calculate yourself):")
            repeat(14) {
                appendLine("  ${dateFmt.format(cal.time)} = ${isoDateFmt.format(cal.time)}")
                cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        }.trimEnd()

        val toolSchemas = tools.joinToString("\n\n") { it.schema() }

        val systemPrompt = if (tools.isEmpty()) {
            """
                You are a helpful assistant.
                Today is $currentDate and the current time is $currentTime.
            """.trimIndent()
        } else {
            """
                You are a helpful assistant with access to tools.
                Today is $currentDate and the current time is $currentTime.
                
                $dateReference
                
                When the user refers to a date (e.g. "tomorrow", "next Saturday", "on Friday"),
                look it up in the Date reference above — do NOT calculate it yourself.
                If no time is specified, infer a sensible default: "morning" → 09:00, "afternoon" → 14:00,
                "evening" → 18:00, "lunch" → 12:00.
                
                ## Available Tools
                $toolSchemas
                
                ## Instructions
                - Only use a tool if the user's request EXACTLY matches one of the tools listed above.
                  NEVER invent, guess, or hallucinate tool names. The only valid tool names are the
                  "name" fields in the schemas above.
                - For general questions, greetings, or capability questions (e.g. "what can you do?"),
                  respond in plain natural language — list your available tools by their description.
                - If a tool is needed, respond ONLY with a JSON object in this exact format:
                {
                  "tool_call": {
                    "name": "<tool_name>",
                    "arguments": {
                      "<arg_name>": "<arg_value>"
                    }
                  }
                }
                - Do NOT include any explanation before or after the JSON tool call block.
            """.trimIndent()
        }

        return "<start_of_turn>user\n$systemPrompt\n\nUser: $userMessage<end_of_turn>\n<start_of_turn>model\n"
    }
    private fun buildFollowUpPrompt(
        originalMessage: String,
        toolCall: ToolCall,
        toolResult: String
    ): String = """
        <start_of_turn>user
        The user asked: "$originalMessage"
        
        You called the tool "${toolCall.toolName}" and the result was:
        $toolResult
        
        Now give the user a brief, friendly confirmation of what was done. Be concise.
        <end_of_turn>
        <start_of_turn>model
    """.trimIndent()

    // ─── Tool Call Parsing ────────────────────────────────────────────────────

    data class ToolCall(val toolName: String, val arguments: Map<String, String>)

    private fun parseToolCall(response: String): ToolCall? {
        val jsonStr = extractJson(response) ?: return null
        return try {
            val root = JSONObject(jsonStr)
            val toolCallObj = root.optJSONObject("tool_call") ?: return null
            val name = toolCallObj.optString("name").takeIf { it.isNotEmpty() } ?: return null
            // "arguments" is optional — tools with no params may omit it entirely
            val args = mutableMapOf<String, String>()
            toolCallObj.optJSONObject("arguments")?.let { argsObj ->
                argsObj.keys().forEach { key ->
                    // optString() returns the literal "null" for JSON nulls — skip it
                    val value = argsObj.optString(key)
                    if (value.isNotEmpty() && value != "null") args[key] = value
                }
            }
            ToolCall(name, args)
        } catch (e: Exception) {
            android.util.Log.w("LlmManager", "⚠️ Could not parse tool call JSON: ${e.message}")
            null
        }
    }

    private fun extractJson(text: String): String? {
        // Strip markdown code fences the model sometimes wraps around JSON (e.g. ```json ... ```)
        val stripped = text
            .replace(Regex("```[a-zA-Z]*"), "")
            .replace("```", "")
            .trim()
        val start = stripped.indexOf('{')
        val end = stripped.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) return null
        return stripped.substring(start, end + 1)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────────

    /**
     * Strips Gemma turn tokens that occasionally leak into responses,
     * and trims surrounding whitespace.
     */
    private fun String.sanitize(): String = this
        .replace("<end_of_turn>", "")
        .replace("<start_of_turn>", "")
        .replace("model", "")   // leftover "model" token after stripping <start_of_turn>
        .trim()

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    fun close() {
        generativeModel?.close()
        generativeModel = null
        android.util.Log.d("LlmManager", "🔵 LLM closed")
    }
}