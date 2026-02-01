package com.homebrain.agent.domain.planning

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.homebrain.agent.domain.conversation.Message

/**
 * Entry point for GOAP planning - represents the user's message with context.
 * 
 * This is the starting blackboard object that triggers the planning process.
 */
@JsonClassDescription("User message with conversation context")
data class UserInput(
    val message: String,
    val conversationHistory: List<Message>? = null
) {
    
    init {
        require(message.isNotBlank()) { "User message cannot be blank" }
    }
    
    /**
     * Convert to a prompt string with conversation history.
     */
    fun toPromptString(): String = buildString {
        if (!conversationHistory.isNullOrEmpty()) {
            appendLine("Conversation history:")
            conversationHistory.forEach { msg ->
                appendLine("${msg.role.toApiString()}: ${msg.content}")
            }
            appendLine()
        }
        append("User: $message")
    }
    
    companion object {
        /**
         * Create a simple user input without history.
         */
        fun simple(message: String) = UserInput(message, null)
        
        /**
         * Create user input with conversation history.
         */
        fun withHistory(message: String, history: List<Message>) = UserInput(message, history)
    }
}
