package com.homebrain.agent.api.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.homebrain.agent.api.dto.ChatRequest
import com.homebrain.agent.api.dto.ConversationMessageDto
import com.homebrain.agent.api.mapper.ChatMapper
import com.homebrain.agent.application.ChatUseCase
import com.homebrain.agent.domain.conversation.ChatResponse
import com.homebrain.agent.domain.conversation.CodeProposal
import com.homebrain.agent.domain.conversation.Message
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class ChatControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var chatUseCase: ChatUseCase
    private val chatMapper = ChatMapper()
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        chatUseCase = mockk()
        val controller = ChatController(chatUseCase, chatMapper)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Nested
    inner class ChatEndpoint {
        @Test
        fun `should return text response`() {
            val request = ChatRequest(message = "Hello")
            val response = ChatResponse.text("Hello! How can I help you?")

            every { chatUseCase.chat("Hello", null) } returns response

            mockMvc.perform(
                post("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.message").value("Hello! How can I help you?"))
                .andExpect(jsonPath("$.code_proposal").doesNotExist())
        }

        @Test
        fun `should return response with code proposal`() {
            val request = ChatRequest(message = "Create a light controller")
            val response = ChatResponse(
                message = "Here's your automation:",
                codeProposal = CodeProposal(
                    code = "def on_message(t, p, ctx): pass",
                    filename = "light_controller.star",
                    summary = "Controls lights"
                )
            )

            every { chatUseCase.chat("Create a light controller", null) } returns response

            mockMvc.perform(
                post("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.message").value("Here's your automation:"))
                .andExpect(jsonPath("$.code_proposal.code").value("def on_message(t, p, ctx): pass"))
                .andExpect(jsonPath("$.code_proposal.filename").value("light_controller.star"))
                .andExpect(jsonPath("$.code_proposal.summary").value("Controls lights"))
        }

        @Test
        fun `should pass conversation history to use case`() {
            val historySlot = slot<List<Message>>()
            val request = ChatRequest(
                message = "Continue",
                conversationHistory = listOf(
                    ConversationMessageDto("user", "Hello"),
                    ConversationMessageDto("assistant", "Hi there!")
                )
            )
            val response = ChatResponse.text("Sure, what else?")

            every { chatUseCase.chat("Continue", capture(historySlot)) } returns response

            mockMvc.perform(
                post("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)

            val captured = historySlot.captured
            assert(captured.size == 2)
            assert(captured[0].role == Message.Role.USER)
            assert(captured[0].content == "Hello")
            assert(captured[1].role == Message.Role.ASSISTANT)
        }

        @Test
        fun `should pass null history when empty`() {
            val request = ChatRequest(
                message = "Hello",
                conversationHistory = emptyList()
            )
            val response = ChatResponse.text("Hi!")

            every { chatUseCase.chat("Hello", null) } returns response

            mockMvc.perform(
                post("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)

            verify { chatUseCase.chat("Hello", null) }
        }

        @Test
        fun `should pass null history when not provided`() {
            val request = ChatRequest(message = "Hello")
            val response = ChatResponse.text("Hi!")

            every { chatUseCase.chat("Hello", null) } returns response

            mockMvc.perform(
                post("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)

            verify { chatUseCase.chat("Hello", null) }
        }
    }
}
