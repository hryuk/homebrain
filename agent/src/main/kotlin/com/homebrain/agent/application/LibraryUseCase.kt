package com.homebrain.agent.application

import com.homebrain.agent.domain.commit.Commit
import com.homebrain.agent.domain.library.LibraryModule
import com.homebrain.agent.infrastructure.engine.EngineClient
import com.homebrain.agent.infrastructure.persistence.GitOperations
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * Exception thrown when a library module is not found.
 */
class LibraryModuleNotFoundException(moduleName: String) : 
    RuntimeException("Library module not found: $moduleName")

/**
 * Use case for library module operations.
 */
@Service
class LibraryUseCase(
    private val engineClient: EngineClient,
    private val gitOperations: GitOperations,
    private val codeEmbeddingService: CodeEmbeddingService
) {
    /**
     * Get all available library modules.
     */
    fun getAllModules(): List<LibraryModule> {
        return engineClient.getLibraryModules()
    }

    /**
     * Get the source code for a specific library module.
     * @throws LibraryModuleNotFoundException if the module doesn't exist
     */
    fun getModuleCode(moduleName: String): String {
        val code = engineClient.getLibraryCode(moduleName)
        if (code.isEmpty()) {
            throw LibraryModuleNotFoundException(moduleName)
        }
        return code
    }

    /**
     * Delete a library module.
     * 
     * @param name The library module name (without extension)
     * @return The commit for the deletion
     * @throws LibraryModuleNotFoundException if the module doesn't exist
     */
    fun delete(name: String): Commit {
        val filename = "lib/$name.lib.star"
        
        if (!gitOperations.fileExists(filename)) {
            throw LibraryModuleNotFoundException(name)
        }
        
        logger.info { "Deleting library: $filename" }
        
        gitOperations.deleteFile(filename)
        val commit = gitOperations.commit("Delete library: $filename")
        
        // Async: update embeddings
        codeEmbeddingService.removeLibrary(name)
        
        return commit
    }
}
