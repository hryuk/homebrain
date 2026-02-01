package com.homebrain.agent.application

import com.homebrain.agent.infrastructure.engine.EngineClient
import org.springframework.stereotype.Service

/**
 * Use case for MQTT message retrieval.
 * 
 * Provides access to captured MQTT messages from the engine for visualization.
 */
@Service
class MqttMessageUseCase(
    private val engineClient: EngineClient
) {

    /**
     * Gets recent MQTT messages.
     */
    fun getMessages(): List<Map<String, Any>> {
        return engineClient.getMessages()
    }
}
