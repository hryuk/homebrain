package com.homebrain.agent.infrastructure.ai

import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/**
 * Loads prompt templates from classpath resources.
 * 
 * Prompts are stored as Markdown files in resources/prompts/ directory.
 * This allows prompts to be edited separately from code, with proper
 * syntax highlighting and version control diffs.
 * 
 * Supports simple variable substitution using {{variableName}} syntax.
 */
@Component
class PromptLoader {
    
    private val cache = mutableMapOf<String, String>()
    
    /**
     * Load a prompt template from resources/prompts/.
     * 
     * Results are cached after first load for performance.
     * 
     * @param name The prompt file name (e.g., "chat-system-prompt.md")
     * @return The prompt content as a string
     * @throws IllegalArgumentException if the prompt file doesn't exist
     */
    fun load(name: String): String {
        return cache.getOrPut(name) {
            val resource = ClassPathResource("prompts/$name")
            require(resource.exists()) { "Prompt file not found: prompts/$name" }
            resource.inputStream.bufferedReader().readText()
        }
    }
    
    /**
     * Load a prompt template and replace variables.
     * 
     * Variables in the template use {{variableName}} syntax.
     * 
     * @param name The prompt file name
     * @param variables Map of variable names to values
     * @return The prompt with variables substituted
     */
    fun load(name: String, variables: Map<String, String>): String {
        var content = load(name)
        variables.forEach { (key, value) ->
            content = content.replace("{{$key}}", value)
        }
        return content
    }
    
    /**
     * Clear the prompt cache.
     * 
     * Primarily useful for testing.
     */
    fun clearCache() {
        cache.clear()
    }
}
