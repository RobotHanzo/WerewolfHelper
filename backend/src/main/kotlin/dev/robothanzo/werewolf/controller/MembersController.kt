package dev.robothanzo.werewolf.controller

import dev.robothanzo.werewolf.controller.dto.MemberDto
import dev.robothanzo.werewolf.controller.dto.MembersResponse
import dev.robothanzo.werewolf.discord.DiscordGateway
import dev.robothanzo.werewolf.security.annotations.CanManageGuild
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@RestController
@RequestMapping("/api/sessions/{guildId}/members")
@Tag(name = "Members", description = "Guild member search for the dashboard pickers")
class MembersController(private val gateway: DiscordGateway) {

    @Operation(summary = "Search members", description = "Members matching a name query (for promote/demote/police pickers).")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "OK")])
    @GetMapping
    @CanManageGuild
    fun search(
        @PathVariable guildId: String,
        @RequestParam(required = false, defaultValue = "") query: String,
    ): ResponseEntity<MembersResponse> {
        val members = gateway.listMembers(guildId.toLong())
            .filter { !it.bot && (query.isBlank() || it.displayName.contains(query, true) || it.name.contains(query, true)) }
            .take(25)
            .map { MemberDto(it.id.toString(), it.name, it.displayName, it.avatarUrl) }
        return ResponseEntity.ok(MembersResponse(members))
    }
}
