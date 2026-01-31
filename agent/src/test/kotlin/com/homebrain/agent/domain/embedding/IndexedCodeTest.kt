package com.homebrain.agent.domain.embedding

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IndexedCodeTest {

    @Nested
    inner class Construction {
        
        @Test
        fun `should create indexed code with all fields`() {
            val embedding = Embedding(FloatArray(768) { 0.5f })
            val indexed = IndexedCode(
                id = "automation:test",
                type = CodeType.AUTOMATION,
                name = "test",
                sourceCode = "def on_message(t, p, ctx): pass",
                embedding = embedding
            )
            
            assertEquals("automation:test", indexed.id)
            assertEquals(CodeType.AUTOMATION, indexed.type)
            assertEquals("test", indexed.name)
            assertTrue(indexed.sourceCode.isNotEmpty())
            assertEquals(embedding, indexed.embedding)
        }
        
        @Test
        fun `should allow null embedding`() {
            val indexed = IndexedCode(
                id = "automation:test",
                type = CodeType.AUTOMATION,
                name = "test",
                sourceCode = "def on_message(t, p, ctx): pass"
            )
            
            assertNull(indexed.embedding)
        }
        
        @Test
        fun `should reject blank id`() {
            assertThrows<IllegalArgumentException> {
                IndexedCode(
                    id = "",
                    type = CodeType.AUTOMATION,
                    name = "test",
                    sourceCode = "code"
                )
            }
        }
        
        @Test
        fun `should reject blank name`() {
            assertThrows<IllegalArgumentException> {
                IndexedCode(
                    id = "automation:test",
                    type = CodeType.AUTOMATION,
                    name = "",
                    sourceCode = "code"
                )
            }
        }
        
        @Test
        fun `should reject blank source code`() {
            assertThrows<IllegalArgumentException> {
                IndexedCode(
                    id = "automation:test",
                    type = CodeType.AUTOMATION,
                    name = "test",
                    sourceCode = ""
                )
            }
        }
    }
    
    @Nested
    inner class WithEmbedding {
        
        @Test
        fun `should create copy with embedding`() {
            val indexed = IndexedCode(
                id = "automation:test",
                type = CodeType.AUTOMATION,
                name = "test",
                sourceCode = "code"
            )
            
            val embedding = Embedding(FloatArray(768) { 0.5f })
            val withEmbedding = indexed.withEmbedding(embedding)
            
            assertEquals(indexed.id, withEmbedding.id)
            assertEquals(indexed.type, withEmbedding.type)
            assertEquals(indexed.name, withEmbedding.name)
            assertEquals(indexed.sourceCode, withEmbedding.sourceCode)
            assertEquals(embedding, withEmbedding.embedding)
        }
    }
    
    @Nested
    inner class FactoryMethods {
        
        @Test
        fun `automationId should create correct id format`() {
            assertEquals("automation:motion_light", IndexedCode.automationId("motion_light"))
        }
        
        @Test
        fun `libraryId should create correct id format`() {
            assertEquals("library:timers", IndexedCode.libraryId("timers"))
        }
        
        @Test
        fun `forAutomation should create automation IndexedCode`() {
            val indexed = IndexedCode.forAutomation(
                name = "motion_light",
                sourceCode = "def on_message(t, p, ctx): pass"
            )
            
            assertEquals("automation:motion_light", indexed.id)
            assertEquals(CodeType.AUTOMATION, indexed.type)
            assertEquals("motion_light", indexed.name)
            assertNull(indexed.embedding)
        }
        
        @Test
        fun `forAutomation should accept embedding`() {
            val embedding = Embedding(FloatArray(768) { 0.1f })
            val indexed = IndexedCode.forAutomation(
                name = "test",
                sourceCode = "code",
                embedding = embedding
            )
            
            assertEquals(embedding, indexed.embedding)
        }
        
        @Test
        fun `forLibrary should create library IndexedCode`() {
            val indexed = IndexedCode.forLibrary(
                name = "timers",
                sourceCode = "def debounce(ctx, key, ms): pass"
            )
            
            assertEquals("library:timers", indexed.id)
            assertEquals(CodeType.LIBRARY, indexed.type)
            assertEquals("timers", indexed.name)
            assertNull(indexed.embedding)
        }
        
        @Test
        fun `forLibrary should accept embedding`() {
            val embedding = Embedding(FloatArray(768) { 0.2f })
            val indexed = IndexedCode.forLibrary(
                name = "utils",
                sourceCode = "code",
                embedding = embedding
            )
            
            assertEquals(embedding, indexed.embedding)
        }
    }
    
    @Nested
    inner class Equality {
        
        @Test
        fun `indexed codes with same fields should be equal`() {
            val embedding = Embedding(FloatArray(768) { 0.5f })
            
            val indexed1 = IndexedCode(
                id = "automation:test",
                type = CodeType.AUTOMATION,
                name = "test",
                sourceCode = "code",
                embedding = embedding
            )
            val indexed2 = IndexedCode(
                id = "automation:test",
                type = CodeType.AUTOMATION,
                name = "test",
                sourceCode = "code",
                embedding = Embedding(FloatArray(768) { 0.5f })
            )
            
            assertEquals(indexed1, indexed2)
        }
    }
}
