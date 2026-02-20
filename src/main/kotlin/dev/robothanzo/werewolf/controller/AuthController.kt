package dev.robothanzo.werewolf.controller

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.controller.dto.ApiResponse
import dev.robothanzo.werewolf.controller.dto.AuthData
import dev.robothanzo.werewolf.controller.dto.AuthResponse
import dev.robothanzo.werewolf.database.documents.AuthSession
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.database.documents.UserRole
import dev.robothanzo.werewolf.utils.isAdmin
import io.mokulu.discord.oauth.DiscordAPI
import io.mokulu.discord.oauth.DiscordOAuth
import io.mokulu.discord.oauth.model.User
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.io.IOException
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Endpoints for user authentication and session management")
class AuthController {
    private val log = LoggerFactory.getLogger(AuthController::class.java)

    companion object {
        private val CLIENT_ID = System.getenv().getOrDefault("DISCORD_CLIENT_ID", "")
        private val CLIENT_SECRET = System.getenv().getOrDefault("DISCORD_CLIENT_SECRET", "")
        private val REDIRECT_URI = System.getenv().getOrDefault(
            "DISCORD_REDIRECT_URI",
            "http://localhost:5173/auth/callback"
        )
    }

    private val discordOAuth = DiscordOAuth(
        CLIENT_ID, CLIENT_SECRET, REDIRECT_URI,
        arrayOf("identify", "guilds", "guilds.members.read")
    )

    @Operation(
        summary = "Initiate Discord OAuth login",
        description = "Redirects the user to Discord for authentication."
    )
    @GetMapping("/login")
    @Throws(IOException::class)
    fun login(
        @Parameter(description = "Optional Guild ID to redirect back to after login")
        @RequestParam(name = "guild_id", required = false) guildId: String?,
        response: HttpServletResponse
    ) {
        val state = guildId ?: "no_guild"
        response.sendRedirect(discordOAuth.getAuthorizationURL(state))
    }

    @Operation(
        summary = "Discord OAuth callback",
        description = "Handles the callback from Discord, exchanges code for tokens, and creates a user session."
    )
    @GetMapping("/callback")
    @Throws(IOException::class)
    fun callback(
        @RequestParam code: String,
        @RequestParam state: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        try {
            val tokenResponse = discordOAuth.getTokens(code)
            val discordAPI = DiscordAPI(tokenResponse.accessToken)
            val user: User = discordAPI.fetchUser()

            // Prevent session fixation: invalidate old session and create new one
            val oldSession = request.getSession(false)
            oldSession?.invalidate()
            val session = request.getSession(true)

            // Store user in Session
            val authSession = AuthSession(
                userId = user.id
            )

            session.setAttribute("user", authSession)

            if ("no_guild" != state) {
                try {
                    // Validate it's a number - prevents open redirect attacks
                    val gid = state.toLong()
                    authSession.guildId = state

                    // Attempt to pre-calculate role
                    val member = WerewolfApplication.jda.getGuildById(gid)?.getMemberById(user.id)
                    if (member != null && (member.isAdmin())
                    ) {
                        authSession.role = UserRole.JUDGE
                    } else {
                        authSession.role = UserRole.SPECTATOR
                    }

                    session.setAttribute("user", authSession)
                    response.sendRedirect("${Session.DASHBOARD_BASE_URL}/server/$state")
                } catch (e: NumberFormatException) {
                    // Invalid guild ID format - redirect to server selection instead
                    log.warn("Invalid guild ID in OAuth state: {}", state)
                    authSession.role = UserRole.PENDING
                    session.setAttribute("user", authSession)
                    response.sendRedirect("${Session.DASHBOARD_BASE_URL}/")
                } catch (e: Exception) {
                    log.warn("Failed to set initial guild info: {}", state, e)
                    authSession.role = UserRole.PENDING
                    session.setAttribute("user", authSession)
                    response.sendRedirect("${Session.DASHBOARD_BASE_URL}/")
                }
            } else {
                authSession.role = UserRole.PENDING
                session.setAttribute("user", authSession)
                response.sendRedirect("${Session.DASHBOARD_BASE_URL}/")
            }
        } catch (e: Exception) {
            log.error("Auth callback failed", e)
            response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Auth failed")
        }
    }

    @Operation(summary = "Select Guild", description = "Updates the user session with the selected guild ID.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200", description = "Guild selected successfully",
                content = [Content(schema = Schema(implementation = AuthResponse::class))]
            ),
            SwaggerApiResponse(responseCode = "400", description = "Invalid Guild ID or Guild not found"),
            SwaggerApiResponse(responseCode = "401", description = "User not authenticated"),
            SwaggerApiResponse(responseCode = "403", description = "User is not a member of the guild")
        ]
    )
    @PostMapping("/select-guild/{guildId}")
    fun selectGuild(
        @Parameter(description = "ID of the guild to select")
        @PathVariable guildId: String,
        session: HttpSession
    ): ResponseEntity<ApiResponse> {
        val user = session.getAttribute("user") as? AuthSession
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Not authenticated"))

        return try {
            val gid = guildId.toLong()
            WerewolfApplication.jda.getGuildById(gid)
                ?: return ResponseEntity.badRequest().body(ApiResponse.error("Guild not found"))

            val member = user.userId?.let { WerewolfApplication.jda.getGuildById(gid)?.getMemberById(it) }
                ?: return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Not a member"))

            // Determine role
            val role = if (member.isAdmin()) UserRole.JUDGE else UserRole.SPECTATOR
            user.guildId = guildId
            user.role = role
            session.setAttribute("user", user)

            ResponseEntity.ok(AuthResponse(AuthData(user = user)))
        } catch (e: NumberFormatException) {
            ResponseEntity.badRequest().body(ApiResponse.error("Invalid guild ID"))
        } catch (e: Exception) {
            log.error("Failed to select guild", e)
            ResponseEntity.internalServerError().body(ApiResponse.error("Internal error"))
        }
    }

    @Operation(
        summary = "Get current user info",
        description = "Returns the currently authenticated user's session information."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200", description = "User info returned",
                content = [Content(schema = Schema(implementation = AuthResponse::class))]
            ),
            SwaggerApiResponse(responseCode = "401", description = "User not authenticated")
        ]
    )
    @GetMapping("/me")
    fun me(session: HttpSession): ResponseEntity<ApiResponse> {
        val user = session.getAttribute("user") as? AuthSession
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Not authenticated"))

        val discordUser = user.userId?.let { WerewolfApplication.jda.getUserById(it) }

        return ResponseEntity.ok(
            AuthResponse(
                AuthData(
                    user = user,
                    username = discordUser?.name,
                    avatar = discordUser?.effectiveAvatarUrl
                )
            )
        )
    }

    @Operation(summary = "Logout", description = "Invalidates the current user session.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Logged out successfully")
        ]
    )
    @PostMapping("/logout")
    fun logout(session: HttpSession): ResponseEntity<ApiResponse> {
        session.invalidate()
        return ResponseEntity.ok(ApiResponse.ok(message = "Logged out successfully"))
    }
}
