package dev.robothanzo.werewolf.controller

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.controller.dto.ApiResponse
import dev.robothanzo.werewolf.controller.dto.BasicUserDto
import dev.robothanzo.werewolf.controller.dto.BasicUserResponse
import dev.robothanzo.werewolf.security.annotations.CanViewGuild
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@RestController
@RequestMapping("/api/users")
@Tag(name = "User Info", description = "Endpoints for retrieving user information")
class UserController {
    private val log = LoggerFactory.getLogger(UserController::class.java)

    @Operation(
        summary = "Get User Info",
        description = "Retrieves public information about a user in a specific guild."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200", description = "Successfully retrieved user info",
                content = [Content(schema = Schema(implementation = BasicUserResponse::class))]
            ),
            SwaggerApiResponse(responseCode = "404", description = "User or Guild not found"),
            SwaggerApiResponse(responseCode = "403", description = "User does not have permission to view this guild")
        ]
    )
    @GetMapping("/{guildId}/{userId}")
    @CanViewGuild
    fun getUser(
        @Parameter(description = "ID of the guild")
        @PathVariable guildId: String,
        @Parameter(description = "ID of the user")
        @PathVariable userId: String
    ): ResponseEntity<ApiResponse> {
        return try {
            val member = WerewolfApplication.jda.getGuildById(guildId)?.getMemberById(userId)
            if (member != null) {
                return ResponseEntity.ok(
                    BasicUserResponse(
                        BasicUserDto(
                            id = member.id,
                            name = member.effectiveName,
                            avatar = member.effectiveAvatarUrl
                        )
                    )
                )
            } else {
                ResponseEntity.notFound().build()
            }
        } catch (e: Exception) {
            log.error("Failed to fetch user info", e)
            ResponseEntity.internalServerError().body(ApiResponse.error(e.message ?: "Unknown error"))
        }
    }
}
