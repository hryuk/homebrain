package com.homebrain.agent.infrastructure.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.homebrain.agent.domain.planning.GeneratedCodeResponse
import com.homebrain.agent.domain.planning.GeneratedFileResponse
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for JSON extraction from LLM responses.
 * 
 * Reproduces the bug where LLM responses after tool calls include
 * surrounding text, markdown formatting, or explanations that break
 * direct JSON parsing.
 */
class HomebrainAgentJsonExtractionTest {

    private val objectMapper = ObjectMapper().apply {
        findAndRegisterModules()
    }

    /**
     * Extracts GeneratedCodeResponse JSON from LLM text output.
     * This is the same logic used in HomebrainAgent.
     */
    private fun extractJsonResponse(text: String): GeneratedCodeResponse {
        // Try to find JSON in the response
        val jsonPatterns = listOf(
            // Direct JSON object
            Regex("""(?s)\{.*"summary".*"files".*\}"""),
            // JSON in markdown code block
            Regex("""(?s)```(?:json)?\s*(\{.*"summary".*"files".*\})\s*```"""),
        )
        
        for (pattern in jsonPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val jsonStr = if (match.groups.size > 1 && match.groups[1] != null) {
                    match.groups[1]!!.value
                } else {
                    match.value
                }
                try {
                    return objectMapper.readValue<GeneratedCodeResponse>(jsonStr)
                } catch (e: Exception) {
                    // Continue to next pattern
                }
            }
        }
        
