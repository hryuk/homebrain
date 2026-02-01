package com.homebrain.agent.infrastructure.ai

import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.AchievesGoal
import com.embabel.agent.api.annotation.Agent
import com.embabel.agent.api.annotation.Condition
import com.embabel.agent.api.annotation.Export
import com.embabel.agent.api.common.OperationContext
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.homebrain.agent.application.CodeEmbeddingService
import com.homebrain.agent.config.HomebrainAgentConfig
import com.homebrain.agent.domain.conversation.CodeProposal
import com.homebrain.agent.domain.conversation.FileProposal
import com.homebrain.agent.domain.planning.AutomationRequirements
import com.homebrain.agent.domain.planning.AutomationResponse
import com.homebrain.agent.domain.planning.ConversationalAnswer
import com.homebrain.agent.domain.planning.ExtractedCode
import com.homebrain.agent.domain.planning.ExtractionResponse
import com.homebrain.agent.domain.planning.GatheredContext
import com.homebrain.agent.domain.planning.GeneratedCode
import com.homebrain.agent.domain.planning.GeneratedCodeResponse
import com.homebrain.agent.domain.planning.ParsedIntent
import com.homebrain.agent.domain.planning.UserInput
import com.homebrain.agent.domain.planning.ValidatedCode
import com.homebrain.agent.domain.planning.ValidationFailure
import com.homebrain.agent.infrastructure.engine.EngineClient
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * GOAP-based smart home automation agent.
 * 
 * This agent uses Embabel's Goal Oriented Action Planning to:
 * 1. Parse user intent
 * 2. Extract automation requirements
 * 3. Gather context (topics, similar code, libraries)
 * 4. Generate automation code
 * 5. Extract reusable helper functions to library modules
 * 6. Validate and fix code in a loop
 * 7. Respond with validated code or conversational answer
 * 
 * The GOAP planner automatically sequences actions based on type dependencies
 * and replans when conditions change (e.g., validation fails).
 */
