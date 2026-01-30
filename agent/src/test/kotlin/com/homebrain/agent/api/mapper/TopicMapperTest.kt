package com.homebrain.agent.api.mapper

import com.homebrain.agent.domain.topic.Topic
import com.homebrain.agent.domain.topic.TopicPath
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class TopicMapperTest {

    private lateinit var mapper: TopicMapper

    @BeforeEach
    fun setUp() {
        mapper = TopicMapper()
    }

    @Nested
    inner class ToDto {
        @Test
        fun `should map topic with all properties`() {
            val now = Instant.now()
            val topic = Topic(
                path = TopicPath("zigbee2mqtt/device/state"),
                lastValue = """{"state": "ON"}""",
                lastSeen = now
            )

            val result = mapper.toDto(topic)

            assertEquals("zigbee2mqtt/device/state", result.path)
            assertEquals("""{"state": "ON"}""", result.lastValue)
            assertEquals(now, result.lastSeen)
        }

        @Test
        fun `should map topic with null lastValue`() {
            val topic = Topic(
                path = TopicPath("homeassistant/status"),
                lastValue = null,
                lastSeen = null
            )

            val result = mapper.toDto(topic)

            assertEquals("homeassistant/status", result.path)
            assertNull(result.lastValue)
            assertNull(result.lastSeen)
        }

        @Test
        fun `should preserve exact topic path`() {
            val topic = Topic.fromPath("home/floor1/room2/sensor/temperature")

            val result = mapper.toDto(topic)

            assertEquals("home/floor1/room2/sensor/temperature", result.path)
        }
    }

    @Nested
    inner class ToDtoList {
        @Test
        fun `should map list of topics`() {
            val topics = listOf(
                Topic.fromPath("zigbee2mqtt/device1/state", "ON"),
                Topic.fromPath("zigbee2mqtt/device2/state", "OFF"),
                Topic.fromPath("homeassistant/status")
            )

            val result = mapper.toDtoList(topics)

            assertEquals(3, result.size)
            assertEquals("zigbee2mqtt/device1/state", result[0].path)
            assertEquals("ON", result[0].lastValue)
            assertEquals("zigbee2mqtt/device2/state", result[1].path)
            assertEquals("OFF", result[1].lastValue)
            assertEquals("homeassistant/status", result[2].path)
            assertNull(result[2].lastValue)
        }

        @Test
        fun `should return empty list for empty input`() {
            val result = mapper.toDtoList(emptyList())
            assertTrue(result.isEmpty())
        }
    }

    @Nested
    inner class ToPathList {
        @Test
        fun `should extract paths as strings`() {
            val topics = listOf(
                Topic.fromPath("zigbee2mqtt/light/state"),
                Topic.fromPath("zigbee2mqtt/sensor/temperature"),
                Topic.fromPath("homeassistant/status")
            )

            val result = mapper.toPathList(topics)

            assertEquals(3, result.size)
            assertEquals("zigbee2mqtt/light/state", result[0])
            assertEquals("zigbee2mqtt/sensor/temperature", result[1])
            assertEquals("homeassistant/status", result[2])
        }

        @Test
        fun `should return empty list for empty input`() {
            val result = mapper.toPathList(emptyList())
            assertTrue(result.isEmpty())
        }

        @Test
        fun `should preserve topic order`() {
            val topics = listOf(
                Topic.fromPath("c/topic"),
                Topic.fromPath("a/topic"),
                Topic.fromPath("b/topic")
            )

            val result = mapper.toPathList(topics)

            assertEquals(listOf("c/topic", "a/topic", "b/topic"), result)
        }
    }
}
