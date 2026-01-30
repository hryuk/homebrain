package com.homebrain.agent.application

import com.embabel.agent.core.AgentPlatform
import com.homebrain.agent.domain.conversation.ChatResponse
import com.homebrain.agent.domain.conversation.CodeProposal
import com.homebrain.agent.domain.conversation.FileProposal
import com.homebrain.agent.domain.conversation.Message
import com.homebrain.agent.domain.validation.ValidationResult
import com.homebrain.agent.infrastructure.ai.ChatAgentRequest
import com.homebrain.agent.infrastructure.ai.CodeFixRequest
import com.homebrain.agent.infrastructure.ai.CodeFixResponse
import com.homebrain.agent.infrastructure.engine.EngineClient
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ChatUseCaseTest {

    private lateinit var agentPlatform: AgentPlatform
    private lateinit var engineClient: EngineClient
    private lateinit var agentInvoker: AgentInvoker
    private lateinit var useCase: ChatUseCase

    private val validAutomationCode = """
        def on_message(topic, payload, ctx):
            ctx.log("test")
        
        config = {"name": "Test", "enabled": True}
    """.trimIndent()

    private val invalidAutomationCode = """
        def on_message(topic, payload, ctx)
            ctx.log("test")
        
        config = {"name": "Test"}
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        agentPlatform = mockk()
        engineClient = mockk()
        agentInvoker = mockk()
        useCase = ChatUseCase(agentPlatform, engineClient, agentInvoker)
    }

    @Nested
    inner class ChatWithoutCodeProposal {
        @Test
        fun `should return response as-is when no code proposal`() {
            val response = ChatResponse(
                message = "The living room light is currently ON",
                codeProposal = null
            )
            
            every { 
                agentInvoker.invoke(agentPlatform, any<ChatAgentRequest>(), ChatResponse::class.java) 
            } returns response
            
            val result = useCase.chat("What is the status of the living room light?")
            
            assertEquals("The living room light is currently ON", result.message)
            assertNull(result.codeProposal)
            
            // Should not call engine
            verify(exactly = 0) { engineClient.validateCode(any(), any()) }
        }

        @Test
        fun `should preserve conversation history`() {
            val history = listOf(
                Message(Message.Role.USER, "Turn on the light"),
                Message(Message.Role.ASSISTANT, "Done!")
            )
            val response = ChatResponse(
                message = "It's already on",
                codeProposal = null
            )
            
            every { 
                agentInvoker.invoke(agentPlatform, any<ChatAgentRequest>(), ChatResponse::class.java) 
            } returns response
            
            val result = useCase.chat("Is it on?", history)
            
            assertEquals("It's already on", result.message)
        }
    }

    @Nested
    inner class ValidationPassesFirstTime {
        @Test
        fun `should return response when code is valid`() {
            val proposal = CodeProposal.singleAutomation(
                code = validAutomationCode,
                filename = "test.star",
                summary = "Test automation"
            )
            val response = ChatResponse(
                message = "Here's your automation",
                codeProposal = proposal
            )
            
            every { 
                agentInvoker.invoke(agentPlatform, any<ChatAgentRequest>(), ChatResponse::class.java) 
            } returns response
            every { engineClient.validateCode(validAutomationCode, "automation") } returns ValidationResult.success()
            
            val result = useCase.chat("Create an automation")
            
            assertEquals("Here's your automation", result.message)
            assertNotNull(result.codeProposal)
            assertEquals(validAutomationCode, result.codeProposal!!.code)
            
            verify(exactly = 1) { engineClient.validateCode(any(), any()) }
        }

        @Test
        fun `should validate library and automation separately`() {
            val libraryCode = "def helper(ctx): return True"
            val automationCode = validAutomationCode
            
            val proposal = CodeProposal.withLibrary(
                libraryCode = libraryCode,
                libraryFilename = "lib/helpers.lib.star",
                automationCode = automationCode,
                automationFilename = "use_helpers.star",
                summary = "Library + automation"
            )
            val response = ChatResponse(
                message = "Here's your automation with library",
                codeProposal = proposal
            )
            
            every { 
                agentInvoker.invoke(agentPlatform, any<ChatAgentRequest>(), ChatResponse::class.java) 
            } returns response
            every { engineClient.validateCode(libraryCode, "library") } returns ValidationResult.success()
            every { engineClient.validateCode(automationCode, "automation") } returns ValidationResult.success()
            
            val result = useCase.chat("Create with library")
            
            assertNotNull(result.codeProposal)
            assertEquals(2, result.codeProposal!!.files.size)
            
            verify { engineClient.validateCode(libraryCode, "library") }
            verify { engineClient.validateCode(automationCode, "automation") }
        }
    }

    @Nested
    inner class ValidationFailsAndFixerSucceeds {
        @Test
        fun `should fix code when validation fails and return fixed code`() {
            val proposal = CodeProposal.singleAutomation(
                code = invalidAutomationCode,
                filename = "test.star",
                summary = "Test automation"
            )
            val response = ChatResponse(
                message = "Here's your automation",
                codeProposal = proposal
            )
            
            every { 
                agentInvoker.invoke(agentPlatform, any<ChatAgentRequest>(), ChatResponse::class.java) 
            } returns response
            
            // First validation fails
            every { engineClient.validateCode(invalidAutomationCode, "automation") } returns 
                ValidationResult.failure(listOf("syntax error: missing colon"))
            
            // Fixer returns valid code
            every { 
                agentInvoker.invoke(agentPlatform, any<CodeFixRequest>(), CodeFixResponse::class.java) 
            } returns CodeFixResponse(validAutomationCode)
            
            // Second validation succeeds
            every { engineClient.validateCode(validAutomationCode, "automation") } returns ValidationResult.success()
            
            val result = useCase.chat("Create an automation")
            
            assertNotNull(result.codeProposal)
            assertEquals(validAutomationCode, result.codeProposal!!.code)
            
            verify(exactly = 2) { engineClient.validateCode(any(), any()) }
            verify(exactly = 1) { agentInvoker.invoke(agentPlatform, any<CodeFixRequest>(), CodeFixResponse::class.java) }
        }

        @Test
        fun `should pass errors to fixer agent`() {
            val errors = listOf("error 1", "error 2")
            val proposal = CodeProposal.singleAutomation(
                code = invalidAutomationCode,
                filename = "test.star",
                summary = "Test"
            )
            val response = ChatResponse(message = "Here", codeProposal = proposal)
            
            every { 
                agentInvoker.invoke(agentPlatform, any<ChatAgentRequest>(), ChatResponse::class.java) 
            } returns response
            
            every { engineClient.validateCode(invalidAutomationCode, "automation") } returns 
                ValidationResult.failure(errors)
            
            val fixRequestSlot = slot<CodeFixRequest>()
            every { 
                agentInvoker.invoke(agentPlatform, capture(fixRequestSlot), CodeFixResponse::class.java) 
            } returns CodeFixResponse(validAutomationCode)
            every { engineClient.validateCode(validAutomationCode, "automation") } returns ValidationResult.success()
            
            useCase.chat("Create")
            
            assertEquals(invalidAutomationCode, fixRequestSlot.captured.code)
            assertEquals(errors, fixRequestSlot.captured.errors)
            assertEquals(FileProposal.FileType.AUTOMATION, fixRequestSlot.captured.fileType)
        }
    }

    @Nested
    inner class MaxRetriesExceeded {
        @Test
        fun `should return without code proposal after max retries`() {
            val proposal = CodeProposal.singleAutomation(
                code = invalidAutomationCode,
                filename = "test.star",
                summary = "Test"
            )
            val response = ChatResponse(message = "Here", codeProposal = proposal)
            
            every { 
                agentInvoker.invoke(agentPlatform, any<ChatAgentRequest>(), ChatResponse::class.java) 
            } returns response
            
            // All validations fail
            every { engineClient.validateCode(any(), any()) } returns 
                ValidationResult.failure(listOf("persistent error"))
            
            // Fixer keeps returning invalid code
            every { 
                agentInvoker.invoke(agentPlatform, any<CodeFixRequest>(), CodeFixResponse::class.java) 
            } returns CodeFixResponse("still broken code")
            
            val result = useCase.chat("Create")
            
            // Should return without code proposal
            assertNull(result.codeProposal)
            assertTrue(result.message.contains("unable", ignoreCase = true) || 
                      result.message.contains("couldn't", ignoreCase = true) || 
                      result.message.contains("tried", ignoreCase = true) || 
                      result.message.contains("attempt", ignoreCase = true),
                "Message should indicate failure: ${result.message}")
            
            // Should have attempted MAX_FIX_ATTEMPTS times
            // Initial validation + MAX_FIX_ATTEMPTS validations after each fix + final validation
            verify(exactly = ChatUseCase.MAX_FIX_ATTEMPTS + 1) { engineClient.validateCode(any(), any()) }
            verify(exactly = ChatUseCase.MAX_FIX_ATTEMPTS) { 
                agentInvoker.invoke(agentPlatform, any<CodeFixRequest>(), CodeFixResponse::class.java) 
            }
        }

        @Test
        fun `failure message should suggest user clarify requirements`() {
            val proposal = CodeProposal.singleAutomation(
                code = invalidAutomationCode,
                filename = "test.star",
                summary = "Test"
            )
            val response = ChatResponse(message = "Here", codeProposal = proposal)
            
            every { 
                agentInvoker.invoke(agentPlatform, any<ChatAgentRequest>(), ChatResponse::class.java) 
            } returns response
            
            every { engineClient.validateCode(any(), any()) } returns 
                ValidationResult.failure(listOf("error"))
            every { 
                agentInvoker.invoke(agentPlatform, any<CodeFixRequest>(), CodeFixResponse::class.java) 
            } returns CodeFixResponse("broken")
            
            val result = useCase.chat("Create")
            
            // Message should guide user to try again
            assertTrue(
                result.message.contains("try again", ignoreCase = true) ||
                result.message.contains("clearer", ignoreCase = true) ||
                result.message.contains("specific", ignoreCase = true) ||
                result.message.contains("requirements", ignoreCase = true),
                "Message should guide user: ${result.message}"
            )
        }
    }

    @Nested
    inner class MultipleFilesValidation {
        @Test
        fun `should fix only the invalid file`() {
            val libraryCode = "def helper(): pass"  // Will be valid
            val invalidAutoCode = "def on_message(t,p,c):\n  pass\nconfig={}"  // Will be invalid
            
            val proposal = CodeProposal.withLibrary(
                libraryCode = libraryCode,
                libraryFilename = "lib/test.lib.star",
                automationCode = invalidAutoCode,
                automationFilename = "auto.star",
                summary = "Test"
            )
            val response = ChatResponse(message = "Here", codeProposal = proposal)
            
            every { 
                agentInvoker.invoke(agentPlatform, any<ChatAgentRequest>(), ChatResponse::class.java) 
            } returns response
            
            // Library passes, automation fails
            every { engineClient.validateCode(libraryCode, "library") } returns ValidationResult.success()
            every { engineClient.validateCode(invalidAutoCode, "automation") } returns 
                ValidationResult.failure(listOf("missing config fields"))
            
            // Fixer fixes automation
            val fixedAutoCode = validAutomationCode
            every { 
                agentInvoker.invoke(agentPlatform, match<CodeFixRequest> { it.fileType == FileProposal.FileType.AUTOMATION }, CodeFixResponse::class.java) 
            } returns CodeFixResponse(fixedAutoCode)
            every { engineClient.validateCode(fixedAutoCode, "automation") } returns ValidationResult.success()
            
            val result = useCase.chat("Create")
            
            assertNotNull(result.codeProposal)
            
            // Library should be unchanged
            val libs = result.codeProposal!!.getLibraries()
            assertEquals(1, libs.size)
            assertEquals(libraryCode, libs[0].code)
            
            // Automation should be fixed
            val autos = result.codeProposal!!.getAutomations()
            assertEquals(1, autos.size)
            assertEquals(fixedAutoCode, autos[0].code)
        }
    }

    @Nested
    inner class EngineClientErrors {
        @Test
        fun `should treat validation client error as validation failure`() {
            val proposal = CodeProposal.singleAutomation(
                code = validAutomationCode,
                filename = "test.star",
                summary = "Test"
            )
            val response = ChatResponse(message = "Here", codeProposal = proposal)
            
            every { 
                agentInvoker.invoke(agentPlatform, any<ChatAgentRequest>(), ChatResponse::class.java) 
            } returns response
            
            // Engine client returns error (network issue, etc)
            every { engineClient.validateCode(any(), any()) } returns 
                ValidationResult.failure("Validation request failed: Connection refused")
            
            // Should try to fix
            every { 
                agentInvoker.invoke(agentPlatform, any<CodeFixRequest>(), CodeFixResponse::class.java) 
            } returns CodeFixResponse(validAutomationCode)
            
            useCase.chat("Create")
            
            // Should have tried to fix (even though code might be valid, we can't verify)
            verify(atLeast = 1) { 
                agentInvoker.invoke(agentPlatform, any<CodeFixRequest>(), CodeFixResponse::class.java) 
            }
        }
    }
}
