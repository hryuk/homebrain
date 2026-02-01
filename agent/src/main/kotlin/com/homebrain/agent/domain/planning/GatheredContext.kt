package com.homebrain.agent.domain.planning

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.homebrain.agent.domain.embedding.CodeSearchResult
import com.homebrain.agent.domain.library.LibraryModule

/**
 * Context gathered from the system for code generation.
 * 
 * This is a container object that holds the results of parallel context
 * gathering operations (topic discovery and similar code search).
 * 
 * GOAP will run gatherContext() which internally uses parallelMap to
 * fetch topics and search similar code concurrently.
 */
@JsonClassDescription("Context gathered from the smart home system")
data class GatheredContext(
    val availableTopics: List<String>,
    val relevantTopics: List<String>,
    val similarCode: List<CodeSearchResult>,
    val availableLibraries: List<LibraryModule>
) {
    
    /**
     * Check if similar code with high similarity was found.
     */
    fun hasHighSimilarityCode(threshold: Float = 0.7f): Boolean =
        similarCode.any { it.similarity >= threshold }
    
    /**
     * Get the most similar code item, if any.
     */
    fun mostSimilar(): CodeSearchResult? =
        similarCode.maxByOrNull { it.similarity }
    
    /**
     * Get relevant library functions that might be useful.
     */
    fun relevantLibraries(): List<LibraryModule> =
        availableLibraries.filter { lib ->
            lib.functions.isNotEmpty()
        }
    
    /**
     * Convert to a prompt-friendly string.
     */
    fun toPromptString(): String = buildString {
        appendLine("Available Context:")
        appendLine()
        
        if (relevantTopics.isNotEmpty()) {
            appendLine("Relevant MQTT Topics:")
            relevantTopics.forEach { appendLine("  - $it") }
            appendLine()
        }
        
        if (similarCode.isNotEmpty()) {
            appendLine("Similar Existing Code:")
            similarCode.take(3).forEach { result ->
                appendLine("  - ${result.name} (${result.type}, similarity: ${(result.similarity * 100).toInt()}%)")
            }
            appendLine()
        }
        
        if (availableLibraries.isNotEmpty()) {
            appendLine("Available Libraries:")
            availableLibraries.forEach { lib ->
                appendLine("  - ctx.lib.${lib.name}: ${lib.functions.joinToString(", ")}")
            }
        }
    }
    
    companion object {
        /**
         * Create an empty context (for testing or when nothing is found).
         */
        fun empty() = GatheredContext(
            availableTopics = emptyList(),
            relevantTopics = emptyList(),
            similarCode = emptyList(),
            availableLibraries = emptyList()
        )
    }
}
