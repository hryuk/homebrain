package com.homebrain.agent.api.mapper

import com.homebrain.agent.api.dto.CommitInfoDto
import com.homebrain.agent.api.dto.LibraryCodeDto
import com.homebrain.agent.api.dto.LibraryModuleDto
import com.homebrain.agent.domain.commit.Commit
import com.homebrain.agent.domain.library.LibraryModule
import org.springframework.stereotype.Component

/**
 * Maps between library domain models and API DTOs.
 */
@Component
class LibraryMapper {

    fun toModuleDto(module: LibraryModule): LibraryModuleDto {
        return LibraryModuleDto(
            name = module.name,
            description = module.description,
            functions = module.functions
        )
    }

    fun toModuleDtoList(modules: List<LibraryModule>): List<LibraryModuleDto> {
        return modules.map { toModuleDto(it) }
    }

    fun toCodeDto(moduleName: String, code: String): LibraryCodeDto {
        return LibraryCodeDto(
            name = moduleName,
            code = code
        )
    }

    fun toCommitDto(commit: Commit): CommitInfoDto {
        return CommitInfoDto(
            hash = commit.hash,
            message = commit.message,
            author = commit.author,
            date = commit.timestamp
        )
    }
}
