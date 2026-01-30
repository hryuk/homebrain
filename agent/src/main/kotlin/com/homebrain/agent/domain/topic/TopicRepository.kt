package com.homebrain.agent.domain.topic

/**
 * Port interface for topic discovery.
 * 
 * Topics are discovered by the engine at runtime. This repository
 * provides a way to query the known topics.
 */
interface TopicRepository {
    /**
     * Gets all discovered topics.
     */
    fun findAll(): List<Topic>

    /**
     * Searches topics by keyword.
     */
    fun search(keyword: String): List<Topic>

    /**
     * Finds topics matching a pattern (supports MQTT wildcards).
     */
    fun findByPattern(pattern: TopicPath): List<Topic>
}
