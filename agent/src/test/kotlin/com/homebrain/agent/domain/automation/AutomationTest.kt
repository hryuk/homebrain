package com.homebrain.agent.domain.automation

import com.homebrain.agent.domain.commit.Commit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class AutomationTest {

    private val sampleCode = AutomationCode("""
        def on_message(topic, payload, ctx):
            ctx.log("Received message")
        
        config = {
            "name": "Test Automation",
            "subscribe": ["test/+"]
        }
    """.trimIndent())

    private val sampleCommit = Commit(
        hash = "abc123def456",
        message = "Add test automation",
        author = "homebrain",
        timestamp = Instant.now()
    )

    @Nested
    inner class Construction {
        @Test
        fun `should create automation with id and code`() {
            val automation = Automation(
                id = AutomationId("my_automation"),
                code = sampleCode
            )
            assertEquals("my_automation", automation.id.value)
            assertEquals(sampleCode, automation.code)
            assertNull(automation.commit)
        }

        @Test
        fun `should create automation with commit`() {
            val automation = Automation(
                id = AutomationId("my_automation"),
                code = sampleCode,
                commit = sampleCommit
            )
            assertEquals(sampleCommit, automation.commit)
        }
    }

    @Nested
    inner class ToFilename {
        @Test
        fun `should return filename with star extension`() {
            val automation = Automation(
                id = AutomationId("light_controller"),
                code = sampleCode
            )
            assertEquals("light_controller.star", automation.toFilename())
        }
    }

    @Nested
    inner class UpdateCode {
        @Test
        fun `should create new automation with updated code`() {
            val original = Automation(
                id = AutomationId("test"),
                code = sampleCode,
                commit = sampleCommit
            )
            
            val newCode = AutomationCode("def on_schedule(ctx): pass")
            val updated = original.updateCode(newCode)
            
            // Original unchanged
            assertEquals(sampleCode, original.code)
            
            // Updated has new code but same id and commit
            assertEquals(newCode, updated.code)
            assertEquals(original.id, updated.id)
            assertEquals(original.commit, updated.commit)
        }
    }

    @Nested
    inner class WithCommit {
        @Test
        fun `should create new automation with commit`() {
            val original = Automation(
                id = AutomationId("test"),
                code = sampleCode
            )
            assertNull(original.commit)
            
            val withCommit = original.withCommit(sampleCommit)
            
            // Original unchanged
            assertNull(original.commit)
            
            // New instance has commit
            assertEquals(sampleCommit, withCommit.commit)
            assertEquals(original.id, withCommit.id)
            assertEquals(original.code, withCommit.code)
        }

        @Test
        fun `should replace existing commit`() {
            val original = Automation(
                id = AutomationId("test"),
                code = sampleCode,
                commit = sampleCommit
            )
            
            val newCommit = Commit(
                hash = "newHash123",
                message = "Updated",
                author = "test",
                timestamp = Instant.now()
            )
            
            val updated = original.withCommit(newCommit)
            assertEquals(newCommit, updated.commit)
        }
    }

    @Nested
    inner class FactoryFromFilename {
        @Test
        fun `should create from filename with extension`() {
            val automation = Automation.fromFilename(
                "temperature_monitor.star",
                "def on_message(t, p, ctx): pass"
            )
            assertEquals("temperature_monitor", automation.id.value)
            assertEquals("def on_message(t, p, ctx): pass", automation.code.value)
            assertNull(automation.commit)
        }

        @Test
        fun `should create from filename without extension`() {
            val automation = Automation.fromFilename(
                "temperature_monitor",
                "def on_message(t, p, ctx): pass"
            )
            assertEquals("temperature_monitor", automation.id.value)
        }
    }

    @Nested
    inner class FactoryFromId {
        @Test
        fun `should create from id string`() {
            val automation = Automation.fromId(
                "light_controller",
                "def on_schedule(ctx): pass"
            )
            assertEquals("light_controller", automation.id.value)
            assertEquals("def on_schedule(ctx): pass", automation.code.value)
            assertNull(automation.commit)
        }
    }

    @Nested
    inner class Equality {
        @Test
        fun `automations with same properties should be equal`() {
            val auto1 = Automation(AutomationId("test"), sampleCode, sampleCommit)
            val auto2 = Automation(AutomationId("test"), sampleCode, sampleCommit)
            assertEquals(auto1, auto2)
        }

        @Test
        fun `automations with different ids should not be equal`() {
            val auto1 = Automation(AutomationId("test1"), sampleCode)
            val auto2 = Automation(AutomationId("test2"), sampleCode)
            assertNotEquals(auto1, auto2)
        }

        @Test
        fun `automations with different code should not be equal`() {
            val auto1 = Automation(AutomationId("test"), sampleCode)
            val auto2 = Automation(AutomationId("test"), AutomationCode("different code"))
            assertNotEquals(auto1, auto2)
        }

        @Test
        fun `automations with different commits should not be equal`() {
            val auto1 = Automation(AutomationId("test"), sampleCode, sampleCommit)
            val auto2 = Automation(AutomationId("test"), sampleCode, null)
            assertNotEquals(auto1, auto2)
        }
    }
}
