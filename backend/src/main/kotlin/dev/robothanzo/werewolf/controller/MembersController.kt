package dev.robothanzo.werewolf.controller

import dev.robothanzo.werewolf.controller.dto.ApiResponse
import dev.robothanzo.werewolf.controller.dto.DashboardRoleRequest
import dev.robothanzo.werewolf.controller.dto.MemberDto
import dev.robothanzo.werewolf.controller.dto.MembersResponse
import dev.robothanzo.werewolf.discord.DiscordGateway
import dev.robothanzo.werewolf.discord.DiscordProperties
import dev.robothanzo.werewolf.domain.DashboardRole
import dev.robothanzo.werewolf.domain.DashboardUser
import dev.robothanzo.werewolf.domain.LogSeverity
import dev.robothanzo.werewolf.domain.repo.DashboardUserRepository
import dev.robothanzo.werewolf.security.CurrentUser
import dev.robothanzo.werewolf.security.DashboardRoleService
import dev.robothanzo.werewolf.security.annotations.CanManageGuild
import dev.robothanzo.werewolf.service.GameSessionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@RestController
@RequestMapping("/api/sessions/{guildId}/members")
@Tag(name = "Members", description = "Guild member search for the dashboard pickers")
class MembersController(
    private val gateway: DiscordGateway,
    private val currentUser: CurrentUser,
    private val roleService: DashboardRoleService,
    private val dashboardUsers: DashboardUserRepository,
    private val sessionService: GameSessionService,
    private val properties: DiscordProperties,
) {

    @Operation(summary = "Search members", description = "Members matching a name query (for promote/demote/police pickers).")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "OK")])
    @GetMapping
    @CanManageGuild
    fun search(
        @PathVariable guildId: String,
        @RequestParam(required = false, defaultValue = "") query: String,
        @RequestParam(required = false, defaultValue = "") type: String,
    ): ResponseEntity<MembersResponse> {
        val currentUserId = currentUser.userId()
        val gId = guildId.toLong()

        val members = gateway.listMembers(gId)
            .filter { member ->
                if (member.bot) return@filter false
                if (query.isNotBlank() && !member.displayName.contains(query, true) && !member.name.contains(query, true)) {
                    return@filter false
                }

                when (type) {
                    "promote" -> {
                        // Promotion:
                        // 1. Not the current logged-in user
                        // 2. Not already a judge
                        // 3. Not an active player in the game
                        member.id != currentUserId &&
                                roleService.roleFor(gId, member.id) != DashboardRole.JUDGE &&
                                !roleService.isActivePlayer(gId, member.id)
                    }
                    "demote" -> {
                        // Demotion:
                        // 1. Not the current logged-in user
                        // 2. Currently a judge
                        // 3. Not a server creator
                        member.id != currentUserId &&
                                roleService.roleFor(gId, member.id) == DashboardRole.JUDGE &&
                                member.id !in properties.serverCreatorIds
                    }
                    else -> true
                }
            }
            .take(25)
            .map { MemberDto(it.id.toString(), it.name, it.displayName, it.avatarUrl) }

        return ResponseEntity.ok(MembersResponse(members))
    }

    @Operation(summary = "Update dashboard role", description = "Promote or demote a member's dashboard role override.")
    @PostMapping("/role")
    @CanManageGuild
    fun updateRole(
        @PathVariable guildId: String,
        @RequestBody body: DashboardRoleRequest,
    ): ResponseEntity<ApiResponse> {
        val gId = guildId.toLong()
        val uId = body.userId.toLongOrNull() ?: return ResponseEntity.badRequest().body(ApiResponse.error("無效的用戶 ID"))
        val newRole = try {
            DashboardRole.valueOf(body.role)
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(ApiResponse.error("無效的角色"))
        }

        val currentUserId = currentUser.userId()
        if (currentUserId == uId) {
            return ResponseEntity.badRequest().body(ApiResponse.error("不能變更自己的角色"))
        }

        // Prevent demoting creators
        if (newRole != DashboardRole.JUDGE && uId in properties.serverCreatorIds) {
            return ResponseEntity.badRequest().body(ApiResponse.error("不能撤銷創作者的法官權限"))
        }

        val dashboardId = "$guildId:$uId"
        val userOverride = dashboardUsers.findById(dashboardId).orElse(
            DashboardUser(id = dashboardId, guildId = gId, userId = uId, role = newRole)
        ).apply { role = newRole }
        dashboardUsers.save(userOverride)

        val members = gateway.listMembers(gId)
        val targetMember = members.find { it.id == uId }
        val targetName = targetMember?.displayName ?: targetMember?.name ?: uId.toString()

        if (newRole == DashboardRole.JUDGE) {
            gateway.grantJudgeRole(gId, uId)
            sessionService.log(gId, LogSeverity.ACTION, "judge.promoted", targetName)
        } else {
            gateway.revokeJudgeRole(gId, uId)
            sessionService.log(gId, LogSeverity.ACTION, "judge.demoted", targetName)
        }

        // Tell every connected client to re-resolve its role so the dashboard + permissions update in
        // real time (the demoted judge loses judge screens, the promoted spectator gains them).
        sessionService.broadcastAuthRefresh(gId)

        return ResponseEntity.ok(ApiResponse.ok())
    }
}
