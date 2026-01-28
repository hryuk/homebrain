package com.homebrain.agent.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

private val logger = KotlinLogging.logger {}

@Service
class EngineProxyService(
    @Value("\${app.engine.url}")
    private val engineUrl: String
) {
    private val webClient = WebClient.builder()
        .baseUrl(engineUrl)
        .build()

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
