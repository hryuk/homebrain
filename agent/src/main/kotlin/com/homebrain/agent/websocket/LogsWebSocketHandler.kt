package com.homebrain.agent.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.homebrain.agent.service.EngineProxyService
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

@Component
class LogsWebSocketHandler(
    private val engineProxyService: EngineProxyService,
    private val objectMapper: ObjectMapper
) : TextWebSocketHandler() {

    private val sessions = ConcurrentHashMap<String, WebSocketSession>()
    private var previousLogs: List<Map<String, Any>> = emptyList()
    private lateinit var scheduler: ScheduledExecutorService

    @PostConstruct
    fun init() {
        scheduler = Executors.newSingleThreadScheduledExecutor()
        scheduler.scheduleWithFixedDelay(
            { pollLogs() },
            1,
            1,
            TimeUnit.SECONDS
        )
        logger.info { "Logs WebSocket handler initialized" }
    }

    @PreDestroy
    fun shutdown() {
        scheduler.shutdown()
        logger.info { "Logs WebSocket handler shutdown" }
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        sessions[session.id] = session
        logger.info { "WebSocket client connected: ${session.id}" }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        sessions.remove(session.id)
        logger.info { "WebSocket client disconnected: ${session.id}" }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        // Keep-alive or ping handling - no specific action needed
        logger.debug { "Received message from ${session.id}: ${message.payload}" }
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        logger.warn(exception) { "WebSocket transport error for session ${session.id}" }
        sessions.remove(session.id)
    }

    private fun pollLogs() {
        if (sessions.isEmpty()) {
            return
        }

        try {
            val currentLogs = engineProxyService.getLogs()
            val newLogs = findNewLogs(previousLogs, currentLogs)

            if (newLogs.isNotEmpty()) {
                val message = objectMapper.writeValueAsString(newLogs)
                broadcast(message)
                previousLogs = currentLogs
            }
        } catch (e: Exception) {
            logger.warn(e) { "Error polling logs" }
        }
    }

    private fun findNewLogs(
        old: List<Map<String, Any>>,
        new: List<Map<String, Any>>
    ): List<Map<String, Any>> {
        if (old.isEmpty()) {
            return new
        }
        if (new.size <= old.size) {
            return emptyList()
        }
        return new.subList(old.size, new.size)
    }

    private fun broadcast(message: String) {
        val textMessage = TextMessage(message)
        val deadSessions = mutableListOf<String>()

        sessions.forEach { (id, session) ->
            try {
                if (session.isOpen) {
                    session.sendMessage(textMessage)
                } else {
                    deadSessions.add(id)
                }
            } catch (e: Exception) {
                logger.warn(e) { "Error sending message to session $id" }
                deadSessions.add(id)
            }
        }

        deadSessions.forEach { sessions.remove(it) }
    }
}
