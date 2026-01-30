package com.homebrain.agent.domain.automation

import com.homebrain.agent.domain.commit.Commit

/**
 * Port interface for automation persistence.
 * 
 * This is the domain's contract for how automations are stored and retrieved.
 * The implementation details (Git, filesystem, etc.) are in the infrastructure layer.
 */
interface AutomationRepository {
    /**
     * Finds an automation by its ID.
     * @return the automation, or null if not found
     */
    fun findById(id: AutomationId): Automation?

    /**
     * Lists all automation IDs.
     */
    fun findAllIds(): List<AutomationId>

    /**
     * Saves an automation (create or update).
     * @return the commit created for this save operation
     */
    fun save(automation: Automation): Commit

    /**
     * Deletes an automation by its ID.
     * @return the commit created for the deletion
     */
    fun delete(id: AutomationId): Commit

    /**
     * Checks if an automation exists.
     */
    fun exists(id: AutomationId): Boolean
}
