package com.homebrain.agent.api.rest

import com.homebrain.agent.api.mapper.TopicMapper
import com.homebrain.agent.application.TopicUseCase
import com.homebrain.agent.domain.topic.Topic
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class TopicsControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var topicUseCase: TopicUseCase
    private val topicMapper = TopicMapper()

    @BeforeEach
    fun setUp() {
        topicUseCase = mockk()
        val controller = TopicsController(topicUseCase, topicMapper)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Nested
    inner class ListTopics {
        @Test
        fun `should return list of topic paths`() {
            val topics = listOf(
                Topic.fromPath("zigbee2mqtt/device1/state"),
                Topic.fromPath("zigbee2mqtt/device2/state"),
                Topic.fromPath("homeassistant/status")
            )
            every { topicUseCase.getAllTopics() } returns topics

            mockMvc.perform(get("/api/topics"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0]").value("zigbee2mqtt/device1/state"))
                .andExpect(jsonPath("$[1]").value("zigbee2mqtt/device2/state"))
                .andExpect(jsonPath("$[2]").value("homeassistant/status"))
        }

        @Test
        fun `should return empty list when no topics`() {
            every { topicUseCase.getAllTopics() } returns emptyList()

            mockMvc.perform(get("/api/topics"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray)
                .andExpect(jsonPath("$").isEmpty)
        }

        @Test
        fun `should return topics in order`() {
            val topics = listOf(
                Topic.fromPath("c/topic"),
                Topic.fromPath("a/topic"),
                Topic.fromPath("b/topic")
            )
            every { topicUseCase.getAllTopics() } returns topics

            mockMvc.perform(get("/api/topics"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0]").value("c/topic"))
                .andExpect(jsonPath("$[1]").value("a/topic"))
                .andExpect(jsonPath("$[2]").value("b/topic"))
        }
    }
}
