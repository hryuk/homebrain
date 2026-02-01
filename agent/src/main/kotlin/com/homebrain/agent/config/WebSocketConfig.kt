package com.homebrain.agent.config

import com.homebrain.agent.infrastructure.websocket.LogsWebSocketHandler
import com.homebrain.agent.infrastructure.websocket.MqttWebSocketHandler
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val logsWebSocketHandler: LogsWebSocketHandler,
    private val mqttWebSocketHandler: MqttWebSocketHandler
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(logsWebSocketHandler, "/ws/logs")
            .setAllowedOrigins("*")
        registry.addHandler(mqttWebSocketHandler, "/ws/mqtt")
            .setAllowedOrigins("*")
    }
}
