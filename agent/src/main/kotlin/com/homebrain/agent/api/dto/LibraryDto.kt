package com.homebrain.agent.api.dto

/**
 * Response DTO for listing library modules.
 */
data class LibraryModuleDto(
    val name: String,
    val description: String,
    val functions: List<String>
)

/**
 * Response DTO for retrieving library module source code.
 */
data class LibraryCodeDto(
    val name: String,
    val code: String
)
