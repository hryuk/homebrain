package com.homebrain.agent.domain.planning

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonPropertyDescription

/**
 * Answer to a question or response to general chat.
 * 
 * Produced by the answerQuestion action when the user's intent
 * is not an automation request.
 */
@JsonClassDescription("Answer to a user question or chat response")
data class ConversationalAnswer(
    @get:JsonPropertyDescription("The response text to show the user")
    val answer: String
) {
    init {
        require(answer.isNotBlank()) { "Answer cannot be blank" }
    }
    
    companion object {
        /**
         * Create a simple answer.
         */
        fun of(answer: String) = ConversationalAnswer(answer)
    }
}
