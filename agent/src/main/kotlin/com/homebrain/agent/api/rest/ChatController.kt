package com.homebrain.agent.api.rest

import com.homebrain.agent.api.dto.ChatRequest
import com.homebrain.agent.api.dto.ChatResponse
import com.homebrain.agent.api.mapper.ChatMapper
import com.homebrain.agent.application.ChatUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class ChatController(
    private val chatUseCase: ChatUseCase,
    private val chatMapper: ChatMapper
) {

    @PostMapping("/chat")
    fun chat(@RequestBody request: ChatRequest): ResponseEntity<ChatResponse> {
        val history = chatMapper.toDomainList(request.conversationHistory)
        
        val response = chatUseCase.chat(
            message = request.message,
            conversationHistory = history.ifEmpty { null }
        )

        return ResponseEntity.ok(chatMapper.toDto(response))
    }
}
