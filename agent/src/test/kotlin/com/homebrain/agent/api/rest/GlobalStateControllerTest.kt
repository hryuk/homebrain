package com.homebrain.agent.api.rest

import com.homebrain.agent.api.mapper.GlobalStateMapper
import com.homebrain.agent.application.GlobalStateUseCase
import com.homebrain.agent.application.GlobalStateView
import com.homebrain.agent.domain.library.GlobalStateSchema
import com.homebrain.agent.exception.HomebrainExceptionHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class GlobalStateControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var globalStateUseCase: GlobalStateUseCase
    private val globalStateMapper = GlobalStateMapper()

    @BeforeEach
    fun setUp() {
        globalStateUseCase = mockk()
        val controller = GlobalStateController(globalStateUseCase, globalStateMapper)
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(HomebrainExceptionHandler())
            .build()
    }

    @Nested
    inner class GetGlobalState {
        @Test
        fun `should return global state with values and ownership`() {
            val stateValues = mapOf(
                "presence.living_room.motion" to true,
                "climate.temperature" to 21.5
            )
            val schema = GlobalStateSchema.fromMap(mapOf(
                "presence.*" to listOf("motion_tracker"),
                "climate.*" to listOf("thermostat")
            ))
            val view = GlobalStateView(stateValues, schema)
            
            every { globalStateUseCase.getGlobalStateView() } returns view

            mockMvc.perform(get("/api/global-state"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.entries").isArray)
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.timestamp").isNumber)
                // Entries are sorted by key
                .andExpect(jsonPath("$.entries[0].key").value("climate.temperature"))
                .andExpect(jsonPath("$.entries[0].value").value(21.5))
                .andExpect(jsonPath("$.entries[0].owners[0]").value("thermostat"))
                .andExpect(jsonPath("$.entries[1].key").value("presence.living_room.motion"))
                .andExpect(jsonPath("$.entries[1].value").value(true))
                .andExpect(jsonPath("$.entries[1].owners[0]").value("motion_tracker"))

            verify { globalStateUseCase.getGlobalStateView() }
        }

        @Test
        fun `should return empty entries when no state`() {
            val view = GlobalStateView(emptyMap(), GlobalStateSchema.empty())
            
            every { globalStateUseCase.getGlobalStateView() } returns view

            mockMvc.perform(get("/api/global-state"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.entries").isArray)
                .andExpect(jsonPath("$.entries").isEmpty)
                .andExpect(jsonPath("$.timestamp").isNumber)
        }

        @Test
        fun `should handle state with no matching schema`() {
            val stateValues = mapOf("orphan.key" to "value")
            val view = GlobalStateView(stateValues, GlobalStateSchema.empty())
            
            every { globalStateUseCase.getGlobalStateView() } returns view

            mockMvc.perform(get("/api/global-state"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.entries[0].key").value("orphan.key"))
                .andExpect(jsonPath("$.entries[0].value").value("value"))
                .andExpect(jsonPath("$.entries[0].owners").isEmpty)
        }

        @Test
        fun `should handle complex value types`() {
            val stateValues: Map<String, Any> = mapOf(
                "list.key" to listOf(1, 2, 3),
                "nested.key" to mapOf("inner" to "value")
            )
            val view = GlobalStateView(stateValues, GlobalStateSchema.empty())
            
            every { globalStateUseCase.getGlobalStateView() } returns view

            mockMvc.perform(get("/api/global-state"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.entries[0].key").value("list.key"))
                .andExpect(jsonPath("$.entries[0].value[0]").value(1))
                .andExpect(jsonPath("$.entries[0].value[1]").value(2))
                .andExpect(jsonPath("$.entries[0].value[2]").value(3))
                .andExpect(jsonPath("$.entries[1].key").value("nested.key"))
                .andExpect(jsonPath("$.entries[1].value.inner").value("value"))
        }

        @Test
        fun `should handle multiple owners for same key`() {
            val stateValues = mapOf("presence.room.motion" to true)
            val schema = GlobalStateSchema.fromMap(mapOf(
                "presence.*" to listOf("motion_tracker"),
                "presence.room.*" to listOf("room_controller")
            ))
            val view = GlobalStateView(stateValues, schema)
            
            every { globalStateUseCase.getGlobalStateView() } returns view

            mockMvc.perform(get("/api/global-state"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.entries[0].owners.length()").value(2))
        }
    }
}
