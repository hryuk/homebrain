package com.homebrain.agent.infrastructure.engine

import com.homebrain.agent.domain.library.LibraryModule
import com.homebrain.agent.domain.library.GlobalStateSchema
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

    /**
     * Gets all library modules from the engine.
     */
    fun getLibraryModules(): List<LibraryModule> {
        logger.debug { "Fetching library modules from engine" }
        return try {
            val response = webClient.get()
                .uri("/library")
                .retrieve()
                .bodyToMono<List<Map<String, Any>>>()
                .block() ?: emptyList()

            response.map { module ->
                LibraryModule.create(
                    name = module["name"] as? String ?: "",
                    description = module["description"] as? String ?: "",
                    functions = (module["functions"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                )
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch library modules from engine" }
            emptyList()
        }
    }

    /**
     * Gets the source code for a specific library module.
     */
    fun getLibraryCode(moduleName: String): String {
        logger.debug { "Fetching library code for module: $moduleName" }
        return try {
            webClient.get()
                .uri("/library/$moduleName")
                .retrieve()
                .bodyToMono<String>()
                .block() ?: ""
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch library code for module: $moduleName" }
            ""
        }
    }

    /**
     * Gets the global state schema showing which automations can write which keys.
     */
    fun getGlobalStateSchema(): GlobalStateSchema {
        logger.debug { "Fetching global state schema from engine" }
        return try {
            val response = webClient.get()
                .uri("/global-state-schema")
                .retrieve()
                .bodyToMono<Map<String, List<String>>>()
                .block() ?: emptyMap()

            GlobalStateSchema.fromMap(response)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch global state schema from engine" }
            GlobalStateSchema.empty()
        }
    }

    /**
     * Gets the current global state values.
     */
    fun getGlobalState(): Map<String, Any> {
        logger.debug { "Fetching global state from engine" }
        return try {
            webClient.get()
                .uri("/global-state")
                .retrieve()
                .bodyToMono<Map<String, Any>>()
                .block() ?: emptyMap()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch global state from engine" }
            emptyMap()
        }
    }
}
