package com.homebrain.agent.api.mapper

import com.homebrain.agent.api.dto.AutomationCodeResponse
import com.homebrain.agent.api.dto.AutomationResponse
import com.homebrain.agent.api.dto.CommitInfoDto
import com.homebrain.agent.domain.automation.Automation
import com.homebrain.agent.domain.commit.Commit
import org.springframework.stereotype.Component

/**
 * Maps between automation domain models and API DTOs.
 */
@Component
class AutomationMapper {

    fun toCodeResponse(automation: Automation): AutomationCodeResponse {
        return AutomationCodeResponse(
            id = automation.id.value,
            code = automation.code.value
        )
    }

    fun toResponse(automation: Automation, status: String, commit: Commit? = null): AutomationResponse {
        return AutomationResponse(
            status = status,
            filename = automation.toFilename(),
            commit = commit?.hash
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

    fun toCommitDtoList(commits: List<Commit>): List<CommitInfoDto> {
        return commits.map { toCommitDto(it) }
    }
}