@Component
@Agent(description = "Smart home automation assistant that can create automations and answer questions about your smart home")
class HomebrainAgent(
    private val mqttLlmTools: MqttLlmTools,
    private val engineClient: EngineClient,
    private val embeddingService: CodeEmbeddingService,
    private val promptLoader: PromptLoader,
    private val config: HomebrainAgentConfig
) {
    companion object {
        // Condition names for GOAP planning
        const val CODE_IS_VALID = "codeIsValid"
        const val CODE_IS_INVALID = "codeIsInvalid"
        const val CAN_STILL_RETRY = "canStillRetry"
        const val MAX_RETRIES_EXHAUSTED = "maxRetriesExhausted"
        const val IS_QUESTION_OR_CHAT = "isQuestionOrChat"
        const val IS_AUTOMATION_REQUEST = "isAutomationRequest"
    }

    // ============================================================================
    // PHASE 1: INTENT PARSING
    // ============================================================================

    @Action(
        description = "Parse user message to classify intent as automation request, question, or chat",
        post = [IS_AUTOMATION_REQUEST, IS_QUESTION_OR_CHAT]
    )
    fun parseIntent(userInput: UserInput, context: OperationContext): ParsedIntent {
        logger.info { "Parsing intent for: ${userInput.message.take(50)}..." }
        
        val classificationPrompt = """
            |Classify this user message about a smart home system.
            |
            |Message: "${userInput.message}"
            |
            |Determine:
            |1. type: AUTOMATION_REQUEST (wants to create/modify automation), QUESTION (asking about the system), or CHAT (general conversation)
            |2. description: Brief description of what they want
            |3. confidence: Your confidence score (0.0 to 1.0)
            |4. entities: Extracted entities like device names, room names, conditions (as a map)
        """.trimMargin()

        return context.ai()
            .withDefaultLlm()
            .createObject(classificationPrompt, ParsedIntent::class.java)
    }

    // ============================================================================
    // PHASE 2: REQUIREMENTS EXTRACTION
    // ============================================================================

    @Action(description = "Extract detailed automation requirements from user request")
    fun extractRequirements(
        userInput: UserInput,
        intent: ParsedIntent,
        context: OperationContext
    ): AutomationRequirements? {
        if (!intent.isAutomationRequest()) {
            logger.debug { "Skipping requirements extraction - not an automation request" }
            return null
        }

        logger.info { "Extracting automation requirements" }

        val prompt = """
            |Extract automation requirements from this user request.
            |
            |User message: "${userInput.message}"
            |Intent: ${intent.description}
            |Entities: ${intent.entities}
            |
            |Extract:
            |1. description: Full description of what the automation should do
            |2. triggers: MQTT topics that should trigger this (e.g., "zigbee2mqtt/motion_sensor")
            |3. actions: What the automation should do (e.g., "turn on kitchen light")
            |4. conditions: Optional conditions (e.g., "only after sunset")
            |5. suggestedName: Snake_case filename for the automation
            |6. needsSchedule: Whether this needs a cron schedule
            |7. schedule: Cron expression if needed (e.g., "0 8 * * *" for 8am daily)
            |8. globalStateWrites: Global state keys this automation needs to write to
        """.trimMargin()

        return context.ai()
            .withDefaultLlm()
            .createObject(prompt, AutomationRequirements::class.java)
    }

    // ============================================================================
    // PHASE 3: CONTEXT GATHERING
    // ============================================================================

    @Action(description = "Gather MQTT topics and similar existing code for context")
    fun gatherContext(
        requirements: AutomationRequirements,
        context: OperationContext
    ): GatheredContext {
        logger.info { "Gathering context for: ${requirements.suggestedName}" }

        val allTopics = mqttLlmTools.getAllTopics()
        
        val relevantTopics = requirements.triggers.flatMap { trigger ->
            val pattern = trigger.replace("zigbee2mqtt/", "").replace("/+", "").replace("/#", "")
            if (pattern.isNotBlank()) mqttLlmTools.searchTopics(pattern) else emptyList()
        }.distinct()

        val similarCode = if (embeddingService.isReady()) {
            embeddingService.search(requirements.description, 5)
        } else {
            emptyList()
        }

        val libraries = mqttLlmTools.getLibraryModules().map { info ->
            com.homebrain.agent.domain.library.LibraryModule(
                name = info.name,
                description = info.description,
                functions = info.functions
            )
        }

        val gatheredContext = GatheredContext(
            availableTopics = allTopics,
            relevantTopics = relevantTopics,
            similarCode = similarCode,
            availableLibraries = libraries
        )

        logger.debug { "Found ${relevantTopics.size} relevant topics, ${similarCode.size} similar code items" }
        
        return gatheredContext
    }

    // ============================================================================
    // PHASE 4: CODE GENERATION
    // ============================================================================

    @Action(description = "Generate Starlark automation code using requirements and context")
    fun generateCode(
        userInput: UserInput,
        requirements: AutomationRequirements,
        gatheredContext: GatheredContext,
        context: OperationContext
    ): GeneratedCode {
        logger.info { "Generating code for: ${requirements.suggestedName}" }
        
        val userPrompt = buildCodeGenerationPrompt(userInput, requirements, gatheredContext)

        // Use tools to gather more context, but don't use conversational system prompt
        // since we need structured JSON output
        val codeGenerationSystemPrompt = """
            |You are a Starlark code generator for Homebrain, an MQTT automation framework.
            |You have access to tools to discover topics and review existing code.
            |
            |After using tools, respond with ONLY a JSON object (no markdown, no explanation):
            |{
            |  "summary": "brief description",
            |  "files": [{"code": "...", "filename": "name.star", "type": "automation"}]
            |}
        """.trimMargin()

        // Use createObject with String to get raw text, then parse JSON ourselves
        // This is more robust than letting Embabel parse JSON directly
        val textResponse = context.ai()
            .withDefaultLlm()
            .withSystemPrompt(codeGenerationSystemPrompt)
            .withToolObject(mqttLlmTools)
            .createObject(userPrompt, String::class.java)

        logger.debug { "Raw LLM response: ${textResponse.take(500)}..." }
        
        // Extract JSON from response (handles markdown code blocks, surrounding text, etc.)
        val response = extractJsonResponse(textResponse)

        val files = response.files.map { file ->
            FileProposal(
                code = file.code,
                filename = file.filename,
                type = FileProposal.FileType.fromString(file.type)
            )
        }

        return GeneratedCode(
            files = files,
            summary = response.summary,
            attempt = 1
        )
    }
    
    /**
     * Extract GeneratedCodeResponse JSON from LLM text output.
     * Handles common issues like markdown code blocks and surrounding text.
     */
    private fun extractJsonResponse(text: String): GeneratedCodeResponse {
        val objectMapper = ObjectMapper().apply {
            findAndRegisterModules()
        }
        
        // Try to find JSON in the response
        val jsonPatterns = listOf(
            // Direct JSON object
            Regex("""(?s)\{.*"summary".*"files".*\}"""),
            // JSON in markdown code block
            Regex("""(?s)```(?:json)?\s*(\{.*"summary".*"files".*\})\s*```"""),
        )
        
        for (pattern in jsonPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val jsonStr = if (match.groups.size > 1 && match.groups[1] != null) {
                    match.groups[1]!!.value
                } else {
                    match.value
                }
                try {
                    return objectMapper.readValue<GeneratedCodeResponse>(jsonStr)
                } catch (e: Exception) {
                    logger.warn { "Failed to parse JSON match: ${e.message}" }
                }
            }
        }
        
        // Last resort: try to parse the whole text
        return try {
            objectMapper.readValue<GeneratedCodeResponse>(text.trim())
        } catch (e: Exception) {
            logger.error { "Failed to extract JSON from response: ${text.take(500)}" }
            throw IllegalStateException("Failed to parse code generation response as JSON: ${e.message}")
        }
    }

    private fun buildCodeGenerationPrompt(
        userInput: UserInput,
        requirements: AutomationRequirements,
        context: GatheredContext
    ): String = buildString {
        appendLine("Generate Starlark automation code for this smart home MQTT request.")
        appendLine()
        appendLine("## User Request")
        appendLine(userInput.message)
        appendLine()
        appendLine("## Extracted Requirements")
        append(requirements.toPromptString())
        appendLine()
        appendLine("## Available Context")
        append(context.toPromptString())
        appendLine()
        appendLine("## Starlark Automation Format")
        appendLine("""
Every automation needs:
1. config dict with: name, description, subscribe (list of MQTT topics), enabled (bool)
2. on_message(topic, payload, ctx) function

Available ctx functions:
- ctx.publish(topic, payload) - payload must be string
- ctx.log(message)
- ctx.json_encode(value), ctx.json_decode(data)
- ctx.get_state(key), ctx.set_state(key, value)
- ctx.get_global(key), ctx.set_global(key, value) - needs global_state_writes in config
- ctx.now() - Unix timestamp
- ctx.lib.modulename.function() - call library functions

Example:
```python
def on_message(topic, payload, ctx):
    data = ctx.json_decode(payload)
    if data.get("state") == "ON":
        ctx.publish("zigbee2mqtt/other_light/set", ctx.json_encode({"state": "ON"}))
        ctx.log("Turned on other light")

config = {
    "name": "Light Sync",
    "description": "Sync lights",
    "subscribe": ["zigbee2mqtt/trigger_light"],
    "enabled": True,
}
```
        """.trim())
        appendLine()
        appendLine()
        appendLine("## Instructions")
        appendLine("1. Use tools to discover real MQTT topics if needed")
        appendLine("2. Reuse existing library functions where applicable")
        appendLine("3. Use descriptive snake_case filenames")
    }

    // ============================================================================
    // PHASE 5: LIBRARY EXTRACTION
    // ============================================================================

    @Action(description = "Extract reusable logic (helper functions and inlined patterns) from automation code into library modules")
    fun extractToLibrary(
        generatedCode: GeneratedCode,
        context: OperationContext
    ): ExtractedCode {
        logger.info { "Analyzing code for library extraction - looking for reusable logic (${generatedCode.files.size} files)" }

        val extractionPrompt = promptLoader.load("library-extractor-prompt.md")

        // Prepare automation files for analysis
        val automationFiles = generatedCode.files.filter { it.isAutomation() }
        
        if (automationFiles.isEmpty()) {
            logger.debug { "No automation files to analyze for extraction" }
            return ExtractedCode.noExtraction(generatedCode, "No automation files to analyze")
        }

        val userPrompt = buildString {
            appendLine("Extract reusable logic from this automation code to library modules.")
            appendLine()
            generatedCode.files.forEach { file ->
                appendLine("## ${file.filename}")
                appendLine("```python")
                appendLine(file.code)
                appendLine("```")
                appendLine()
            }
        }

        val textResponse = context.ai()
            .withDefaultLlm()
            .withSystemPrompt(extractionPrompt)
            .withToolObject(mqttLlmTools)
            .createObject(userPrompt, String::class.java)

        logger.debug { "Extraction response: ${textResponse.take(500)}..." }

        val extractionResult = parseExtractionResponse(textResponse)
        
        if (!extractionResult.extracted) {
            logger.info { "No extraction needed - no reusable logic patterns found" }
            return ExtractedCode.noExtraction(generatedCode, extractionResult.summary)
        }

        logger.info { "Extraction performed: ${extractionResult.summary}" }

        val extractedFiles = extractionResult.files.map { file ->
            FileProposal(
                code = file.code,
                filename = file.filename,
                type = FileProposal.FileType.fromString(file.type)
            )
        }

        return ExtractedCode.withExtraction(
            files = extractedFiles,
            originalSummary = generatedCode.summary,
            attempt = generatedCode.attempt,
            extractionSummary = extractionResult.summary
        )
    }

    /**
     * Parse the extraction response from the LLM.
     */
    private fun parseExtractionResponse(text: String): ExtractionResponse {
        val objectMapper = ObjectMapper().apply {
            findAndRegisterModules()
        }

        // Try to find JSON in the response
        val jsonPatterns = listOf(
            // Direct JSON object
            Regex("""(?s)\{.*"extracted".*"files".*\}"""),
            // JSON in markdown code block
            Regex("""(?s)```(?:json)?\s*(\{.*"extracted".*"files".*\})\s*```"""),
        )

        for (pattern in jsonPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val jsonStr = if (match.groups.size > 1 && match.groups[1] != null) {
                    match.groups[1]!!.value
                } else {
                    match.value
                }
                try {
                    return objectMapper.readValue<ExtractionResponse>(jsonStr)
                } catch (e: Exception) {
                    logger.warn { "Failed to parse extraction JSON match: ${e.message}" }
                }
            }
        }

        // Last resort: try to parse the whole text
        return try {
            objectMapper.readValue<ExtractionResponse>(text.trim())
        } catch (e: Exception) {
            logger.warn { "Failed to parse extraction response, returning no extraction: ${e.message}" }
            ExtractionResponse(extracted = false, summary = "Parse error", files = emptyList())
        }
    }

    // ============================================================================
    // PHASE 6: VALIDATION
    // ============================================================================

    @Action(
        description = "Validate generated Starlark code against the engine",
        post = [CODE_IS_VALID, CODE_IS_INVALID]
    )
    fun validateCode(
        extractedCode: ExtractedCode,
        context: OperationContext
    ): ValidatedCode {
        logger.info { "Validating code (attempt ${extractedCode.attempt}, extraction=${extractedCode.extractionPerformed})" }

        val failures = mutableListOf<ValidationFailure>()

        for (file in extractedCode.files) {
            val type = when (file.type) {
                FileProposal.FileType.AUTOMATION -> "automation"
                FileProposal.FileType.LIBRARY -> "library"
            }

            logger.debug { "Validating ${file.type}: ${file.filename}" }
            val result = engineClient.validateCode(file.code, type)

            if (!result.valid) {
                logger.warn { "Validation failed for ${file.filename}: ${result.errors}" }
                val failure = ValidationFailure(file, result.errors)
                failures.add(failure)
                context += failure  // Add to blackboard for fix action
            }
        }

        if (failures.isEmpty()) {
            logger.info { "All ${extractedCode.files.size} files passed validation" }
        } else {
            logger.info { "${failures.size} file(s) failed validation" }
        }

        // Explicitly add to context so codeIsValid condition can find it
        // (return value alone may not be on blackboard in time for condition evaluation)
        val validatedCode = ValidatedCode.fromExtractedCode(extractedCode)
        context += validatedCode
        return validatedCode
    }

    @Condition(name = CODE_IS_VALID)
    fun codeIsValid(context: OperationContext): Boolean {
        // Must have ValidatedCode AND no validation failures
        // This prevents GOAP from skipping validation entirely
        val hasValidatedCode = context.objectsOfType(ValidatedCode::class.java).isNotEmpty()
        val noValidationFailures = context.objectsOfType(ValidationFailure::class.java).isEmpty()
        return hasValidatedCode && noValidationFailures
    }

    @Condition(name = CODE_IS_INVALID)
    fun codeIsInvalid(context: OperationContext): Boolean {
        return context.objectsOfType(ValidationFailure::class.java).isNotEmpty()
    }

    // ============================================================================
    // PHASE 7: CODE FIXING
    // ============================================================================

    @Action(
        description = "Fix Starlark validation errors using LLM",
        pre = [CODE_IS_INVALID, CAN_STILL_RETRY],
        canRerun = true
    )
    fun fixInvalidCode(
        validatedCode: ValidatedCode,
        context: OperationContext
    ): ExtractedCode {
        val failures = context.objectsOfType(ValidationFailure::class.java)
        logger.info { "Fixing ${failures.size} invalid file(s) (attempt ${validatedCode.attempt + 1})" }

        val fixedFiles = validatedCode.files.map { file ->
            val failure = failures.find { it.file.filename == file.filename }
            if (failure != null) {
                fixFile(file, failure.errors, context)
            } else {
                file
            }
        }

        // Return ExtractedCode so it feeds back into validateCode
        return ExtractedCode(
            files = fixedFiles,
            summary = validatedCode.summary,
            attempt = validatedCode.attempt + 1,
            extractionPerformed = false,
            extractionSummary = "From fix attempt"
        )
    }

    private fun fixFile(file: FileProposal, errors: List<String>, context: OperationContext): FileProposal {
        logger.debug { "Fixing ${file.filename} with ${errors.size} error(s)" }

        val fixPrompt = promptLoader.load("code-fixer-prompt.md")
        
        val userPrompt = """
            |Fix the following ${file.type.toApiString()} code that has validation errors.
            |
            |## Original Code:
            |```
            |${file.code}
            |```
            |
            |## Validation Errors:
            |${errors.mapIndexed { i, e -> "${i + 1}. $e" }.joinToString("\n")}
            |
            |Return the fixed code in the fixedCode field.
        """.trimMargin()

        data class FixResponse(val fixedCode: String)
        
        val response = context.ai()
            .withDefaultLlm()
            .withSystemPrompt(fixPrompt)
            .createObject(userPrompt, FixResponse::class.java)

        return file.copy(code = response.fixedCode)
    }

    @Condition(name = CAN_STILL_RETRY)
    fun canStillRetry(context: OperationContext): Boolean {
        val validatedCode = context.objectsOfType(ValidatedCode::class.java).firstOrNull()
        return validatedCode != null && validatedCode.attempt < config.maxFixAttempts
    }

    @Condition(name = MAX_RETRIES_EXHAUSTED)
    fun maxRetriesExhausted(context: OperationContext): Boolean {
        val validatedCode = context.objectsOfType(ValidatedCode::class.java).firstOrNull()
        return validatedCode != null && validatedCode.attempt >= config.maxFixAttempts
    }

    // ============================================================================
    // PHASE 8: CONVERSATIONAL ANSWER
    // ============================================================================

    @Action(
        description = "Answer questions about the smart home or have a conversation",
        pre = [IS_QUESTION_OR_CHAT]
    )
    fun answerQuestion(
        userInput: UserInput,
        intent: ParsedIntent,
        context: OperationContext
    ): ConversationalAnswer? {
        // Double-check: if we're in an automation request, return null to prevent this action
        if (intent.isAutomationRequest()) {
            logger.debug { "Skipping answerQuestion - this is an automation request" }
            return null
        }
        
        logger.info { "Answering ${intent.type}: ${intent.description}" }

        val prompt = """
            |Answer this ${intent.type.name.lowercase()} about a smart home system.
            |
            |User: ${userInput.message}
            |
            |You have access to tools to query the system.
            |Use these tools to provide accurate, helpful information.
            |Be conversational and friendly.
        """.trimMargin()

        val response = context.ai()
            .withDefaultLlm()
            .withToolObject(mqttLlmTools)
            .createObject(prompt, String::class.java)

        return ConversationalAnswer(response)
    }

    @Condition(name = IS_QUESTION_OR_CHAT)
    fun isQuestionOrChat(context: OperationContext): Boolean {
        val intent = context.objectsOfType(ParsedIntent::class.java).firstOrNull()
        return intent != null && !intent.isAutomationRequest()
    }

    @Condition(name = IS_AUTOMATION_REQUEST)
    fun isAutomationRequest(context: OperationContext): Boolean {
        val intent = context.objectsOfType(ParsedIntent::class.java).firstOrNull()
        return intent != null && intent.isAutomationRequest()
    }

    // ============================================================================
    // GOALS: Final response actions
    // ============================================================================

    @AchievesGoal(
        description = "Respond with validated automation code ready for deployment",
        export = Export(
            remote = true,
            name = "createAutomation",
            startingInputTypes = [UserInput::class]
        )
    )
    @Action(pre = [CODE_IS_VALID, IS_AUTOMATION_REQUEST])
    fun respondWithAutomation(
        validatedCode: ValidatedCode,
        context: OperationContext
    ): AutomationResponse {
        logger.info { "Goal achieved: responding with validated automation" }

        val proposal = CodeProposal(
            summary = validatedCode.summary,
            files = validatedCode.files
        )

        return AutomationResponse(
            message = "Here's your automation: ${validatedCode.summary}\n\n" +
                "I've generated ${validatedCode.files.size} file(s). " +
                "Click 'Deploy' to activate this automation.",
            codeProposal = proposal
        )
    }

    @AchievesGoal(description = "Respond when code generation failed after max retry attempts")
    @Action(pre = [MAX_RETRIES_EXHAUSTED, IS_AUTOMATION_REQUEST])
    fun respondWithFailure(
        validatedCode: ValidatedCode,
        context: OperationContext
    ): AutomationResponse {
        val failures = context.objectsOfType(ValidationFailure::class.java)
        val errorSummary = failures.flatMap { it.errors }.take(3).joinToString("; ")
        
        logger.warn { "Goal achieved (failure): max retries exhausted with errors: $errorSummary" }

        return AutomationResponse.maxRetriesExhausted(validatedCode.attempt).copy(
            message = "I attempted to generate an automation for your request, but " +
                "encountered issues that I couldn't resolve automatically after ${validatedCode.attempt} attempts. " +
                "The last errors were: $errorSummary\n\n" +
                "Could you try again with more specific requirements?"
        )
    }

    @AchievesGoal(
        description = "Respond conversationally to questions and chat",
        export = Export(
            remote = true,
            name = "answerQuestion",
            startingInputTypes = [UserInput::class]
        )
    )
    @Action(pre = [IS_QUESTION_OR_CHAT])
    fun respondConversationally(answer: ConversationalAnswer): AutomationResponse {
        logger.info { "Goal achieved: responding conversationally" }
        return AutomationResponse.fromAnswer(answer)
    }
}
