package com.homebrain.agent.api.rest

import com.homebrain.agent.api.dto.LibraryCodeDto
import com.homebrain.agent.api.dto.LibraryModuleDto
import com.homebrain.agent.api.mapper.LibraryMapper
import com.homebrain.agent.application.LibraryUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for library module operations.
 */
@RestController
@RequestMapping("/api/libraries")
class LibraryController(
    private val libraryUseCase: LibraryUseCase,
    private val libraryMapper: LibraryMapper
) {
    /**
     * Get all available library modules.
     */
    @GetMapping
    fun listModules(): List<LibraryModuleDto> {
        val modules = libraryUseCase.getAllModules()
        return libraryMapper.toModuleDtoList(modules)
    }

    /**
     * Get the source code for a specific library module.
     */
    @GetMapping("/{name}")
    fun getModuleCode(@PathVariable name: String): LibraryCodeDto {
        val code = libraryUseCase.getModuleCode(name)
        return libraryMapper.toCodeDto(name, code)
    }
}
