package com.homebrain.agent.domain.automation

import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AutomationCodeTest {

    @Nested
    inner class Construction {
        @Test
        fun `should create valid automation code`() {
            val code = AutomationCode("def on_message(topic, payload, ctx): pass")
            assertEquals("def on_message(topic, payload, ctx): pass", code.value)
        }

        @Test
        fun `should reject blank code`() {
            val exception = assertThrows<IllegalArgumentException> {
                AutomationCode("")
            }
            assertEquals("Automation code cannot be blank", exception.message)
        }

        @Test
        fun `should reject whitespace-only code`() {
            val exception = assertThrows<IllegalArgumentException> {
                AutomationCode("   \n\t  ")
            }
            assertEquals("Automation code cannot be blank", exception.message)
        }
    }

    @Nested
    inner class HasConfigBlock {
        @Test
        fun `should detect config block`() {
            val code = AutomationCode("""
                config = {
                    "name": "Test",
                    "subscribe": ["test/+"]
                }
            """.trimIndent())
            assertTrue(code.hasConfigBlock())
        }

        @Test
        fun `should detect config in any context`() {
            val code = AutomationCode("# This automation has a config block")
            assertTrue(code.hasConfigBlock())
        }

        @Test
        fun `should return false when no config present`() {
            val code = AutomationCode("def on_message(topic, payload, ctx): pass")
            assertFalse(code.hasConfigBlock())
        }
    }

    @Nested
    inner class HasMessageHandler {
        @Test
        fun `should detect on_message handler`() {
            val code = AutomationCode("""
                def on_message(topic, payload, ctx):
                    ctx.log("Received message")
            """.trimIndent())
            assertTrue(code.hasMessageHandler())
        }

        @Test
        fun `should return false when no on_message handler`() {
            val code = AutomationCode("""
                def on_schedule(ctx):
                    ctx.log("Scheduled run")
            """.trimIndent())
            assertFalse(code.hasMessageHandler())
        }

        @Test
        fun `should match partial function names due to contains check`() {
            // Note: Current implementation uses contains("def on_message") which WILL match this
            // This is a known limitation - the check is simple string contains
            val code = AutomationCode("def on_message_received(topic): pass")
            assertTrue(code.hasMessageHandler()) // documents actual behavior
        }

        @Test
        fun `should match with different whitespace due to contains check`() {
            val code = AutomationCode("def on_message (topic, payload, ctx):")
            // Current implementation uses contains("def on_message") which WILL match this
            assertTrue(code.hasMessageHandler()) // documents actual behavior
        }
    }

    @Nested
    inner class HasScheduleHandler {
        @Test
        fun `should detect on_schedule handler`() {
            val code = AutomationCode("""
                def on_schedule(ctx):
                    ctx.publish("heartbeat", "alive")
            """.trimIndent())
            assertTrue(code.hasScheduleHandler())
        }

        @Test
        fun `should return false when no on_schedule handler`() {
            val code = AutomationCode("""
                def on_message(topic, payload, ctx):
                    ctx.log("Message received")
            """.trimIndent())
            assertFalse(code.hasScheduleHandler())
        }
    }

    @Nested
    inner class CompleteAutomation {
        @Test
        fun `should detect all components in complete automation`() {
            val code = AutomationCode("""
                def on_message(topic, payload, ctx):
                    data = ctx.json_decode(payload)
                    ctx.log("Received: " + str(data))

                def on_schedule(ctx):
                    ctx.publish("heartbeat/status", "alive")

                config = {
                    "name": "Complete Automation",
                    "description": "Has all handlers",
                    "subscribe": ["sensor/+/state"],
                    "schedule": "*/5 * * * *",
                    "enabled": True
                }
            """.trimIndent())

            assertTrue(code.hasConfigBlock())
            assertTrue(code.hasMessageHandler())
            assertTrue(code.hasScheduleHandler())
        }
    }

    @Nested
    inner class ToString {
        @Test
        fun `should return the code value`() {
            val codeContent = "def on_message(topic, payload, ctx): pass"
            val code = AutomationCode(codeContent)
            assertEquals(codeContent, code.toString())
        }
    }

    @Nested
    inner class PropertyBasedTests {
        
        private val nonBlankStringArb = Arb.string(1..500).filter { it.isNotBlank() }

        @Test
        fun `non-blank strings should create valid code`() = runBlocking {
            forAll(nonBlankStringArb) { content ->
                val code = AutomationCode(content)
                code.value == content
            }
        }

        @Test
        fun `toString should always return the original value`() = runBlocking {
            forAll(nonBlankStringArb) { content ->
                val code = AutomationCode(content)
                code.toString() == content
            }
        }

        @Test
        fun `hasConfigBlock should return true iff code contains config`() = runBlocking {
            forAll(nonBlankStringArb) { content ->
                val code = AutomationCode(content)
                code.hasConfigBlock() == content.contains("config")
            }
        }

        @Test
        fun `hasMessageHandler should return true iff code contains def on_message`() = runBlocking {
            forAll(nonBlankStringArb) { content ->
                val code = AutomationCode(content)
                code.hasMessageHandler() == content.contains("def on_message")
            }
        }

        @Test
        fun `hasScheduleHandler should return true iff code contains def on_schedule`() = runBlocking {
            forAll(nonBlankStringArb) { content ->
                val code = AutomationCode(content)
                code.hasScheduleHandler() == content.contains("def on_schedule")
            }
        }

        @Test
        fun `codes with same value should be equal`() = runBlocking {
            forAll(nonBlankStringArb) { content ->
                val code1 = AutomationCode(content)
                val code2 = AutomationCode(content)
                code1 == code2
            }
        }
    }
}
