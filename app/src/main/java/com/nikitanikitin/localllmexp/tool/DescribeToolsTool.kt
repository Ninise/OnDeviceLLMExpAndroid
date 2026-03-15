package com.nikitanikitin.localllmexp.tool

import com.example.ondevicellm.tool.Tool

/**
 * A meta-tool that answers "what can you do?" style questions.
 * Instead of fighting the model's tendency to call describe_tools,
 * we register it as a real tool that dynamically lists all other tools.
 *
 * @param toolRegistry The full list of registered tools — injected at registration time
 *                     so this tool always reflects the current set accurately.
 */
class DescribeToolsTool(private val toolRegistry: List<Tool>) : Tool {

    override val name = "describe_tools"

    override val skipFollowUp = true

    override val description =
        "Use this when the user asks what you can do, what tools you have, " +
                "or any general capability question. Takes no arguments."

    override fun schema(): String = """
        {
          "name": "$name",
          "description": "$description",
          "parameters": {
            "type": "object",
            "properties": {},
            "required": []
          }
        }
    """.trimIndent()

    override suspend fun call(arguments: Map<String, String>): String {
        // Filter out self so we don't describe describe_tools recursively
        val otherTools = toolRegistry.filter { it.name != name }

        if (otherTools.isEmpty()) {
            return "I don't have any tools registered yet."
        }

        val lines = otherTools.joinToString("\n") { "• ${it.name}: ${it.userFacingDescription}" }
        return "Here's what I can do:\n$lines"
    }
}