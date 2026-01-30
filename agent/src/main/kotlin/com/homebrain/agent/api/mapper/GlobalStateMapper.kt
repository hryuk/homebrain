package com.homebrain.agent.api.mapper

import com.homebrain.agent.api.dto.GlobalStateDto
import com.homebrain.agent.api.dto.GlobalStateEntryDto
import com.homebrain.agent.domain.library.GlobalStateSchema
import org.springframework.stereotype.Component

/**
 * Maps global state values and schema to API DTOs.
 */
@Component
class GlobalStateMapper {

    /**
     * Combines state values with resolved ownership information from the schema.
     * Entries are sorted by key for consistent ordering.
     */
    fun toGlobalStateDto(
        stateValues: Map<String, Any>,
        schema: GlobalStateSchema
    ): GlobalStateDto {
        val entries = stateValues.entries
            .sortedBy { it.key }
            .map { (key, value) ->
                GlobalStateEntryDto(
                    key = key,
                    value = value,
                    owners = schema.getWritersForKey(key)
                )
            }

        return GlobalStateDto(
            entries = entries,
            timestamp = System.currentTimeMillis()
        )
    }
}
