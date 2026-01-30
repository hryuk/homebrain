package com.homebrain.agent.api.mapper

import com.homebrain.agent.domain.library.GlobalStateSchema
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class GlobalStateMapperTest {

    private lateinit var mapper: GlobalStateMapper

    @BeforeEach
    fun setUp() {
        mapper = GlobalStateMapper()
    }

    @Nested
    inner class ToGlobalStateDto {
        @Test
        fun `should combine state values with resolved ownership`() {
            val stateValues = mapOf(
                "presence.living_room.last_motion" to 1706612400L,
                "presence.bedroom.occupied" to true,
                "climate.target_temperature" to 21.5
            )
            val schema = GlobalStateSchema.fromMap(mapOf(
                "presence.*" to listOf("motion_tracker", "occupancy_manager"),
                "climate.*" to listOf("thermostat_control")
            ))

            val result = mapper.toGlobalStateDto(stateValues, schema)

            assertEquals(3, result.entries.size)
            assertTrue(result.timestamp > 0)

            // Check presence.living_room.last_motion
            val livingRoomEntry = result.entries.find { it.key == "presence.living_room.last_motion" }
            assertNotNull(livingRoomEntry)
            assertEquals(1706612400L, livingRoomEntry?.value)
            assertEquals(listOf("motion_tracker", "occupancy_manager"), livingRoomEntry?.owners)

            // Check climate.target_temperature
            val climateEntry = result.entries.find { it.key == "climate.target_temperature" }
            assertNotNull(climateEntry)
            assertEquals(21.5, climateEntry?.value)
            assertEquals(listOf("thermostat_control"), climateEntry?.owners)
        }

        @Test
        fun `should handle state key with no matching schema`() {
            val stateValues = mapOf("orphan.key" to "value")
            val schema = GlobalStateSchema.fromMap(mapOf(
                "presence.*" to listOf("motion_tracker")
            ))

            val result = mapper.toGlobalStateDto(stateValues, schema)

            assertEquals(1, result.entries.size)
            assertEquals("orphan.key", result.entries[0].key)
            assertEquals("value", result.entries[0].value)
            assertTrue(result.entries[0].owners.isEmpty())
        }

        @Test
        fun `should handle empty state`() {
            val stateValues = emptyMap<String, Any>()
            val schema = GlobalStateSchema.fromMap(mapOf(
                "presence.*" to listOf("motion_tracker")
            ))

            val result = mapper.toGlobalStateDto(stateValues, schema)

            assertTrue(result.entries.isEmpty())
            assertTrue(result.timestamp > 0)
        }

        @Test
        fun `should handle empty schema`() {
            val stateValues = mapOf("key" to "value")
            val schema = GlobalStateSchema.empty()

            val result = mapper.toGlobalStateDto(stateValues, schema)

            assertEquals(1, result.entries.size)
            assertTrue(result.entries[0].owners.isEmpty())
        }

        @Test
        fun `should sort entries by key`() {
            val stateValues = mapOf(
                "zebra.key" to 1,
                "alpha.key" to 2,
                "middle.key" to 3
            )
            val schema = GlobalStateSchema.empty()

            val result = mapper.toGlobalStateDto(stateValues, schema)

            assertEquals("alpha.key", result.entries[0].key)
            assertEquals("middle.key", result.entries[1].key)
            assertEquals("zebra.key", result.entries[2].key)
        }

        @Test
        fun `should handle null values in state`() {
            val stateValues = mapOf<String, Any?>("nullable.key" to null)
            val schema = GlobalStateSchema.empty()

            @Suppress("UNCHECKED_CAST")
            val result = mapper.toGlobalStateDto(stateValues as Map<String, Any>, schema)

            assertEquals(1, result.entries.size)
            assertNull(result.entries[0].value)
        }

        @Test
        fun `should handle complex value types`() {
            val stateValues = mapOf(
                "string.key" to "text",
                "int.key" to 42,
                "double.key" to 3.14,
                "bool.key" to true,
                "list.key" to listOf(1, 2, 3),
                "map.key" to mapOf("nested" to "value")
            )
            val schema = GlobalStateSchema.empty()

            val result = mapper.toGlobalStateDto(stateValues, schema)

            assertEquals(6, result.entries.size)
            
            val stringEntry = result.entries.find { it.key == "string.key" }
            assertEquals("text", stringEntry?.value)
            
            val listEntry = result.entries.find { it.key == "list.key" }
            assertEquals(listOf(1, 2, 3), listEntry?.value)
            
            val mapEntry = result.entries.find { it.key == "map.key" }
            assertEquals(mapOf("nested" to "value"), mapEntry?.value)
        }

        @Test
        fun `should resolve multiple matching patterns for same key`() {
            val stateValues = mapOf("presence.living_room.motion" to true)
            val schema = GlobalStateSchema.fromMap(mapOf(
                "presence.*" to listOf("motion_tracker"),
                "presence.living_room.*" to listOf("living_room_controller")
            ))

            val result = mapper.toGlobalStateDto(stateValues, schema)

            assertEquals(1, result.entries.size)
            // Both patterns match, so both automations should be listed
            val owners = result.entries[0].owners
            assertTrue(owners.contains("motion_tracker"))
            assertTrue(owners.contains("living_room_controller"))
        }
    }
}
