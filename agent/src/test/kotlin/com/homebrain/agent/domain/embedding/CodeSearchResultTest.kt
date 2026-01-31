package com.homebrain.agent.domain.embedding

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class CodeSearchResultTest {

    @Nested
    inner class Construction {
        
        @Test
        fun `should create search result with valid similarity`() {
            val result = CodeSearchResult(
                id = "automation:test",
                type = CodeType.AUTOMATION,
                name = "test",
                sourceCode = "code",
                similarity = 0.85f
            )
            
            assertEquals("automation:test", result.id)
            assertEquals(CodeType.AUTOMATION, result.type)
            assertEquals("test", result.name)
            assertEquals("code", result.sourceCode)
            assertEquals(0.85f, result.similarity)
        }
        
        @Test
        fun `should accept similarity of 0`() {
            val result = CodeSearchResult(
                id = "automation:test",
                type = CodeType.AUTOMATION,
                name = "test",
                sourceCode = "code",
                similarity = 0f
            )
            
            assertEquals(0f, result.similarity)
        }
        
        @Test
        fun `should accept similarity of 1`() {
            val result = CodeSearchResult(
                id = "automation:test",
                type = CodeType.AUTOMATION,
                name = "test",
                sourceCode = "code",
                similarity = 1f
            )
            
            assertEquals(1f, result.similarity)
        }
        
        @Test
        fun `should reject similarity below 0`() {
            assertThrows<IllegalArgumentException> {
                CodeSearchResult(
                    id = "automation:test",
                    type = CodeType.AUTOMATION,
                    name = "test",
                    sourceCode = "code",
                    similarity = -0.1f
                )
            }
        }
        
        @Test
        fun `should reject similarity above 1`() {
            assertThrows<IllegalArgumentException> {
                CodeSearchResult(
                    id = "automation:test",
                    type = CodeType.AUTOMATION,
                    name = "test",
                    sourceCode = "code",
                    similarity = 1.1f
                )
            }
        }
    }
    
    @Nested
    inner class FromIndexedCode {
        
        @Test
        fun `should create from IndexedCode with similarity`() {
            val indexed = IndexedCode.forAutomation(
                name = "motion_light",
                sourceCode = "def on_message(t, p, ctx): pass"
            )
            
            val result = CodeSearchResult.from(indexed, 0.92f)
            
            assertEquals(indexed.id, result.id)
            assertEquals(indexed.type, result.type)
            assertEquals(indexed.name, result.name)
            assertEquals(indexed.sourceCode, result.sourceCode)
            assertEquals(0.92f, result.similarity)
        }
        
        @Test
        fun `should work with library IndexedCode`() {
            val indexed = IndexedCode.forLibrary(
                name = "timers",
                sourceCode = "def debounce(ctx, key, ms): pass"
            )
            
            val result = CodeSearchResult.from(indexed, 0.75f)
            
            assertEquals(CodeType.LIBRARY, result.type)
            assertEquals("timers", result.name)
        }
    }
    
    @Nested
    inner class Equality {
        
        @Test
        fun `results with same fields should be equal`() {
            val result1 = CodeSearchResult(
                id = "automation:test",
                type = CodeType.AUTOMATION,
                name = "test",
                sourceCode = "code",
                similarity = 0.85f
            )
            val result2 = CodeSearchResult(
                id = "automation:test",
                type = CodeType.AUTOMATION,
                name = "test",
                sourceCode = "code",
                similarity = 0.85f
            )
            
            assertEquals(result1, result2)
            assertEquals(result1.hashCode(), result2.hashCode())
        }
    }
}
