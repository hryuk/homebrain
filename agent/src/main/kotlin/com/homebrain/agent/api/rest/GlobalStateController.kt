package com.homebrain.agent.api.rest

import com.homebrain.agent.api.dto.GlobalStateDto
import com.homebrain.agent.api.mapper.GlobalStateMapper
import com.homebrain.agent.application.GlobalStateUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for global state operations.
 */
@RestController
@RequestMapping("/api/global-state")
class GlobalStateController(
    private val globalStateUseCase: GlobalStateUseCase,
    private val globalStateMapper: GlobalStateMapper
) {
    /**
     * Get the current global state with values and ownership information.
     */
    @GetMapping
    fun getGlobalState(): GlobalStateDto {
        val view = globalStateUseCase.getGlobalStateView()
        return globalStateMapper.toGlobalStateDto(view.values, view.schema)
    }
}
