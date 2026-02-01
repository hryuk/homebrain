package com.homebrain.agent.domain.planning

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonPropertyDescription

/**
 * Classification of user intent.
 */
enum class IntentType {
    /** User wants to create or modify an automation */
    AUTOMATION_REQUEST,
    
    /** User is asking a question about the smart home */
    QUESTION,
    
    /** General conversation/chat */
    CHAT,
    
    /** Could not determine intent */
    UNKNOWN
}

/**
 * Result of intent classification.
 * 
 * Produced by the parseIntent action using a fast, cheap LLM (Haiku).
 * This determines which branch of the GOAP plan to follow.
 */
@JsonClassDescription("Classified user intent from their message")
data class ParsedIntent(
    @get:JsonPropertyDescription("The type of intent detected")
    val type: IntentType,
    
    @get:JsonPropertyDescription("Brief description of what the user wants")
    val description: String,
    
    @get:JsonPropertyDescription("Confidence score from 0.0 to 1.0")
    val confidence: Double,
    
    @get:JsonPropertyDescription("Extracted entities like device names, room names, conditions")
    val entities: Map<String, String> = emptyMap()
) {
    init {
        require(description.isNotBlank()) { "Intent description cannot be blank" }
        require(confidence in 0.0..1.0) { "Confidence must be between 0 and 1, got: $confidence" }
    }
    
    /**
     * Check if this is a request for automation creation/modification.
     */
    fun isAutomationRequest(): Boolean = type == IntentType.AUTOMATION_REQUEST
    
    /**
     * Check if this is a question or chat.
     */
    fun isConversational(): Boolean = type == IntentType.QUESTION || type == IntentType.CHAT
    
    companion object {
        /**
         * Create an automation request intent.
         */
        fun automationRequest(description: String, confidence: Double = 0.9, entities: Map<String, String> = emptyMap()) =
            ParsedIntent(IntentType.AUTOMATION_REQUEST, description, confidence, entities)
        
        /**
         * Create a question intent.
         */
        fun question(description: String, confidence: Double = 0.9) =
            ParsedIntent(IntentType.QUESTION, description, confidence)
        
        /**
         * Create a chat intent.
         */
        fun chat(description: String, confidence: Double = 0.9) =
            ParsedIntent(IntentType.CHAT, description, confidence)
    }
}
