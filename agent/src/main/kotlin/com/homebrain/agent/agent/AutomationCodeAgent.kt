package com.homebrain.agent.agent

import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.AchievesGoal
import com.embabel.agent.api.annotation.Agent
import com.embabel.agent.api.common.Ai
import com.homebrain.agent.domain.*
import com.homebrain.agent.service.EngineProxyService
import com.homebrain.agent.service.GitService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
@Agent(description = "Generates Starlark automation code from natural language descriptions")
class AutomationCodeAgent(
    private val engineProxyService: EngineProxyService,
    private val gitService: GitService
) {

    @Action(description = "Gather MQTT topics and existing automation code")
    fun gatherContext(request: CodeGenerationRequest): CodeGenerationContext {
        logger.info { "Gathering context for: ${request.userMessage.take(50)}..." }

        val topics = engineProxyService.getTopics()
        logger.debug { "Available topics: ${topics.size}" }

        val existingCode = request.existingAutomationId?.let { id ->
            try {
                val filename = "$id.star"
                gitService.readFile(filename).also {
                    logger.debug { "Read existing automation: $filename" }
                }
            } catch (e: Exception) {
                logger.warn { "Could not read existing automation ${request.existingAutomationId}: ${e.message}" }
                null
            }
        }

        return CodeGenerationContext(
            userMessage = request.userMessage,
            availableTopics = topics,
            existingCode = existingCode,
            existingAutomationId = request.existingAutomationId
        )
    }

    @Action(description = "Construct prompts for the LLM")
    fun buildPrompt(context: CodeGenerationContext): CodeGenerationPrompt {
        logger.debug { "Building prompt for code generation" }

        val userPrompt = buildString {
            appendLine("Create an automation for the following:")
            appendLine()
            appendLine(context.userMessage)
            appendLine()
            appendLine("Available MQTT topics:")
            context.availableTopics.forEach { topic ->
                appendLine("- $topic")
            }
            context.existingCode?.let { existing ->
                appendLine()
                appendLine("Existing automation code to modify:")
                appendLine(existing)
            }
        }

        return CodeGenerationPrompt(
            systemPrompt = SystemPromptContent.CONTENT,
            userPrompt = userPrompt,
            existingAutomationId = context.existingAutomationId
        )
    }

    @Action(description = "Invoke Claude to generate Starlark code")
    fun generateCode(prompt: CodeGenerationPrompt, ai: Ai): GeneratedCodeRaw {
        logger.info { "Generating code with Claude" }

        val rawCode = ai.withDefaultLlm()
            .withSystemPrompt(prompt.systemPrompt)
            .generateText(prompt.userPrompt)

        // Extract user message from the prompt for use in filename generation
        val userMessage = prompt.userPrompt
            .lines()
            .drop(2) // Skip "Create an automation..." header lines
            .takeWhile { !it.startsWith("Available MQTT topics:") }
            .joinToString(" ")
            .trim()

        return GeneratedCodeRaw(
            rawCode = rawCode,
            userMessage = userMessage,
            existingAutomationId = prompt.existingAutomationId
        )
    }

    @AchievesGoal(description = "Clean generated code and produce deployment-ready automation")
    @Action
    fun finalizeCode(raw: GeneratedCodeRaw): GeneratedAutomationCode {
        logger.debug { "Finalizing generated code" }

        val cleanedCode = cleanCode(raw.rawCode)
        val filename = raw.existingAutomationId?.let { "$it.star" }
            ?: generateFilename(raw.userMessage)

        logger.info { "Generated automation: $filename" }

        return GeneratedAutomationCode(
            code = cleanedCode,
            filename = filename,
            explanation = "Generated automation for: ${raw.userMessage.take(100)}"
        )
    }

    private fun cleanCode(code: String): String {
        var cleaned = code.trim()
        // Remove markdown code blocks
        val prefixes = listOf("```python\n", "```starlark\n", "```\n")
        for (prefix in prefixes) {
            if (cleaned.startsWith(prefix)) {
                cleaned = cleaned.removePrefix(prefix)
                break
            }
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.removeSuffix("```")
        }
        return cleaned.trim()
    }

    private fun generateFilename(description: String): String {
        val filename = description
            .lowercase()
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^a-z0-9_]"), "")
            .take(50)
            .ifEmpty { "automation" }
        return "$filename.star"
    }
}

object SystemPromptContent {
    val CONTENT = """
You are an expert automation code generator for Homebrain, an MQTT automation system.

You generate Starlark automation scripts that respond to MQTT messages.

## Starlark Automation Format

Every automation must have:
1. A 'config' dict with: name, description, subscribe (list of MQTT topics), enabled (bool), and optionally schedule (cron expression)
2. An 'on_message(topic, payload, ctx)' function that handles incoming MQTT messages
3. Optionally an 'on_schedule(ctx)' function for periodic tasks

## Available Context Functions (ctx)

- ctx.publish(topic, payload) - Publish an MQTT message (payload must be a string)
- ctx.log(message) - Log a message
- ctx.json_encode(value) - Convert a dict/list to JSON string
- ctx.json_decode(data) - Parse JSON string to dict/list
- ctx.get_state(key) - Get a persistent state value
- ctx.set_state(key, value) - Set a persistent state value
- ctx.clear_state(key) - Clear a persistent state value
- ctx.now() - Get current Unix timestamp

## Example Automation

```python
def on_message(topic, payload, ctx):
    data = ctx.json_decode(payload)
    if data.get("occupancy"):
        ctx.publish("zigbee2mqtt/light/set", ctx.json_encode({"state": "ON"}))
        ctx.set_state("last_motion", ctx.now())
        ctx.log("Motion detected - light on")

def on_schedule(ctx):
    last = ctx.get_state("last_motion")
    if last and ctx.now() - last > 300:
        ctx.publish("zigbee2mqtt/light/set", ctx.json_encode({"state": "OFF"}))
        ctx.clear_state("last_motion")

config = {
    "name": "Motion Light",
    "description": "Turn on light on motion, off after 5 min",
    "subscribe": ["zigbee2mqtt/motion_sensor"],
    "schedule": "* * * * *",
    "enabled": True,
}
```

## Rules

1. Always use ctx.json_encode() when publishing JSON payloads
2. Always use ctx.json_decode() to parse incoming payloads
3. Use descriptive names and descriptions
4. Only subscribe to topics that are needed
5. Use state for values that need to persist across messages
6. Keep automations focused on a single purpose
7. Generate valid Starlark code (Python-like but no classes, no imports)

Respond with ONLY the Starlark code, no markdown code blocks or explanations.
""".trimIndent()
}
