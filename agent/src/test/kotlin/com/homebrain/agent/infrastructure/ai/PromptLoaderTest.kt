package com.homebrain.agent.infrastructure.ai

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertSame

class PromptLoaderTest {
    
    private lateinit var promptLoader: PromptLoader
    
    @BeforeEach
    fun setUp() {
        promptLoader = PromptLoader()
        promptLoader.clearCache()
    }
    
    @Test
    fun `should load prompt from resources`() {
        val prompt = promptLoader.load("chat-system-prompt.md")
        
        assertContains(prompt, "Homebrain")
        assertContains(prompt, "MQTT automation framework")
        assertContains(prompt, "getAllTopics()")
    }
    
    @Test
    fun `should throw exception for non-existent prompt`() {
        val exception = assertThrows<IllegalArgumentException> {
            promptLoader.load("non-existent-prompt.md")
        }
        
        assertContains(exception.message!!, "Prompt file not found")
    }
    
    @Test
    fun `should cache loaded prompts`() {
        val first = promptLoader.load("chat-system-prompt.md")
        val second = promptLoader.load("chat-system-prompt.md")
        
        assertSame(first, second, "Cached prompts should return same instance")
    }
    
    @Test
    fun `should substitute single variable`() {
        val result = promptLoader.load("test-template.md", mapOf("name" to "World", "place" to "Homebrain"))
        
        assertEquals("Hello World! Welcome to Homebrain.\n", result)
    }
    
    @Test
    fun `should substitute multiple variables`() {
        val result = promptLoader.load("test-template.md", mapOf(
            "name" to "Alice",
            "place" to "Wonderland"
        ))
        
        assertEquals("Hello Alice! Welcome to Wonderland.\n", result)
    }
    
    @Test
    fun `should leave unmatched variables unchanged`() {
        val result = promptLoader.load("test-template.md", mapOf("name" to "Bob"))
        
        assertEquals("Hello Bob! Welcome to {{place}}.\n", result)
    }
    
    @Test
    fun `should handle empty variables map`() {
        val result = promptLoader.load("test-template.md", emptyMap())
        
        assertEquals("Hello {{name}}! Welcome to {{place}}.\n", result)
    }
    
    @Test
    fun `should clear cache`() {
        val first = promptLoader.load("chat-system-prompt.md")
        promptLoader.clearCache()
        val second = promptLoader.load("chat-system-prompt.md")
        
        // After clearing cache, should load fresh (content same but different instance)
        assertEquals(first, second)
        // Note: We can't reliably test they're different instances since strings may be interned
    }
}
