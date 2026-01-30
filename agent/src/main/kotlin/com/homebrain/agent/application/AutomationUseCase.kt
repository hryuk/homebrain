package com.homebrain.agent.application

import com.homebrain.agent.domain.automation.Automation
import com.homebrain.agent.domain.automation.AutomationCode
import com.homebrain.agent.domain.automation.AutomationId
import com.homebrain.agent.domain.automation.AutomationRepository
import com.homebrain.agent.domain.commit.Commit
import com.homebrain.agent.infrastructure.engine.EngineClient
import com.homebrain.agent.infrastructure.persistence.GitOperations
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * Result of an automation operation.
 */
data class AutomationResult(
    val automation: Automation,
    val commit: Commit,
    val isNew: Boolean
)

/**
 * Use case for automation CRUD operations.
 * 
 * Orchestrates automation creation, updates, deletion, and retrieval,
 * combining the git-based repository with runtime information from the engine.
 */
@Service
class AutomationUseCase(
    private val automationRepository: AutomationRepository,
    private val engineClient: EngineClient,
    private val gitOperations: GitOperations
) {

    /**
     * Creates a new automation.
     * 
     * @param code The Starlark code
     * @param filename Optional filename (will be generated if not provided)
     * @return The created automation with commit info
     */
    fun create(code: String, filename: String?): AutomationResult {
        val sanitizedFilename = sanitizeFilename(filename ?: "automation")
        val automation = Automation.fromFilename(sanitizedFilename, code)
        
        logger.info { "Creating automation: ${automation.toFilename()}" }
        
        val commit = automationRepository.save(automation)
        return AutomationResult(
            automation = automation.withCommit(commit),
            commit = commit,
            isNew = true
        )
    }

    /**
     * Updates an existing automation.
     * 
     * @param id The automation ID
     * @param code The new Starlark code
     * @return The updated automation with commit info
     * @throws AutomationNotFoundException if the automation doesn't exist
     */
    fun update(id: String, code: String): AutomationResult {
        val automationId = AutomationId(id)
        
        if (!automationRepository.exists(automationId)) {
            throw AutomationNotFoundException(id)
        }

        val automation = Automation(automationId, AutomationCode(code))
        
        logger.info { "Updating automation: ${automation.toFilename()}" }
        
        val commit = automationRepository.save(automation)
        return AutomationResult(
            automation = automation.withCommit(commit),
            commit = commit,
            isNew = false
        )
    }

    /**
     * Gets an automation by ID.
     * 
     * @param id The automation ID
     * @return The automation
     * @throws AutomationNotFoundException if the automation doesn't exist
     */
    fun getById(id: String): Automation {
        val automationId = AutomationId(id)
        return automationRepository.findById(automationId)
            ?: throw AutomationNotFoundException(id)
    }

    /**
     * Deletes an automation.
     * 
     * @param id The automation ID
     * @return The commit for the deletion
     * @throws AutomationNotFoundException if the automation doesn't exist
     */
    fun delete(id: String): Commit {
        val automationId = AutomationId(id)
        
        if (!automationRepository.exists(automationId)) {
            throw AutomationNotFoundException(id)
        }
        
        logger.info { "Deleting automation: ${automationId.toFilename()}" }
        
        return automationRepository.delete(automationId)
    }

    /**
     * Lists all automations with runtime information from the engine.
     * 
     * This combines file-based storage with runtime status from the engine.
     */
    fun listAll(): List<Map<String, Any>> {
        return engineClient.getAutomations()
    }

    /**
     * Gets the git commit history.
     */
    fun getHistory(limit: Int = 50): List<Commit> {
        return gitOperations.getHistory(limit)
    }

    private fun sanitizeFilename(filename: String): String {
        val name = filename.removeSuffix(".star")
            .lowercase()
            .replace(Regex("[^a-z0-9_]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
        return if (name.isEmpty()) "automation" else name
    }
}

/**
 * Exception thrown when an automation is not found.
 */
class AutomationNotFoundException(id: String) : RuntimeException("Automation not found: $id")
