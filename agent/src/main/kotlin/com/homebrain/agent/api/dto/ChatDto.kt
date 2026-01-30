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
 * DTO for a proposed code change containing one or more files.
 * When the LLM creates reusable library functions, it proposes both
 * the library and automation files together.
 */
data class CodeProposalDto(
    val summary: String,
    val files: List<FileProposalDto>
)

/**
 * DTO for a single file in a code proposal.
 */
data class FileProposalDto(
    val code: String,
    val filename: String,
    val type: String  // "automation" or "library"
)
