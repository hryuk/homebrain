package com.homebrain.agent.api.mapper

import com.homebrain.agent.api.dto.ConversationMessageDto
import com.homebrain.agent.domain.conversation.ChatResponse
import com.homebrain.agent.domain.conversation.CodeProposal
import com.homebrain.agent.domain.conversation.Message
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ChatMapperTest {

    private lateinit var mapper: ChatMapper

    @BeforeEach
    fun setUp() {
        mapper = ChatMapper()
    }

    @Nested
    inner class ToDtoChatResponse {
        @Test
        fun `should map response without code proposal`() {
            val response = ChatResponse.text("I can help you with that.")

            val result = mapper.toDto(response)

            assertEquals("I can help you with that.", result.message)
            assertNull(result.codeProposal)
        }

        @Test
        fun `should map response with code proposal`() {
            val response = ChatResponse.withCode(
                message = "Here's your automation:",
                code = "def on_message(t, p, ctx): pass",
                filename = "light_controller",
                summary = "Controls lights based on motion"
            )

            val result = mapper.toDto(response)

            assertEquals("Here's your automation:", result.message)
            assertNotNull(result.codeProposal)
            assertEquals("Controls lights based on motion", result.codeProposal?.summary)
            assertEquals(1, result.codeProposal?.files?.size)
            assertEquals("def on_message(t, p, ctx): pass", result.codeProposal?.files?.get(0)?.code)
            assertEquals("light_controller", result.codeProposal?.files?.get(0)?.filename)
            assertEquals("automation", result.codeProposal?.files?.get(0)?.type)
        }
    }

    @Nested
    inner class ToDtoCodeProposal {
        @Test
        fun `should map single file code proposal`() {
            val proposal = CodeProposal.singleAutomation(
                code = """
                    def on_message(topic, payload, ctx):
                        ctx.log("Received message")
                    
                    config = {"name": "Test", "subscribe": ["test/+"]}
                """.trimIndent(),
                filename = "test_automation.star",
                summary = "Logs all messages from test topics"
            )

            val result = mapper.toDto(proposal)

            assertEquals("Logs all messages from test topics", result.summary)
            assertEquals(1, result.files.size)
            assertTrue(result.files[0].code.contains("def on_message"))
            assertEquals("test_automation.star", result.files[0].filename)
            assertEquals("automation", result.files[0].type)
        }

        @Test
        fun `should map multi-file code proposal`() {
            val proposal = CodeProposal.withLibrary(
                libraryCode = "def helper(ctx): pass",
                libraryFilename = "lib/helpers.lib.star",
                automationCode = "def on_message(t, p, ctx): ctx.lib.helpers.helper(ctx)",
                automationFilename = "use_helpers.star",
                summary = "Automation with library function"
            )

            val result = mapper.toDto(proposal)

            assertEquals("Automation with library function", result.summary)
            assertEquals(2, result.files.size)
            
            // Library should be first
            assertEquals("lib/helpers.lib.star", result.files[0].filename)
            assertEquals("library", result.files[0].type)
            
            // Automation should be second
            assertEquals("use_helpers.star", result.files[1].filename)
            assertEquals("automation", result.files[1].type)
        }
    }

    @Nested
    inner class ToDomainMessage {
        @Test
        fun `should map user message`() {
            val dto = ConversationMessageDto(role = "user", content = "Create an automation")

            val result = mapper.toDomain(dto)

            assertEquals(Message.Role.USER, result.role)
            assertEquals("Create an automation", result.content)
        }

        @Test
        fun `should map assistant message`() {
            val dto = ConversationMessageDto(role = "assistant", content = "I'll help you")

            val result = mapper.toDomain(dto)

            assertEquals(Message.Role.ASSISTANT, result.role)
            assertEquals("I'll help you", result.content)
        }

        @Test
        fun `should handle uppercase role`() {
            val dto = ConversationMessageDto(role = "USER", content = "Hello")

            val result = mapper.toDomain(dto)

            assertEquals(Message.Role.USER, result.role)
        }

        @Test
        fun `should throw for invalid role`() {
            val dto = ConversationMessageDto(role = "system", content = "Hello")

            assertThrows<IllegalArgumentException> {
                mapper.toDomain(dto)
            }
        }
    }

    @Nested
    inner class ToDomainList {
        @Test
        fun `should map list of messages`() {
            val dtos = listOf(
                ConversationMessageDto("user", "Hello"),
                ConversationMessageDto("assistant", "Hi there!"),
                ConversationMessageDto("user", "Create an automation")
            )

            val result = mapper.toDomainList(dtos)

            assertEquals(3, result.size)
            assertEquals(Message.Role.USER, result[0].role)
            assertEquals("Hello", result[0].content)
            assertEquals(Message.Role.ASSISTANT, result[1].role)
            assertEquals("Hi there!", result[1].content)
            assertEquals(Message.Role.USER, result[2].role)
        }

        @Test
        fun `should return empty list for null input`() {
            val result = mapper.toDomainList(null)
            assertTrue(result.isEmpty())
        }

        @Test
        fun `should return empty list for empty input`() {
            val result = mapper.toDomainList(emptyList())
            assertTrue(result.isEmpty())
        }

        @Test
        fun `should preserve message order`() {
            val dtos = listOf(
                ConversationMessageDto("user", "First"),
                ConversationMessageDto("assistant", "Second"),
                ConversationMessageDto("user", "Third")
            )

            val result = mapper.toDomainList(dtos)

            assertEquals("First", result[0].content)
            assertEquals("Second", result[1].content)
            assertEquals("Third", result[2].content)
        }
    }
}
