package com.homebrain.agent.domain.conversation

/**
 * Domain model representing the response from a chat interaction.
 * 
 * Always includes a message to the user, and optionally includes
 * a code proposal if the user requested an automation.
 */
data class ChatResponse(
    val message: String,
    val codeProposal: CodeProposal? = null
) {
    init {
        require(message.isNotBlank()) { "Response message cannot be blank" }
    }

    /**
     * Checks if this response includes a code proposal.
     */
    fun hasCodeProposal(): Boolean = codeProposal != null

    companion object {
        /**
         * Creates a simple text response without a code proposal.
         */
        fun text(message: String) = ChatResponse(message)

        /**
         * Creates a response with a single automation code proposal.
         */
        fun withCode(message: String, code: String, filename: String, summary: String) =
            ChatResponse(message, CodeProposal.singleAutomation(code, filename, summary))

        /**
         * Creates a response with a code proposal containing multiple files.
         */
        fun withProposal(message: String, proposal: CodeProposal) =
            ChatResponse(message, proposal)
    }
}
