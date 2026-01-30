package com.homebrain.agent.infrastructure.ai

import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.AchievesGoal
import com.embabel.agent.api.annotation.Agent
import com.embabel.agent.api.common.Ai
import com.homebrain.agent.domain.conversation.FileProposal
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Request for fixing Starlark code.
 */
data class CodeFixRequest(
    val code: String,
    val fileType: FileProposal.FileType,
    val errors: List<String>
)

/**
 * Response containing the fixed code.
 */
data class CodeFixResponse(
    val fixedCode: String
)

/**
 * Embabel-powered agent for fixing Starlark syntax and structural errors.
 * 
 * This agent is invoked when generated code fails validation. It receives
 * the original code and validation errors, and attempts to produce valid code.
 * 
 * The system prompt is loaded from resources/prompts/code-fixer-prompt.md.
 */
@Component
@Agent(description = "Fixes Starlark syntax and structural errors in automation and library code")
class CodeFixerAgent(
    private val promptLoader: PromptLoader
) {
    
    private val systemPrompt: String by lazy {
        promptLoader.load("code-fixer-prompt.md")
    }

    /**
     * Attempts to fix validation errors in Starlark code.
     * 
     * @param request The code, file type, and validation errors
     * @param ai The Embabel AI interface
     * @return Response containing the fixed code
     */
    @AchievesGoal(description = "Fix validation errors in Starlark code to produce valid, loadable code")
    @Action(description = "Fix Starlark syntax and structural errors based on validation feedback")
    fun fixCode(request: CodeFixRequest, ai: Ai): CodeFixResponse {
        logger.info { "Fixing ${request.fileType} code with ${request.errors.size} error(s)" }
        logger.debug { "Errors: ${request.errors}" }

        val userPrompt = buildUserPrompt(request)

        // Use createObject to have the LLM generate the fixed code
        val response = ai.withDefaultLlm()
            .withSystemPrompt(systemPrompt)
            .createObject(userPrompt, CodeFixResponse::class.java)

        logger.debug { "Fixed code length: ${response.fixedCode.length}" }
        return response
    }

    /**
     * Builds the user prompt with the code and errors.
     * Exposed for testing.
     */
    fun buildUserPrompt(request: CodeFixRequest): String {
        val fileTypeString = when (request.fileType) {
            FileProposal.FileType.AUTOMATION -> "automation"
            FileProposal.FileType.LIBRARY -> "library"
        }
        
        val errorsFormatted = request.errors.mapIndexed { index, error ->
            "${index + 1}. $error"
        }.joinToString("\n")

        return """
            |Fix the following $fileTypeString code that has validation errors.
            |
            |## Original Code:
            |```
            |${request.code}
            |```
            |
            |## Validation Errors:
            |$errorsFormatted
            |
            |Please fix ALL the errors and return valid Starlark code.
            |Remember: This is a $fileTypeString file, so apply the appropriate requirements.
        """.trimMargin()
    }
}
