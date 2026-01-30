package com.homebrain.agent.domain.conversation

/**
 * Value object representing a message in a conversation.
 */
data class Message(
    val role: Role,
    val content: String
) {
    init {
        require(content.isNotBlank()) { "Message content cannot be blank" }
    }

    enum class Role {
        USER,
        ASSISTANT;

        fun toApiString(): String = name.lowercase()

        companion object {
            fun fromString(value: String): Role = when (value.lowercase()) {
                "user" -> USER
                "assistant" -> ASSISTANT
                else -> throw IllegalArgumentException("Unknown role: $value")
            }
        }
    }

    companion object {
        fun user(content: String) = Message(Role.USER, content)
        fun assistant(content: String) = Message(Role.ASSISTANT, content)
    }
}
