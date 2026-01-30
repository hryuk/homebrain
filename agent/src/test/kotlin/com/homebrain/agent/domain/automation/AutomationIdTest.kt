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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class AutomationIdTest {

    @Nested
    inner class Construction {
        @Test
        fun `should create valid automation id`() {
            val id = AutomationId("my_automation")
            assertEquals("my_automation", id.value)
        }

        @ParameterizedTest
        @ValueSource(strings = ["valid_id", "automation123", "my-automation", "CamelCase", "with.dot", "a"])
        fun `should accept valid ids`(id: String) {
            val automationId = AutomationId(id)
            assertEquals(id, automationId.value)
        }
    }

    @Nested
    inner class Validation {
        @Test
        fun `should reject blank id`() {
            val exception = assertThrows<IllegalArgumentException> {
                AutomationId("")
            }
            assertEquals("Automation ID cannot be blank", exception.message)
        }

        @Test
        fun `should reject whitespace-only id`() {
            val exception = assertThrows<IllegalArgumentException> {
                AutomationId("   ")
            }
            assertEquals("Automation ID cannot be blank", exception.message)
        }

        @Test
        fun `should reject id containing forward slash`() {
            val exception = assertThrows<IllegalArgumentException> {
                AutomationId("path/to/automation")
            }
            assertEquals("Automation ID cannot contain slashes", exception.message)
        }

        @Test
        fun `should reject id containing backslash`() {
            val exception = assertThrows<IllegalArgumentException> {
                AutomationId("path\\to\\automation")
            }
            assertEquals("Automation ID cannot contain backslashes", exception.message)
        }

        @Test
        fun `should reject id ending with star extension`() {
            val exception = assertThrows<IllegalArgumentException> {
                AutomationId("automation.star")
            }
            assertEquals("Automation ID should not include .star extension", exception.message)
        }
    }

    @Nested
    inner class ToFilename {
        @Test
        fun `should convert to filename with star extension`() {
            val id = AutomationId("temperature_monitor")
            assertEquals("temperature_monitor.star", id.toFilename())
        }

        @Test
        fun `should provide string representation`() {
            val id = AutomationId("light_controller")
            assertEquals("light_controller", id.toString())
        }
    }

    @Nested
    inner class FromFilename {
        @Test
        fun `should remove star extension`() {
            val id = AutomationId.fromFilename("my_automation.star")
            assertEquals("my_automation", id.value)
        }

        @Test
        fun `should work without extension`() {
            val id = AutomationId.fromFilename("my_automation")
            assertEquals("my_automation", id.value)
        }

        @Test
        fun `should only remove trailing star extension`() {
            val id = AutomationId.fromFilename("my.star.automation.star")
            assertEquals("my.star.automation", id.value)
        }
    }

    @Nested
    inner class Equality {
        @Test
        fun `should be equal for same value`() {
            val id1 = AutomationId("test")
            val id2 = AutomationId("test")
            assertEquals(id1, id2)
        }

        @Test
        fun `should not be equal for different values`() {
            val id1 = AutomationId("test1")
            val id2 = AutomationId("test2")
            assertNotEquals(id1, id2)
        }
    }

    @Nested
    inner class PropertyBasedTests {
        
        // Arbitrary for valid automation IDs (no slashes, backslashes, .star suffix, or blank)
        private val validIdArb = Arb.string(1..50)
            .filter { it.isNotBlank() }
            .filter { !it.contains("/") }
            .filter { !it.contains("\\") }
            .filter { !it.endsWith(".star") }

        @Test
        fun `valid IDs should round-trip through toFilename and fromFilename`() = runBlocking {
            forAll(validIdArb) { idValue ->
                val original = AutomationId(idValue)
                val roundTripped = AutomationId.fromFilename(original.toFilename())
                original.value == roundTripped.value
            }
        }

        @Test
        fun `toFilename should always end with star extension`() = runBlocking {
            forAll(validIdArb) { idValue ->
                val id = AutomationId(idValue)
                id.toFilename().endsWith(".star")
            }
        }

        @Test
        fun `toString should return the original value`() = runBlocking {
            forAll(validIdArb) { idValue ->
                val id = AutomationId(idValue)
                id.toString() == idValue
            }
        }

        @Test
        fun `IDs with same value should be equal`() = runBlocking {
            forAll(validIdArb) { idValue ->
                val id1 = AutomationId(idValue)
                val id2 = AutomationId(idValue)
                id1 == id2 && id1.hashCode() == id2.hashCode()
            }
        }

        @Test
        fun `any string containing slash should be rejected`() = runBlocking {
            val stringWithSlash = Arb.string(1..50)
                .filter { it.contains("/") && !it.contains("\\") && !it.endsWith(".star") }
            
            forAll(stringWithSlash) { idValue ->
                try {
                    AutomationId(idValue)
                    false // Should have thrown
                } catch (e: IllegalArgumentException) {
                    e.message == "Automation ID cannot contain slashes"
                }
            }
        }

        @Test
        fun `any string containing backslash should be rejected`() = runBlocking {
            val stringWithBackslash = Arb.string(1..50)
                .filter { it.contains("\\") && !it.contains("/") && !it.endsWith(".star") }
            
            forAll(stringWithBackslash) { idValue ->
                try {
                    AutomationId(idValue)
                    false // Should have thrown
                } catch (e: IllegalArgumentException) {
                    e.message == "Automation ID cannot contain backslashes"
                }
            }
        }
    }
}
