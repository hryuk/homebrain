package com.homebrain.agent.api.dto

import java.time.Instant

/**
 * Request DTO for creating/updating an automation.
 */
data class AutomationRequest(
    val filename: String? = null,
    val code: String
)

/**
 * Response DTO for automation operations.
 */
data class AutomationResponse(
    val status: String,
    val filename: String,
    val commit: String? = null
)

/**
 * Response DTO for retrieving automation code.
 */
data class AutomationCodeResponse(
    val id: String,
    val code: String
)

/**
 * DTO for git commit information.
 */
data class CommitInfoDto(
    val hash: String,
    val message: String,
    val author: String,
    val date: Instant
)

/**
 * Request DTO for deploying multiple files (library + automation).
 */
data class MultiDeployRequest(
    val files: List<FileDeployRequestDto>
)

/**
 * DTO for a single file in a multi-deploy request.
 */
data class FileDeployRequestDto(
    val code: String,
    val filename: String,
    val type: String  // "automation" or "library"
)

/**
 * Response DTO for multi-file deployment.
 */
data class MultiDeployResponse(
    val status: String,
    val files: List<DeployedFileDto>,
    val commit: String
)

/**
 * DTO for a deployed file result.
 */
data class DeployedFileDto(
    val filename: String,
    val type: String,
    val isNew: Boolean
)
