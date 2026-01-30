package com.homebrain.agent.domain.validation

import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ValidationResultTest {

    @Nested
    inner class Construction {
        @Test
        fun `success creates valid result with no errors`() {
            val result = ValidationResult.success()
            
            assertTrue(result.valid)
            assertTrue(result.errors.isEmpty())
        }

        @Test
        fun `failure creates invalid result with errors`() {
            val errors = listOf("error 1", "error 2")
            val result = ValidationResult.failure(errors)
            
            assertFalse(result.valid)
            assertEquals(2, result.errors.size)
            assertEquals("error 1", result.errors[0])
            assertEquals("error 2", result.errors[1])
        }

        @Test
        fun `failure with single error`() {
            val result = ValidationResult.failure(listOf("single error"))
            
            assertFalse(result.valid)
            assertEquals(1, result.errors.size)
            assertEquals("single error", result.errors[0])
        }

        @Test
        fun `failure with empty errors list is still invalid`() {
            val result = ValidationResult.failure(emptyList())
            
            assertFalse(result.valid)
            assertTrue(result.errors.isEmpty())
        }

        @Test
        fun `direct construction with valid true`() {
            val result = ValidationResult(valid = true, errors = emptyList())
            
            assertTrue(result.valid)
        }

        @Test
        fun `direct construction with valid false`() {
            val result = ValidationResult(valid = false, errors = listOf("error"))
            
            assertFalse(result.valid)
        }
    }

    @Nested
    inner class HelperMethods {
        @Test
        fun `isValid returns true for success`() {
            val result = ValidationResult.success()
            assertTrue(result.isValid())
        }

        @Test
        fun `isValid returns false for failure`() {
            val result = ValidationResult.failure(listOf("error"))
            assertFalse(result.isValid())
        }

        @Test
        fun `hasErrors returns false for success`() {
            val result = ValidationResult.success()
            assertFalse(result.hasErrors())
        }

        @Test
        fun `hasErrors returns true for failure with errors`() {
            val result = ValidationResult.failure(listOf("error"))
            assertTrue(result.hasErrors())
        }

        @Test
        fun `hasErrors returns false for failure with empty errors`() {
            val result = ValidationResult.failure(emptyList())
            assertFalse(result.hasErrors())
        }

        @Test
        fun `errorSummary returns joined errors`() {
            val result = ValidationResult.failure(listOf("error 1", "error 2"))
            assertEquals("error 1; error 2", result.errorSummary())
        }

        @Test
        fun `errorSummary returns empty string for no errors`() {
            val result = ValidationResult.success()
            assertEquals("", result.errorSummary())
        }

        @Test
        fun `errorSummary with single error`() {
            val result = ValidationResult.failure(listOf("only error"))
            assertEquals("only error", result.errorSummary())
        }
    }

    @Nested
    inner class Equality {
        @Test
        fun `two success results are equal`() {
            val r1 = ValidationResult.success()
            val r2 = ValidationResult.success()
            assertEquals(r1, r2)
        }

        @Test
        fun `two failure results with same errors are equal`() {
            val r1 = ValidationResult.failure(listOf("a", "b"))
            val r2 = ValidationResult.failure(listOf("a", "b"))
            assertEquals(r1, r2)
        }

        @Test
        fun `success and failure are not equal`() {
            val r1 = ValidationResult.success()
            val r2 = ValidationResult.failure(emptyList())
            assertNotEquals(r1, r2)
        }

        @Test
        fun `failures with different errors are not equal`() {
            val r1 = ValidationResult.failure(listOf("error 1"))
            val r2 = ValidationResult.failure(listOf("error 2"))
            assertNotEquals(r1, r2)
        }
    }

    @Nested
    inner class PropertyBasedTests {
        private val nonEmptyStringArb = Arb.string(1..100).filter { it.isNotBlank() }
        private val errorListArb = Arb.list(nonEmptyStringArb, 1..10)

        @Test
        fun `success is always valid`() = runBlocking {
            // This seems trivial but ensures the factory method is consistent
            repeat(100) {
                val result = ValidationResult.success()
                assertTrue(result.valid)
                assertTrue(result.errors.isEmpty())
            }
        }

        @Test
        fun `failure preserves all errors`() = runBlocking {
            forAll(errorListArb) { errors ->
                val result = ValidationResult.failure(errors)
                !result.valid && result.errors == errors
            }
        }

        @Test
        fun `error summary contains all errors`() = runBlocking {
            forAll(errorListArb) { errors ->
                val result = ValidationResult.failure(errors)
                val summary = result.errorSummary()
                errors.all { error -> summary.contains(error) }
            }
        }
    }
}
