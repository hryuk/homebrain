package com.homebrain.agent.application

import com.homebrain.agent.domain.library.GlobalStateSchema
import com.homebrain.agent.infrastructure.engine.EngineClient
import org.springframework.stereotype.Service

/**
 * Combined view of global state values and ownership schema.
 */
data class GlobalStateView(
    val values: Map<String, Any>,
    val schema: GlobalStateSchema
)

/**
 * Use case for global state operations.
 */
@Service
class GlobalStateUseCase(
    private val engineClient: EngineClient
) {
    /**
     * Get the combined view of global state values and ownership schema.
     */
    fun getGlobalStateView(): GlobalStateView {
        val values = engineClient.getGlobalState()
        val schema = engineClient.getGlobalStateSchema()
        
        return GlobalStateView(
            values = values,
            schema = schema
        )
    }
}
