package com.homebrain.agent.infrastructure.persistence

import com.homebrain.agent.domain.automation.Automation
import com.homebrain.agent.domain.automation.AutomationCode
import com.homebrain.agent.domain.automation.AutomationId
import com.homebrain.agent.domain.automation.AutomationRepository
import com.homebrain.agent.domain.commit.Commit
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Repository

private val logger = KotlinLogging.logger {}

/**
 * Git-based implementation of AutomationRepository.
 * 
 * Stores automations as .star files in a git repository,
 * with each save/delete operation creating a commit.
 */
@Repository
class GitAutomationRepository(
    private val gitOperations: GitOperations
) : AutomationRepository {

    override fun findById(id: AutomationId): Automation? {
        val filename = id.toFilename()
        
        if (!gitOperations.fileExists(filename)) {
            return null
        }

        return try {
            val code = gitOperations.readFile(filename)
            Automation(id, AutomationCode(code))
        } catch (e: Exception) {
            logger.error(e) { "Failed to read automation: $filename" }
            null
        }
    }

    override fun findAllIds(): List<AutomationId> {
        return gitOperations.listFiles("star")
            .map { AutomationId.fromFilename(it) }
    }

    override fun save(automation: Automation): Commit {
        val filename = automation.toFilename()
        val isNew = !gitOperations.fileExists(filename)
        val action = if (isNew) "Add" else "Update"

        logger.info { "$action automation: $filename" }

        gitOperations.writeFile(filename, automation.code.value)
        gitOperations.stageFile(filename)
        return gitOperations.commit("$action automation: $filename")
    }

    override fun delete(id: AutomationId): Commit {
        val filename = id.toFilename()
        logger.info { "Delete automation: $filename" }

        gitOperations.deleteFile(filename)
        return gitOperations.commit("Delete automation: $filename")
    }

    override fun exists(id: AutomationId): Boolean {
        return gitOperations.fileExists(id.toFilename())
    }
}
