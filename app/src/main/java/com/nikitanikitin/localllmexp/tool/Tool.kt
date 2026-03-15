package com.example.ondevicellm.tool

/**
 * Each tool declares its name, a JSON schema for the LLM, and a call() handler.
 */
interface Tool {
    val name: String

    /**
     * Prompt-engineering description injected into the LLM system prompt.
     * Can be verbose, include date hints, examples, etc.
     */
    val description: String

    /**
     * Short, clean description shown to the user (e.g. by DescribeToolsTool).
     * Defaults to the first sentence of [description] so existing tools need no changes.
     */
    val userFacingDescription: String
        get() = description.trimIndent().lines()
            .firstOrNull { it.isNotBlank() }
            ?.trim() ?: description

    /**
     * If true, the tool's return value is shown directly to the user without
     * a follow-up LLM call. Use for informational tools whose result is already
     * a complete, user-facing message (e.g. DescribeToolsTool).
     */
    val skipFollowUp: Boolean get() = false

    /** JSON Schema object describing the tool's arguments (sent to the model). */
    fun schema(): String

    /**
     * Execute the tool given a map of argument name → value (already parsed from
     * the model's JSON response).
     */
    suspend fun call(arguments: Map<String, String>): String
}