        // Last resort: try to parse the whole text
        return try {
            objectMapper.readValue<GeneratedCodeResponse>(text.trim())
        } catch (e: Exception) {
            throw IllegalStateException("Failed to parse code generation response as JSON: ${e.message}")
        }
    }

    @Nested
    inner class CleanJsonResponse {
        @Test
        fun `should parse clean JSON response`() {
            val json = """
                {
                    "summary": "Turn on light when motion detected",
                    "files": [
                        {
                            "code": "def on_message(topic, payload, ctx):\n    ctx.publish(\"light/set\", \"{\\\"state\\\": \\\"ON\\\"}\")\n\nconfig = {\"name\": \"Motion Light\", \"subscribe\": [\"motion/+\"], \"enabled\": true}",
                            "filename": "motion_light.star",
                            "type": "automation"
                        }
                    ]
                }
            """.trimIndent()

            val result = extractJsonResponse(json)

            assertEquals("Turn on light when motion detected", result.summary)
            assertEquals(1, result.files.size)
            assertEquals("motion_light.star", result.files[0].filename)
            assertEquals("automation", result.files[0].type)
        }
    }

    @Nested
    inner class MarkdownCodeBlockResponse {
        @Test
        fun `should extract JSON from markdown code block`() {
            // This is what the LLM often returns after tool calls
            val response = """
                Based on the topics I found, here's the automation:
                
                ```json
                {
                    "summary": "Blink light on button press",
                    "files": [
                        {
                            "code": "def on_message(topic, payload, ctx):\n    ctx.log(\"Button pressed\")\n\nconfig = {\"name\": \"Button Blink\", \"subscribe\": [\"button/+\"], \"enabled\": true}",
                            "filename": "button_blink.star",
                            "type": "automation"
                        }
                    ]
                }
                ```
                
                This automation will trigger when any button topic receives a message.
            """.trimIndent()

            val result = extractJsonResponse(response)

            assertEquals("Blink light on button press", result.summary)
            assertEquals(1, result.files.size)
            assertEquals("button_blink.star", result.files[0].filename)
        }

        @Test
        fun `should extract JSON from code block without json label`() {
            val response = """
                Here's the code:
                
                ```
                {
                    "summary": "Simple automation",
                    "files": [
                        {
                            "code": "config = {}",
                            "filename": "simple.star",
                            "type": "automation"
                        }
                    ]
                }
                ```
            """.trimIndent()

            val result = extractJsonResponse(response)

            assertEquals("Simple automation", result.summary)
        }
    }

    @Nested
    inner class SurroundingTextResponse {
        @Test
        fun `should extract JSON with text before it`() {
            // BUG REPRODUCTION: This is the actual bug - LLM adds explanation before JSON
            val response = """
                I've analyzed the available topics and found several light-related devices. 
                Based on your request, I'll create an automation that syncs the lights.
                
                {
                    "summary": "Sync bedroom lights",
                    "files": [
                        {
                            "code": "def on_message(topic, payload, ctx):\n    pass\n\nconfig = {\"name\": \"Sync\", \"subscribe\": [\"bedroom/+\"], \"enabled\": true}",
                            "filename": "sync_lights.star",
                            "type": "automation"
                        }
                    ]
                }
            """.trimIndent()

            val result = extractJsonResponse(response)

            assertEquals("Sync bedroom lights", result.summary)
            assertEquals("sync_lights.star", result.files[0].filename)
        }

        @Test
        fun `should extract JSON with text after it`() {
            val response = """
                {
                    "summary": "Night mode",
                    "files": [
                        {
                            "code": "config = {\"name\": \"Night\", \"subscribe\": [], \"enabled\": true}",
                            "filename": "night_mode.star",
                            "type": "automation"
                        }
                    ]
                }
                
                Feel free to ask if you need any modifications to this automation!
            """.trimIndent()

            val result = extractJsonResponse(response)

            assertEquals("Night mode", result.summary)
        }

        @Test
        fun `should extract JSON with text both before and after`() {
            // BUG REPRODUCTION: Common pattern after tool usage
            val response = """
                I searched for topics matching "light" and found: homeassistant/light/kitchen, zigbee2mqtt/light/living_room.
                
                Here's the automation you requested:
                
                {
                    "summary": "Kitchen light automation",
                    "files": [
                        {
                            "code": "def on_message(topic, payload, ctx):\n    data = ctx.json_decode(payload)\n    if data.get(\"state\") == \"ON\":\n        ctx.log(\"Light is on\")\n\nconfig = {\"name\": \"Kitchen Light\", \"description\": \"Monitors kitchen light\", \"subscribe\": [\"homeassistant/light/kitchen\"], \"enabled\": true}",
                            "filename": "kitchen_light.star",
                            "type": "automation"
                        }
                    ]
                }
                
                The automation subscribes to the kitchen light topic and logs when it turns on.
                Let me know if you want me to add more functionality!
            """.trimIndent()

            val result = extractJsonResponse(response)

            assertEquals("Kitchen light automation", result.summary)
            assertEquals("kitchen_light.star", result.files[0].filename)
            assertTrue(result.files[0].code.contains("def on_message"))
        }
    }

    @Nested
    inner class MultipleFilesResponse {
        @Test
        fun `should parse response with library and automation`() {
            val response = """
                {
                    "summary": "Blink functionality with library",
                    "files": [
                        {
                            "code": "def blink(ctx, topic, count):\n    ctx.log(\"Blinking \" + str(count) + \" times\")",
                            "filename": "lib/lights.lib.star",
                            "type": "library"
                        },
                        {
                            "code": "def on_message(topic, payload, ctx):\n    ctx.lib.lights.blink(ctx, \"light/set\", 3)\n\nconfig = {\"name\": \"Blink on Press\", \"subscribe\": [\"button/+\"], \"enabled\": true}",
                            "filename": "blink_on_press.star",
                            "type": "automation"
                        }
                    ]
                }
            """.trimIndent()

            val result = extractJsonResponse(response)

            assertEquals("Blink functionality with library", result.summary)
            assertEquals(2, result.files.size)
            
            val library = result.files.find { it.type == "library" }
            assertNotNull(library)
            assertEquals("lib/lights.lib.star", library!!.filename)
            
            val automation = result.files.find { it.type == "automation" }
            assertNotNull(automation)
            assertEquals("blink_on_press.star", automation!!.filename)
        }
    }

    @Nested
    inner class ToolCallOutputPattern {
        @Test
        fun `should handle response after tool calls with verbose output`() {
            // BUG REPRODUCTION: This is the exact pattern that was failing
            // The LLM uses tools, gets results, then generates a response with explanation
            val response = """
                I found the following relevant topics using searchTopics:
                - zigbee2mqtt/0x680ae2fffe30d0eb (light)
                - zigbee2mqtt/0x680ae2fffe494243 (light)
                - homeassistant/light/config
                
                I also checked the existing libraries with getLibraryModules and found:
                - lights: Light control utilities
                - devices: Device state synchronization
                
                Based on this context, here's your automation:
                
                ```json
                {
                    "summary": "Blink other lights when one turns off",
                    "files": [
                        {
                            "code": "def on_message(topic, payload, ctx):\n    data = ctx.json_decode(payload)\n    if data.get(\"state\") == \"OFF\":\n        ctx.log(\"Light turned off, blinking others\")\n        ctx.lib.lights.blink(ctx, \"zigbee2mqtt/0x680ae2fffe494243/set\", 3, 500)\n\nconfig = {\n    \"name\": \"Blink Other Lights on Turn Off\",\n    \"description\": \"When any light is turned off, blink all other lights that are currently on 3 times\",\n    \"subscribe\": [\"zigbee2mqtt/0x680ae2fffe30d0eb\", \"zigbee2mqtt/0x680ae2fffe494243\", \"zigbee2mqtt/0x680ae2fffe3cd0d3\"],\n    \"enabled\": true\n}",
                            "filename": "blink_lights_on_turnoff.star",
                            "type": "automation"
                        }
                    ]
                }
                ```
                
                This automation uses the existing lights library for the blink functionality.
            """.trimIndent()

            val result = extractJsonResponse(response)

            assertEquals("Blink other lights when one turns off", result.summary)
            assertEquals(1, result.files.size)
            assertEquals("blink_lights_on_turnoff.star", result.files[0].filename)
            assertTrue(result.files[0].code.contains("ctx.lib.lights.blink"))
        }
    }

    @Nested
    inner class InvalidResponses {
        @Test
        fun `should throw for response with no JSON`() {
            val response = """
                I understand you want to create an automation, but I need more information.
                Could you please specify which devices you want to control?
            """.trimIndent()

            assertThrows(IllegalStateException::class.java) {
                extractJsonResponse(response)
            }
        }

        @Test
        fun `should throw for malformed JSON`() {
            val response = """
                {
                    "summary": "Broken JSON
                    "files": [
                }
            """.trimIndent()

            assertThrows(IllegalStateException::class.java) {
                extractJsonResponse(response)
            }
        }

        @Test
        fun `should throw for JSON missing required fields`() {
            val response = """
                {
                    "message": "This is not the right format",
                    "code": "some code"
                }
            """.trimIndent()

            assertThrows(IllegalStateException::class.java) {
                extractJsonResponse(response)
            }
        }
    }

    @Nested
    inner class EdgeCases {
        @Test
        fun `should handle JSON with escaped quotes in code`() {
            val response = """
                {
                    "summary": "Test with escapes",
                    "files": [
                        {
                            "code": "ctx.publish(\"topic\", \"{\\\"state\\\": \\\"ON\\\"}\")",
                            "filename": "test.star",
                            "type": "automation"
                        }
                    ]
                }
            """.trimIndent()

            val result = extractJsonResponse(response)

            assertTrue(result.files[0].code.contains("\\\"state\\\""))
        }

        @Test
        fun `should handle JSON with newlines in code`() {
            val response = """
                {
                    "summary": "Multi-line code",
                    "files": [
                        {
                            "code": "def on_message(topic, payload, ctx):\n    data = ctx.json_decode(payload)\n    if data.get(\"state\") == \"ON\":\n        ctx.log(\"ON\")\n\nconfig = {\"name\": \"Test\", \"subscribe\": [\"test/+\"], \"enabled\": true}",
                            "filename": "multiline.star",
                            "type": "automation"
                        }
                    ]
                }
            """.trimIndent()

            val result = extractJsonResponse(response)

            assertTrue(result.files[0].code.contains("def on_message"))
            assertTrue(result.files[0].code.contains("config = "))
        }
    }
}
