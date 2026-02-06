package dev.robothanzo.werewolf.controller

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.AuthSession
import dev.robothanzo.werewolf.database.documents.UserRole
import dev.robothanzo.werewolf.utils.isAdmin
import io.mokulu.discord.oauth.DiscordAPI
import io.mokulu.discord.oauth.DiscordOAuth
import io.mokulu.discord.oauth.model.User
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.io.IOException

@RestController
@RequestMapping("/api/auth")
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

    @GetMapping("/login")
    @Throws(IOException::class)
    fun login(
        @RequestParam(name = "guild_id", required = false) guildId: String?,
        response: HttpServletResponse
    ) {
        val state = guildId ?: "no_guild"
        response.sendRedirect(discordOAuth.getAuthorizationURL(state))
    }

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
                    response.sendRedirect(
                        System.getenv().getOrDefault("DASHBOARD_URL", "http://localhost:5173") + "/server/" + state
                    )
                } catch (e: NumberFormatException) {
                    // Invalid guild ID format - redirect to server selection instead
                    log.warn("Invalid guild ID in OAuth state: {}", state)
                    authSession.role = UserRole.PENDING
                    session.setAttribute("user", authSession)
                    response.sendRedirect(System.getenv().getOrDefault("DASHBOARD_URL", "http://localhost:5173") + "/")
                } catch (e: Exception) {
                    log.warn("Failed to set initial guild info: {}", state, e)
                    authSession.role = UserRole.PENDING
                    session.setAttribute("user", authSession)
                    response.sendRedirect(System.getenv().getOrDefault("DASHBOARD_URL", "http://localhost:5173") + "/")
                }
            } else {
                authSession.role = UserRole.PENDING
                session.setAttribute("user", authSession)
                response.sendRedirect(System.getenv().getOrDefault("DASHBOARD_URL", "http://localhost:5173") + "/")
            }
        } catch (e: Exception) {
            log.error("Auth callback failed", e)
            response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Auth failed")
        }
    }

    @PostMapping("/select-guild/{guildId}")
    fun selectGuild(@PathVariable guildId: String, session: HttpSession): ResponseEntity<*> {
        val user = session.getAttribute("user") as? AuthSession
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build<Any>()

        return try {
            val gid = guildId.toLong()
            WerewolfApplication.jda.getGuildById(gid)
                ?: return ResponseEntity.badRequest().body(mapOf("success" to false, "error" to "Guild not found"))

            val member = user.userId?.let { WerewolfApplication.jda.getGuildById(gid)?.getMemberById(it) }
                ?: return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(mapOf("success" to false, "error" to "Not a member"))

            // Determine role
            val role = if (member.isAdmin()) UserRole.JUDGE else UserRole.SPECTATOR
            user.guildId = guildId
            user.role = role
            session.setAttribute("user", user)

            ResponseEntity.ok(mapOf("success" to true, "user" to user))
        } catch (e: NumberFormatException) {
            ResponseEntity.badRequest().body(mapOf("success" to false, "error" to "Invalid guild ID"))
        } catch (e: Exception) {
            log.error("Failed to select guild", e)
            ResponseEntity.internalServerError().body(mapOf("success" to false, "error" to "Internal error"))
        }
    }

    @GetMapping("/me")
    fun me(session: HttpSession): ResponseEntity<*> {
        val user = session.getAttribute("user") as? AuthSession
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("success" to false, "error" to "Not authenticated"))

        val response = mutableMapOf<String, Any?>()
        response["success"] = true
        response["user"] = user
        val discordUser = user.userId?.let { WerewolfApplication.jda.getUserById(it) }
        response["username"] = discordUser?.name
        response["avatar"] = discordUser?.effectiveAvatarUrl

        return ResponseEntity.ok(response)
    }

    @PostMapping("/logout")
    fun logout(session: HttpSession): ResponseEntity<*> {
        session.invalidate()
        return ResponseEntity.ok(mapOf("success" to true))
    }
}
