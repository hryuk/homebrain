package com.homebrain.agent.domain.planning

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.homebrain.agent.domain.conversation.CodeProposal

/**
 * Final response from the Homebrain agent.
 * 
 * This is the goal output type for GOAP planning. Three actions can produce this:
 * - respondWithAutomation: When code is successfully validated
 * - respondWithFailure: When max fix attempts are exhausted
 * - respondConversationally: When the user asked a question/chatted
 */
@JsonClassDescription("Final response from the smart home assistant")
data class AutomationResponse(
    @get:JsonPropertyDescription("The message to display to the user")
    val message: String,
    
    @get:JsonPropertyDescription("Optional validated code proposal")
    val codeProposal: CodeProposal? = null
) {
    
    init {
        require(message.isNotBlank()) { "Response message cannot be blank" }
    }
    
    /**
     * Convert to a content string for display.
     */
    fun toContentString(): String = buildString {
            appendLine(message)
            if (codeProposal != null) {
                appendLine()
                appendLine("Code proposal: ${codeProposal.summary}")
                codeProposal.files.forEach { file ->
                    appendLine("- ${file.filename} (${file.type.toApiString()})")
                }
            }
        }
    
    /**
     * Check if this response includes a code proposal.
     */
    fun hasCodeProposal(): Boolean = codeProposal != null
    
    /**
     * Check if this is a success response with validated code.
     */
    fun isSuccess(): Boolean = codeProposal != null
    
    /**
     * Check if this is a conversational response (no code).
     */
    fun isConversational(): Boolean = codeProposal == null
    
    companion object {
        /**
         * Create a successful response with validated code.
         */
        fun withCode(message: String, proposal: CodeProposal) = AutomationResponse(
            message = message,
            codeProposal = proposal
        )
        
        /**
         * Create a response from validated GeneratedCode.
         */
        fun fromGeneratedCode(generatedCode: GeneratedCode, message: String? = null) = AutomationResponse(
            message = message ?: "Here's your automation: ${generatedCode.summary}",
            codeProposal = generatedCode.toCodeProposal()
        )
        
        /**
         * Create a failure response when code generation failed.
         */
        fun failure(message: String) = AutomationResponse(
            message = message,
            codeProposal = null
        )
        
        /**
         * Create a conversational response (answer to a question).
         */
        fun conversational(answer: String) = AutomationResponse(
            message = answer,
            codeProposal = null
        )
        
        /**
         * Create from a ConversationalAnswer.
         */
        fun fromAnswer(answer: ConversationalAnswer) = AutomationResponse(
            message = answer.answer,
            codeProposal = null
        )
        
        /**
         * Default failure message when max retries are exhausted.
         */
        fun maxRetriesExhausted(attempts: Int) = AutomationResponse(
            message = "I attempted to generate an automation for your request, but " +
                "encountered issues that I couldn't resolve automatically after $attempts attempts. " +
                "Could you try again with more specific requirements? " +
                "For example, specify exact topic names, device names, or conditions.",
            codeProposal = null
        )
    }
}
