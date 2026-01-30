package com.homebrain.agent.application

import com.embabel.agent.api.invocation.AgentInvocation
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
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * Interface for invoking agents.
 * Allows mocking in tests without static mocking.
 */
interface AgentInvoker {
    fun <T : Any> invoke(platform: AgentPlatform, request: Any, responseType: Class<T>): T
}

/**
 * Default implementation using Embabel's AgentInvocation.
 */
class EmbabelAgentInvoker : AgentInvoker {
    override fun <T : Any> invoke(platform: AgentPlatform, request: Any, responseType: Class<T>): T {
        val invocation = AgentInvocation.create(platform, responseType)
        return invocation.invoke(request)
    }
}

/**
 * Use case for chat interactions.
 * 
 * Orchestrates the conversation flow, using the Embabel agent platform
 * to process user messages and generate responses.
 * 
 * When code is generated, it is validated against the engine before being
 * returned to the user. If validation fails, a fixer agent attempts to
 * correct the code (up to MAX_FIX_ATTEMPTS times).
 */
@Service
class ChatUseCase(
    private val agentPlatform: AgentPlatform,
    private val engineClient: EngineClient,
    private val agentInvoker: AgentInvoker = EmbabelAgentInvoker()
) {
    companion object {
        /**
         * Maximum number of times to attempt fixing invalid code.
         */
        const val MAX_FIX_ATTEMPTS = 3
    }

    /**
     * Processes a user message and returns a response.
     * 
     * If the response includes a code proposal, the code is validated against
     * the engine. Invalid code is automatically fixed (up to MAX_FIX_ATTEMPTS).
     * If fixing fails, the response is returned without a code proposal.
     * 
     * @param message The user's message
     * @param conversationHistory Previous messages in the conversation
     * @return The chat response, optionally including a validated code proposal
     */
    fun chat(message: String, conversationHistory: List<Message>? = null): ChatResponse {
        logger.info { "Processing chat message: ${message.take(50)}..." }

        val request = ChatAgentRequest(
            message = message,
            conversationHistory = conversationHistory
        )

        val response = agentInvoker.invoke(agentPlatform, request, ChatResponse::class.java)

        // If no code proposal, return as-is
        val codeProposal = response.codeProposal ?: return response

        // Validate and potentially fix the code
        val validatedProposal = validateAndFixProposal(codeProposal)

        return if (validatedProposal != null) {
            response.copy(codeProposal = validatedProposal)
        } else {
            // All fix attempts failed
            logger.warn { "Failed to generate valid code after $MAX_FIX_ATTEMPTS attempts" }
            ChatResponse(
                message = "I attempted to generate an automation for your request, but " +
                    "encountered issues that I couldn't resolve automatically. " +
                    "Could you try again with more specific requirements? " +
                    "For example, specify exact topic names, device names, or conditions.",
                codeProposal = null
            )
        }
    }

    /**
     * Validates a code proposal and attempts to fix any invalid files.
     * 
     * @param proposal The code proposal to validate
     * @return The validated (and potentially fixed) proposal, or null if fixing failed
     */
    private fun validateAndFixProposal(proposal: CodeProposal): CodeProposal? {
        var currentFiles = proposal.files.toMutableList()
        var attempts = 0

        while (attempts < MAX_FIX_ATTEMPTS) {
            val validationResults = validateAllFiles(currentFiles)
            
            // Check if all files are valid
            val invalidFiles = validationResults.filter { !it.value.valid }
            if (invalidFiles.isEmpty()) {
                logger.info { "All ${currentFiles.size} file(s) passed validation" }
                return CodeProposal(
                    summary = proposal.summary,
                    files = currentFiles
                )
            }

            logger.info { "Validation failed for ${invalidFiles.size} file(s), attempt ${attempts + 1}/$MAX_FIX_ATTEMPTS" }

            // Try to fix each invalid file
            currentFiles = currentFiles.map { file ->
                val result = validationResults[file]
                if (result != null && !result.valid) {
                    fixFile(file, result.errors)
                } else {
                    file
                }
            }.toMutableList()

            attempts++
        }

        // Final validation attempt
        val finalResults = validateAllFiles(currentFiles)
        val stillInvalid = finalResults.filter { !it.value.valid }
        
        return if (stillInvalid.isEmpty()) {
            CodeProposal(summary = proposal.summary, files = currentFiles)
        } else {
            logger.warn { "Code still invalid after $MAX_FIX_ATTEMPTS fix attempts: ${stillInvalid.values.flatMap { it.errors }}" }
            null
        }
    }

    /**
     * Validates all files in a list and returns validation results.
     */
    private fun validateAllFiles(files: List<FileProposal>): Map<FileProposal, ValidationResult> {
        return files.associateWith { file ->
            val type = when (file.type) {
                FileProposal.FileType.AUTOMATION -> "automation"
                FileProposal.FileType.LIBRARY -> "library"
            }
            
            logger.debug { "Validating ${file.type} file: ${file.filename}" }
            engineClient.validateCode(file.code, type)
        }
    }

    /**
     * Attempts to fix a file using the code fixer agent.
     */
    private fun fixFile(file: FileProposal, errors: List<String>): FileProposal {
        logger.info { "Attempting to fix ${file.type} file: ${file.filename}" }
        logger.debug { "Errors to fix: $errors" }

        val fixRequest = CodeFixRequest(
            code = file.code,
            fileType = file.type,
            errors = errors
        )

        val fixResponse = agentInvoker.invoke(agentPlatform, fixRequest, CodeFixResponse::class.java)

        return FileProposal(
            code = fixResponse.fixedCode,
            filename = file.filename,
            type = file.type
        )
    }
}
