package com.homebrain.agent.api.dto

import java.time.Instant

/**
 * DTO for MQTT topic information.
 */
data class TopicDto(
    val path: String,
    val lastValue: String? = null,
    val lastSeen: Instant? = null
)
