package com.homebrain.agent.application

import com.homebrain.agent.domain.library.LibraryModule
import com.homebrain.agent.infrastructure.engine.EngineClient
import org.springframework.stereotype.Service

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
    private val engineClient: EngineClient
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
}
