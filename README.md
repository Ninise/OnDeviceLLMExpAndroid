# 🤖 Android On-Device LLM with Tool Calling

A fully on-device AI assistant for Android built with **Gemini Nano** (via ML Kit GenAI Prompt API) and **Jetpack Compose**. No internet required, no API keys, no cloud calls — everything runs locally on the device.

Inspired by a similar experiment built for iOS using Apple's `FoundationModels` framework.

---

## 📱 Demo

Ask it to schedule events, save notes, or just chat:

| Prompt | What happens |
|---|---|
| `"Schedule a team meeting tomorrow at 10am"` | Creates a Google Calendar event |
| `"Remind me to call the dentist on Friday"` | Creates a calendar event on the correct date |
| `"Note: pick up dry cleaning"` | Opens Google Keep pre-filled |
| `"Summarize our chat into a note"` | Summarizes the conversation with the LLM, opens Keep |
| `"What can you do?"` | Lists all available tools |

---

## ✨ Features

- **100% on-device** — Gemini Nano runs locally via Android AICore. No data leaves the device.
- **Tool calling** — prompt-engineered JSON function calling that works on small models.
- **Calendar integration** — creates events on the user's primary Google Calendar via `CalendarContract`.
- **Creates notes** — saves notes via `Intent`, with optional LLM summarization.
- **Robust date handling** — injects a 14-day pre-computed date lookup table into every prompt, fixing the day-of-week arithmetic errors common in small models.
- **Extensible tool system** — add a new tool by implementing one interface and registering it in the ViewModel.

---

## 🏗️ Architecture

```
app/src/main/java/com/nikitanikitin/localllmexp/
├── tool/
│   ├── Tool.kt                 # Tool interface (name, description, schema, call, skipFollowUp)
│   ├── CreateEventTool.kt      # Google Calendar event creation
│   ├── CreateNoteTool.kt       # Google Keep note creation + chat summarization
│   └── DescribeToolsTool.kt    # Meta-tool: answers "what can you do?"
├── utils/
│   └── EventsUtils.kt          # CalendarContract wrapper
├── LlmManager.kt               # ML Kit Prompt API wrapper + tool-calling loop
├── ChatViewModel.kt            # State management, tool wiring
├── ChatScreen.kt               # Jetpack Compose UI
└── MainActivity.kt             # Entry point + runtime permission request
```

### Tool calling flow

The ML Kit Prompt API doesn't support native function calling, so tool invocation is implemented via prompt engineering:

```
User message
  → System prompt injected (date table + tool schemas + instructions)
  → Model responds with plain text OR { "tool_call": { "name": "...", "arguments": {...} } }
  → If tool call: execute tool → feed result back → model generates confirmation
  → If skipFollowUp=true (informational tools): return tool result directly
```

---

## 🚀 Getting Started

### Requirements

- Android device running **Android 10+ (API 26)**
- **Pixel 9** or another [AICore-supported device](https://developer.android.com/ml/gemini-nano) recommended
- Android AICore installed and up to date (pre-installed on Pixel 9)

### Device setup (one-time)

1. Open **Google Play Store** → search **"Android AICore"**
2. Join the **beta program**
3. The app updates to **"Android AICore (Beta)"** — Gemini Nano is ready

### Build & run

```bash
git clone https://github.com/YOUR_USERNAME/YOUR_REPO.git
cd YOUR_REPO
./gradlew installDebug
```

Grant the **Calendar permission** when prompted on first launch.

---

## 🔧 Adding a New Tool

1. Create a class implementing `Tool`:

```kotlin
class MyTool : Tool {
    override val name = "myTool"
    override val userFacingDescription = "Does something useful"
    override val description get() = "Detailed prompt-engineering description for the LLM."
    override val skipFollowUp = false  // true = return tool result directly, skip LLM confirmation

    override fun schema() = """
        {
          "name": "$name",
          "description": "$description",
          "parameters": {
            "type": "object",
            "properties": {
              "input": { "type": "string", "description": "..." }
            },
            "required": ["input"]
          }
        }
    """.trimIndent()

    override suspend fun call(arguments: Map<String, String>): String {
        val input = arguments["input"] ?: return "❌ Missing input"
        // do something
        return "✅ Done: $input"
    }
}
```

2. Register it in `ChatViewModel.initializeLlm()`:

```kotlin
val myTool = MyTool()
llmManager.addTool(myTool)
// Also add it to DescribeToolsTool's list so it appears in capability descriptions
llmManager.addTool(DescribeToolsTool(listOf(createEventTool, createNoteTool, myTool)))
```

---

## 🛠️ Key Implementation Details

### Date injection
Small on-device models frequently miscalculate day-of-week arithmetic. Instead of asking the model to compute "what date is next Saturday?", the system prompt includes a pre-computed 14-day lookup table generated at runtime:

```
Date reference (use these exact dates — do NOT calculate yourself):
  Sunday, March 15, 2026 = 2026-03-15
  Monday, March 16, 2026 = 2026-03-16
  ...
  Saturday, March 21, 2026 = 2026-03-21
```

### Primary calendar targeting
`EventsUtils` writes to the user's **primary Google Calendar** (where `ACCOUNT_NAME == OWNER_ACCOUNT`) rather than creating a new custom calendar. Newly created calendars are invisible in the Google Calendar app until manually enabled — this approach ensures events always appear immediately.

### Tool argument robustness
`JSONObject.optString()` returns the literal string `"null"` when a JSON field is null. The argument parser filters these out so optional tool parameters (like `endDate`) are handled cleanly as absent rather than causing parse errors downstream.

### Response sanitization
Gemma's chat turn tokens (`<end_of_turn>`, `<start_of_turn>model`) occasionally leak into generated text. All responses pass through a `sanitize()` extension before reaching the UI.

---

## 📦 Dependencies

```kotlin
// ML Kit GenAI Prompt API — Gemini Nano via AICore
implementation("com.google.mlkit:genai-prompt:1.0.0-beta1")

// Jetpack Compose BOM + Material3
implementation(platform(libs.androidx.compose.bom))
implementation(libs.androidx.material3)
```

No third-party LLM SDKs. No network permissions.

---

## 🗺️ iOS counterpart

This project is a port of an iOS experiment using Apple's [`FoundationModels`](https://developer.apple.com/documentation/foundationmodels) framework (iOS 18+). The core tool-calling pattern translates directly:

| iOS | Android |
|---|---|
| `FoundationModels` | ML Kit GenAI Prompt API |
| `Tool` protocol | `Tool` interface |
| `@Generable struct Arguments` | `Map<String, String>` parsed from JSON |
| `session.respond(to:including:)` | `LlmManager.respond()` |
| `EventKit / EKEventStore` | `CalendarContract` |
| `async/await` | Kotlin coroutines |

---

## 📄 License

MIT
