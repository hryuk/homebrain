package com.homebrain.agent.api.mapper

import com.homebrain.agent.api.dto.ChatResponse
import com.homebrain.agent.api.dto.CodeProposalDto
import com.homebrain.agent.api.dto.ConversationMessageDto
import com.homebrain.agent.domain.conversation.CodeProposal
import com.homebrain.agent.domain.conversation.Message
import com.homebrain.agent.domain.conversation.ChatResponse as DomainChatResponse
import org.springframework.stereotype.Component

/**
 * Maps between chat domain models and API DTOs.
 */
@Component
class ChatMapper {

    fun toDto(response: DomainChatResponse): ChatResponse {
        return ChatResponse(
            message = response.message,
            codeProposal = response.codeProposal?.let { toDto(it) }
        )
    }

    fun toDto(proposal: CodeProposal): CodeProposalDto {
        return CodeProposalDto(
            code = proposal.code,
            filename = proposal.filename,
            summary = proposal.summary
        )
    }

    fun toDomain(dto: ConversationMessageDto): Message {
        return Message(
            role = Message.Role.fromString(dto.role),
            content = dto.content
        )
    }

    fun toDomainList(dtos: List<ConversationMessageDto>?): List<Message> {
        return dtos?.map { toDomain(it) } ?: emptyList()
    }
}
