package com.homebrain.agent.api.dto

/**
 * DTO for a single global state entry with its value and ownership information.
 */
data class GlobalStateEntryDto(
    val key: String,
    val value: Any?,
    val owners: List<String>
)

/**
 * Response DTO for the global state with combined values and ownership.
 */
data class GlobalStateDto(
    val entries: List<GlobalStateEntryDto>,
    val timestamp: Long
)
