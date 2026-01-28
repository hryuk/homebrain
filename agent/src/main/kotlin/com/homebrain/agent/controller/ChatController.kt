package com.homebrain.agent.controller

import com.embabel.agent.api.invocation.AgentInvocation
import com.embabel.agent.core.AgentPlatform
import com.homebrain.agent.agent.ConversationalChatRequest
import com.homebrain.agent.domain.ChatAgentResponse
import com.homebrain.agent.domain.ConversationMessage
import com.homebrain.agent.dto.ChatRequest
import com.homebrain.agent.dto.ChatResponse
import com.homebrain.agent.dto.CodeProposalDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class ChatController(
    private val agentPlatform: AgentPlatform
) {

    @PostMapping("/chat")
    fun chat(@RequestBody request: ChatRequest): ResponseEntity<ChatResponse> {
        val conversationHistory = request.conversationHistory?.map { msg ->
            ConversationMessage(
                role = msg.role,
                content = msg.content
            )
        }

        val chatRequest = ConversationalChatRequest(
            message = request.message,
            conversationHistory = conversationHistory
        )

        val invocation = AgentInvocation.create(
            agentPlatform,
            ChatAgentResponse::class.java
        )

        val result = invocation.invoke(chatRequest)

        return ResponseEntity.ok(ChatResponse(
            message = result.message,
            codeProposal = result.codeProposal?.let { proposal ->
                CodeProposalDto(
                    code = proposal.code,
                    filename = proposal.filename,
                    summary = proposal.summary
                )
            }
        ))
    }
}
