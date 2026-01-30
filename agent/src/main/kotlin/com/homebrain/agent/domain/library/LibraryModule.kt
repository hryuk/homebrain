package com.homebrain.agent.domain.library

/**
 * Represents a library module with reusable functions.
 *
 * Library modules are pure function collections stored in the
 * automations/lib/ directory with .lib.star extension.
 */
data class LibraryModule(
    val name: String,
    val description: String,
    val functions: List<String>
) {
    init {
        require(name.isNotBlank()) { "Library module name cannot be blank" }
    }

    companion object {
        fun create(name: String, description: String = "", functions: List<String> = emptyList()): LibraryModule {
            return LibraryModule(
                name = name.trim(),
                description = description.trim(),
                functions = functions
            )
        }
    }
}
