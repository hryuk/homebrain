package com.homebrain.agent.api.rest

import com.homebrain.agent.application.MqttMessageUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class MqttController(
    private val mqttMessageUseCase: MqttMessageUseCase
) {

    @GetMapping("/mqtt/messages")
    fun getMessages(): ResponseEntity<List<Map<String, Any>>> {
        val messages = mqttMessageUseCase.getMessages()
        return ResponseEntity.ok(messages)
    }
}
