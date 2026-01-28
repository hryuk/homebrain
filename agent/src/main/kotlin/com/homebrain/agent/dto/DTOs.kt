package com.homebrain.agent.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

// Chat DTOs
data class ChatRequest(
    val message: String,
    @JsonProperty("conversation_history")
    val conversationHistory: List<ConversationMessageDto>? = null,
    @JsonProperty("existing_automation_id")
    val existingAutomationId: String? = null
)

data class ConversationMessageDto(
    val role: String,
    val content: String
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ChatResponse(
    val message: String,
    @JsonProperty("code_proposal")
    val codeProposal: CodeProposalDto? = null
)

data class CodeProposalDto(
    val code: String,
    val filename: String,
    val summary: String
)

// Automation DTOs
data class AutomationRequest(
    val filename: String? = null,
    val code: String
)

data class AutomationResponse(
    val status: String,
    val filename: String,
    val commit: String? = null
)

data class AutomationCodeResponse(
    val id: String,
    val code: String
)

// Git DTOs
data class CommitInfo(
    val hash: String,
    val message: String,
    val author: String,
    val date: Instant
)
