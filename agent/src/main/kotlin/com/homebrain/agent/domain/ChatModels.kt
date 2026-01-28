package com.homebrain.agent.domain

/**
 * Response from the conversational chat agent.
 * Always includes a message, optionally includes a code proposal.
 */
data class ChatAgentResponse(
    val message: String,
    val codeProposal: CodeProposal? = null
)

/**
 * A proposed automation code that requires user confirmation before deployment.
 */
data class CodeProposal(
    val code: String,
    val filename: String,
    val summary: String
)

/**
 * A message in the conversation history.
 */
data class ConversationMessage(
    val role: String,
    val content: String
)
