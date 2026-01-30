package com.homebrain.agent.api.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Request DTO for chat endpoint.
 */
data class ChatRequest(
    val message: String,
    @JsonProperty("conversation_history")
    val conversationHistory: List<ConversationMessageDto>? = null,
    @JsonProperty("existing_automation_id")
    val existingAutomationId: String? = null
)

/**
 * A message in the conversation history.
 */
data class ConversationMessageDto(
    val role: String,
    val content: String
)

/**
 * Response DTO for chat endpoint.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ChatResponse(
    val message: String,
    @JsonProperty("code_proposal")
    val codeProposal: CodeProposalDto? = null
)

/**
 * DTO for a proposed automation code.
 */
data class CodeProposalDto(
    val code: String,
    val filename: String,
    val summary: String
)
