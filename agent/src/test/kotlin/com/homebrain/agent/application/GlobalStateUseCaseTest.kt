package com.homebrain.agent.application

import com.homebrain.agent.domain.library.GlobalStateSchema
import com.homebrain.agent.infrastructure.engine.EngineClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class GlobalStateUseCaseTest {

    private lateinit var engineClient: EngineClient
    private lateinit var useCase: GlobalStateUseCase

    @BeforeEach
    fun setUp() {
        engineClient = mockk()
        useCase = GlobalStateUseCase(engineClient)
    }

    @Nested
    inner class GetGlobalStateView {
        @Test
        fun `should return combined state values and schema`() {
            val stateValues = mapOf(
                "presence.living_room.motion" to true,
                "climate.temperature" to 21.5
            )
            val schema = GlobalStateSchema.fromMap(mapOf(
                "presence.*" to listOf("motion_tracker"),
                "climate.*" to listOf("thermostat")
            ))
            
            every { engineClient.getGlobalState() } returns stateValues
            every { engineClient.getGlobalStateSchema() } returns schema

            val result = useCase.getGlobalStateView()

            assertEquals(stateValues, result.values)
            assertEquals(schema, result.schema)
            verify { engineClient.getGlobalState() }
            verify { engineClient.getGlobalStateSchema() }
        }

        @Test
        fun `should handle empty state`() {
            every { engineClient.getGlobalState() } returns emptyMap()
            every { engineClient.getGlobalStateSchema() } returns GlobalStateSchema.empty()

            val result = useCase.getGlobalStateView()

            assertTrue(result.values.isEmpty())
            assertTrue(result.schema.keyPatterns.isEmpty())
        }

        @Test
        fun `should handle state with values but no schema`() {
            val stateValues = mapOf("orphan.key" to "value")
            
            every { engineClient.getGlobalState() } returns stateValues
            every { engineClient.getGlobalStateSchema() } returns GlobalStateSchema.empty()

            val result = useCase.getGlobalStateView()

            assertEquals(1, result.values.size)
            assertTrue(result.schema.keyPatterns.isEmpty())
        }

        @Test
        fun `should handle schema with patterns but no values`() {
            val schema = GlobalStateSchema.fromMap(mapOf(
                "presence.*" to listOf("motion_tracker")
            ))
            
            every { engineClient.getGlobalState() } returns emptyMap()
            every { engineClient.getGlobalStateSchema() } returns schema

            val result = useCase.getGlobalStateView()

            assertTrue(result.values.isEmpty())
            assertEquals(1, result.schema.keyPatterns.size)
        }

        @Test
        fun `should handle complex value types`() {
            val stateValues: Map<String, Any> = mapOf(
                "list.key" to listOf(1, 2, 3),
                "map.key" to mapOf("nested" to "value")
            )
            
            every { engineClient.getGlobalState() } returns stateValues
            every { engineClient.getGlobalStateSchema() } returns GlobalStateSchema.empty()

            val result = useCase.getGlobalStateView()

            assertEquals(2, result.values.size)
            assertEquals(listOf(1, 2, 3), result.values["list.key"])
            assertEquals(mapOf("nested" to "value"), result.values["map.key"])
        }
    }
}
