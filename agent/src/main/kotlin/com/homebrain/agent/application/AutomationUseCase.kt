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
 * Represents a file to be deployed (automation or library).
 */
data class FileDeployment(
    val code: String,
    val filename: String,
    val type: FileType
) {
    enum class FileType {
        AUTOMATION,
        LIBRARY
    }

    fun isLibrary(): Boolean = type == FileType.LIBRARY
    fun isAutomation(): Boolean = type == FileType.AUTOMATION
}

/**
 * Result of a deployed file.
 */
data class DeployedFile(
    val filename: String,
    val type: FileDeployment.FileType,
    val isNew: Boolean
)

/**
 * Result of deploying multiple files.
 */
data class MultiDeployResult(
    val deployedFiles: List<DeployedFile>,
    val commit: Commit
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
    private val gitOperations: GitOperations,
    private val codeEmbeddingService: CodeEmbeddingService
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
        
        // Async: update embeddings
        codeEmbeddingService.indexAutomation(automation.id.value, code)
        
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
        
        // Async: update embeddings
        codeEmbeddingService.indexAutomation(id, code)
        
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
        
        val commit = automationRepository.delete(automationId)
        
        // Async: update embeddings
        codeEmbeddingService.removeAutomation(id)
        
        return commit
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

    /**
     * Deploys multiple files (libraries and automations) in a single atomic commit.
     * 
     * Files are deployed in order: libraries first, then automations.
     * This ensures library functions are available when automations are loaded.
     * 
     * @param files List of files to deploy
     * @return Result containing deployed file info and commit
     * @throws IllegalArgumentException if files list is empty
     */
    fun deployMultiple(files: List<FileDeployment>): MultiDeployResult {
        require(files.isNotEmpty()) { "At least one file is required for deployment" }
        
        // Sort files: libraries first, then automations
        val sortedFiles = files.sortedBy { if (it.isLibrary()) 0 else 1 }
        
        val deployedFiles = sortedFiles.map { file ->
            val isNew = !gitOperations.fileExists(file.filename)
            val action = if (isNew) "Add" else "Update"
            
            logger.info { "$action ${file.type.name.lowercase()}: ${file.filename}" }
            
            gitOperations.writeFile(file.filename, file.code)
            gitOperations.stageFile(file.filename)
            
            DeployedFile(
                filename = file.filename,
                type = file.type,
                isNew = isNew
            )
        }
        
        // Create a single commit for all files
        val commitMessage = buildCommitMessage(deployedFiles)
        val commit = gitOperations.commit(commitMessage)
        
        // Async: update embeddings for each deployed file
        sortedFiles.forEach { file ->
            val name = extractNameFromFilename(file.filename)
            if (file.isLibrary()) {
                codeEmbeddingService.indexLibrary(name, file.code)
            } else {
                codeEmbeddingService.indexAutomation(name, file.code)
            }
        }
        
        return MultiDeployResult(
            deployedFiles = deployedFiles,
            commit = commit
        )
    }
    
    /**
     * Extract the name from a filename (without path and extension).
     */
    private fun extractNameFromFilename(filename: String): String {
        return filename
            .substringAfterLast("/")
            .removeSuffix(".lib.star")
            .removeSuffix(".star")
    }

    private fun buildCommitMessage(files: List<DeployedFile>): String {
        val libraries = files.filter { it.type == FileDeployment.FileType.LIBRARY }
        val automations = files.filter { it.type == FileDeployment.FileType.AUTOMATION }
        
        return when {
            libraries.isEmpty() -> {
                val action = if (automations.all { it.isNew }) "Add" else "Update"
                "$action automation: ${automations.joinToString(", ") { it.filename }}"
            }
            automations.isEmpty() -> {
                val action = if (libraries.all { it.isNew }) "Add" else "Update"
                "$action library: ${libraries.joinToString(", ") { it.filename }}"
            }
            else -> {
                "Add ${libraries.joinToString(", ") { it.filename }} and ${automations.joinToString(", ") { it.filename }}"
            }
        }
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
