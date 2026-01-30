package com.homebrain.agent.domain.topic

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class TopicTest {

    @Nested
    inner class Construction {
        @Test
        fun `should create topic with path only`() {
            val topic = Topic(TopicPath("zigbee2mqtt/device/state"))
            assertEquals("zigbee2mqtt/device/state", topic.path.value)
            assertNull(topic.lastValue)
            assertNull(topic.lastSeen)
        }

        @Test
        fun `should create topic with all properties`() {
            val now = Instant.now()
            val topic = Topic(
                path = TopicPath("zigbee2mqtt/device/state"),
                lastValue = """{"state": "ON"}""",
                lastSeen = now
            )
            assertEquals("zigbee2mqtt/device/state", topic.path.value)
            assertEquals("""{"state": "ON"}""", topic.lastValue)
            assertEquals(now, topic.lastSeen)
        }
    }

    @Nested
    inner class ContainsKeyword {
        @Test
        fun `should find keyword in path`() {
            val topic = Topic.fromPath("zigbee2mqtt/living_room_light/state")
            assertTrue(topic.containsKeyword("living_room"))
        }

        @Test
        fun `should be case insensitive`() {
            val topic = Topic.fromPath("zigbee2mqtt/Living_Room_Light/state")
            assertTrue(topic.containsKeyword("living_room"))
            assertTrue(topic.containsKeyword("LIVING_ROOM"))
        }

        @Test
        fun `should return false when keyword not found`() {
            val topic = Topic.fromPath("zigbee2mqtt/kitchen_light/state")
            assertFalse(topic.containsKeyword("bedroom"))
        }

        @Test
        fun `should find partial matches`() {
            val topic = Topic.fromPath("zigbee2mqtt/temperature_sensor/state")
            assertTrue(topic.containsKeyword("temp"))
            assertTrue(topic.containsKeyword("sensor"))
        }
    }

    @Nested
    inner class DeviceName {
        @Test
        fun `should extract device name from standard topic`() {
            val topic = Topic.fromPath("zigbee2mqtt/living_room_light/state")
            assertEquals("living_room_light", topic.deviceName())
        }

        @Test
        fun `should return null for single segment topic`() {
            val topic = Topic.fromPath("status")
            assertNull(topic.deviceName())
        }

        @Test
        fun `should return second segment for multi-segment topic`() {
            val topic = Topic.fromPath("home/floor1/room1/sensor")
            assertEquals("floor1", topic.deviceName())
        }

        @Test
        fun `should handle two segment topic`() {
            val topic = Topic.fromPath("homeassistant/switch")
            assertEquals("switch", topic.deviceName())
        }
    }

    @Nested
    inner class FactoryMethods {
        @Test
        fun `fromPath should create topic from string`() {
            val topic = Topic.fromPath("zigbee2mqtt/device/state")
            assertEquals("zigbee2mqtt/device/state", topic.path.value)
            assertNull(topic.lastValue)
        }

        @Test
        fun `fromPath with lastValue should create topic with value`() {
            val topic = Topic.fromPath("zigbee2mqtt/device/state", """{"brightness": 100}""")
            assertEquals("zigbee2mqtt/device/state", topic.path.value)
            assertEquals("""{"brightness": 100}""", topic.lastValue)
        }

        @Test
        fun `fromPath with null lastValue should create topic without value`() {
            val topic = Topic.fromPath("zigbee2mqtt/device/state", null)
            assertEquals("zigbee2mqtt/device/state", topic.path.value)
            assertNull(topic.lastValue)
        }
    }

    @Nested
    inner class Equality {
        @Test
        fun `topics with same path should be equal`() {
            val topic1 = Topic.fromPath("zigbee2mqtt/device/state")
            val topic2 = Topic.fromPath("zigbee2mqtt/device/state")
            assertEquals(topic1, topic2)
        }

        @Test
        fun `topics with different lastValue should not be equal`() {
            val topic1 = Topic.fromPath("zigbee2mqtt/device/state", "value1")
            val topic2 = Topic.fromPath("zigbee2mqtt/device/state", "value2")
            assertNotEquals(topic1, topic2)
        }

        @Test
        fun `topics with different paths should not be equal`() {
            val topic1 = Topic.fromPath("zigbee2mqtt/device1/state")
            val topic2 = Topic.fromPath("zigbee2mqtt/device2/state")
            assertNotEquals(topic1, topic2)
        }
    }
}
