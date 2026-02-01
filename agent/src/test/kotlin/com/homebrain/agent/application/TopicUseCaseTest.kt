package com.homebrain.agent.application

import com.homebrain.agent.domain.topic.Topic
import com.homebrain.agent.domain.topic.TopicRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class TopicUseCaseTest {

    private lateinit var topicRepository: TopicRepository
    private lateinit var useCase: TopicUseCase

    @BeforeEach
    fun setUp() {
        topicRepository = mockk()
        useCase = TopicUseCase(topicRepository)
    }

    @Nested
    inner class GetAllTopics {
        @Test
        fun `should return all topics from repository`() {
            val topics = listOf(
                Topic.fromPath("zigbee2mqtt/living_room_light/state"),
                Topic.fromPath("zigbee2mqtt/kitchen_sensor/temperature"),
                Topic.fromPath("homeassistant/switch/bedroom/state")
            )
            every { topicRepository.findAll() } returns topics

            val result = useCase.getAllTopics()

            assertEquals(3, result.size)
            assertEquals("zigbee2mqtt/living_room_light/state", result[0].path.value)
            verify { topicRepository.findAll() }
        }

        @Test
        fun `should return empty list when no topics discovered`() {
            every { topicRepository.findAll() } returns emptyList()

            val result = useCase.getAllTopics()

            assertTrue(result.isEmpty())
        }

        @Test
        fun `should return topics with last values`() {
            val topics = listOf(
                Topic.fromPath("zigbee2mqtt/sensor/temperature", "22.5")
            )
            every { topicRepository.findAll() } returns topics

            val result = useCase.getAllTopics()

            assertEquals(1, result.size)
            assertEquals("22.5", result[0].lastValue)
        }
    }

    @Nested
    inner class SearchTopics {
        @Test
        fun `should return matching topics`() {
            val topics = listOf(
                Topic.fromPath("zigbee2mqtt/living_room_light/state"),
                Topic.fromPath("zigbee2mqtt/living_room_sensor/temperature")
            )
            every { topicRepository.search("living_room") } returns topics

            val result = useCase.searchTopics("living_room")

            assertEquals(2, result.size)
            assertTrue(result.all { it.path.value.contains("living_room") })
            verify { topicRepository.search("living_room") }
        }

        @Test
        fun `should return empty list when no matches`() {
            every { topicRepository.search("nonexistent") } returns emptyList()

            val result = useCase.searchTopics("nonexistent")

            assertTrue(result.isEmpty())
        }

        @Test
        fun `should search with partial keyword`() {
            val topics = listOf(
                Topic.fromPath("zigbee2mqtt/light_1/state"),
                Topic.fromPath("zigbee2mqtt/light_2/state")
            )
            every { topicRepository.search("light") } returns topics

            val result = useCase.searchTopics("light")

            assertEquals(2, result.size)
        }

        @Test
        fun `should handle empty keyword`() {
            val allTopics = listOf(
                Topic.fromPath("topic1"),
                Topic.fromPath("topic2")
            )
            every { topicRepository.search("") } returns allTopics

            val result = useCase.searchTopics("")

            assertEquals(2, result.size)
        }
    }
}
