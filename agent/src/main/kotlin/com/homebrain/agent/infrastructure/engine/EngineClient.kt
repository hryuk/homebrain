package com.homebrain.agent.infrastructure.engine

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

private val logger = KotlinLogging.logger {}

/**
 * HTTP client for communicating with the Go automation engine.
 * 
 * The engine provides runtime information about automations,
 * discovered MQTT topics, and logs.
 */
@Component
class EngineClient(
    @Value("\${app.engine.url}")
    private val engineUrl: String
) {
    private val webClient = WebClient.builder()
        .baseUrl(engineUrl)
        .build()

    /**
     * Gets all discovered MQTT topic paths from the engine.
     */
    fun getTopics(): List<String> {
        logger.debug { "Fetching topics from engine" }
        return try {
            webClient.get()
                .uri("/topics")
                .retrieve()
                .bodyToMono<List<String>>()
                .block() ?: emptyList()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch topics from engine" }
            emptyList()
        }
    }

    /**
     * Gets runtime automation information from the engine.
     * Returns untyped maps as the engine schema may vary.
     */
    fun getAutomations(): List<Map<String, Any>> {
        logger.debug { "Fetching automations from engine" }
        return try {
            webClient.get()
                .uri("/automations")
                .retrieve()
                .bodyToMono<List<Map<String, Any>>>()
                .block() ?: emptyList()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch automations from engine" }
            emptyList()
        }
    }

    /**
     * Gets recent log entries from the engine.
     */
    fun getLogs(): List<Map<String, Any>> {
        logger.debug { "Fetching logs from engine" }
        return try {
            webClient.get()
                .uri("/logs")
                .retrieve()
                .bodyToMono<List<Map<String, Any>>>()
                .block() ?: emptyList()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch logs from engine" }
            emptyList()
        }
    }
}
