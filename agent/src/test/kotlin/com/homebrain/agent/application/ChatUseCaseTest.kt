package com.homebrain.agent.application

import com.embabel.agent.core.AgentPlatform
import com.homebrain.agent.domain.conversation.ChatResponse
import com.homebrain.agent.domain.conversation.CodeProposal
import com.homebrain.agent.domain.conversation.FileProposal
import com.homebrain.agent.domain.conversation.Message
import com.homebrain.agent.domain.planning.AutomationResponse
import com.homebrain.agent.domain.planning.UserInput
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for ChatUseCase.
 * 
 * The ChatUseCase now simply delegates to the GOAP-based HomebrainAgent.
 * These tests verify that:
 * 1. UserInput is correctly constructed from the chat message and history
 * 2. AutomationResponse is correctly converted to ChatResponse
 * 
 * Note: The validation and code fixing logic is now tested as part of 
 * HomebrainAgent integration tests, not here.
 */
class ChatUseCaseTest {

    private lateinit var agentPlatform: AgentPlatform
    private lateinit var agentInvoker: AgentInvoker
    private lateinit var useCase: ChatUseCase

    private val validAutomationCode = """
        def on_message(topic, payload, ctx):
            ctx.log("test")
        
        config = {"name": "Test", "enabled": True}
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        agentPlatform = mockk()
        agentInvoker = mockk()
        useCase = ChatUseCase(agentPlatform, agentInvoker)
    }

    @Nested
    inner class ChatWithoutCodeProposal {
        @Test
        fun `should return response when no code proposal`() {
            val automationResponse = AutomationResponse(
                message = "The living room light is currently ON",
                codeProposal = null
            )
            
            every { 
                agentInvoker.invoke(agentPlatform, any<UserInput>(), AutomationResponse::class.java) 
            } returns automationResponse
            
            val result = useCase.chat("What is the status of the living room light?")
            
            assertEquals("The living room light is currently ON", result.message)
            assertNull(result.codeProposal)
        }

        @Test
        fun `should pass conversation history to UserInput`() {
            val history = listOf(
                Message(Message.Role.USER, "Turn on the light"),
                Message(Message.Role.ASSISTANT, "Done!")
            )
            val automationResponse = AutomationResponse(
                message = "It's already on",
                codeProposal = null
            )
            
            val userInputSlot = slot<UserInput>()
            every { 
                agentInvoker.invoke(agentPlatform, capture(userInputSlot), AutomationResponse::class.java) 
            } returns automationResponse
            
            val result = useCase.chat("Is it on?", history)
            
            assertEquals("Is it on?", userInputSlot.captured.message)
            assertEquals(2, userInputSlot.captured.conversationHistory?.size)
            assertEquals("It's already on", result.message)
        }
    }

    @Nested
    inner class ChatWithCodeProposal {
        @Test
        fun `should return response with code proposal`() {
            val proposal = CodeProposal.singleAutomation(
                code = validAutomationCode,
                filename = "test.star",
                summary = "Test automation"
            )
            val automationResponse = AutomationResponse(
                message = "Here's your automation",
                codeProposal = proposal
            )
            
            every { 
                agentInvoker.invoke(agentPlatform, any<UserInput>(), AutomationResponse::class.java) 
            } returns automationResponse
            
            val result = useCase.chat("Create an automation")
            
            assertEquals("Here's your automation", result.message)
            assertNotNull(result.codeProposal)
            assertEquals(validAutomationCode, result.codeProposal!!.code)
        }

        @Test
        fun `should handle multi-file code proposals`() {
            val libraryCode = "def helper(ctx): return True"
            val automationCode = validAutomationCode
            
            val proposal = CodeProposal.withLibrary(
                libraryCode = libraryCode,
                libraryFilename = "lib/helpers.lib.star",
                automationCode = automationCode,
                automationFilename = "use_helpers.star",
                summary = "Library + automation"
            )
            val automationResponse = AutomationResponse(
                message = "Here's your automation with library",
                codeProposal = proposal
            )
            
            every { 
                agentInvoker.invoke(agentPlatform, any<UserInput>(), AutomationResponse::class.java) 
            } returns automationResponse
            
            val result = useCase.chat("Create with library")
            
            assertNotNull(result.codeProposal)
            assertEquals(2, result.codeProposal!!.files.size)
            
            val libs = result.codeProposal!!.getLibraries()
            assertEquals(1, libs.size)
            assertEquals(libraryCode, libs[0].code)
            
            val autos = result.codeProposal!!.getAutomations()
            assertEquals(1, autos.size)
            assertEquals(automationCode, autos[0].code)
        }
    }

    @Nested
    inner class UserInputConstruction {
        @Test
        fun `should create UserInput with message only`() {
            val automationResponse = AutomationResponse(message = "Response", codeProposal = null)
            
            val userInputSlot = slot<UserInput>()
            every { 
                agentInvoker.invoke(agentPlatform, capture(userInputSlot), AutomationResponse::class.java) 
            } returns automationResponse
            
            useCase.chat("Hello")
            
            assertEquals("Hello", userInputSlot.captured.message)
            assertNull(userInputSlot.captured.conversationHistory)
        }

        @Test
        fun `should create UserInput with message and null history`() {
            val automationResponse = AutomationResponse(message = "Response", codeProposal = null)
            
            val userInputSlot = slot<UserInput>()
            every { 
                agentInvoker.invoke(agentPlatform, capture(userInputSlot), AutomationResponse::class.java) 
            } returns automationResponse
            
            useCase.chat("Hello", null)
            
            assertEquals("Hello", userInputSlot.captured.message)
            assertNull(userInputSlot.captured.conversationHistory)
        }

        @Test
        fun `should create UserInput with empty history`() {
            val automationResponse = AutomationResponse(message = "Response", codeProposal = null)
            
            val userInputSlot = slot<UserInput>()
            every { 
                agentInvoker.invoke(agentPlatform, capture(userInputSlot), AutomationResponse::class.java) 
            } returns automationResponse
            
            useCase.chat("Hello", emptyList())
            
            assertEquals("Hello", userInputSlot.captured.message)
            assertEquals(0, userInputSlot.captured.conversationHistory?.size)
        }
    }

    @Nested
    inner class ResponseConversion {
        @Test
        fun `should convert AutomationResponse to ChatResponse`() {
            val proposal = CodeProposal.singleAutomation(
                code = validAutomationCode,
                filename = "test.star",
                summary = "Test"
            )
            val automationResponse = AutomationResponse(
                message = "Test message",
                codeProposal = proposal
            )
            
            every { 
                agentInvoker.invoke(agentPlatform, any<UserInput>(), AutomationResponse::class.java) 
            } returns automationResponse
            
            val result = useCase.chat("Create")
            
            assertTrue(result is ChatResponse)
            assertEquals("Test message", result.message)
            assertEquals(proposal, result.codeProposal)
        }

        @Test
        fun `should handle null codeProposal in conversion`() {
            val automationResponse = AutomationResponse(
                message = "Just a message",
                codeProposal = null
            )
            
            every { 
                agentInvoker.invoke(agentPlatform, any<UserInput>(), AutomationResponse::class.java) 
            } returns automationResponse
            
            val result = useCase.chat("Question?")
            
            assertEquals("Just a message", result.message)
            assertNull(result.codeProposal)
        }
    }
}
