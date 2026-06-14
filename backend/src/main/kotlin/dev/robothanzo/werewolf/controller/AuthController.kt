package dev.robothanzo.werewolf.controller

import tools.jackson.databind.ObjectMapper
import dev.robothanzo.werewolf.controller.dto.ApiResponse
import dev.robothanzo.werewolf.controller.dto.AuthInfo
import dev.robothanzo.werewolf.controller.dto.AuthResponse
import dev.robothanzo.werewolf.discord.DiscordProperties
import dev.robothanzo.werewolf.security.CurrentUser
import dev.robothanzo.werewolf.security.DashboardRoleService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.view.RedirectView
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

/**
 * Discord OAuth2 (identify + guilds + member scopes) over the standard token/userinfo endpoints,
 * with a Mongo-backed cookie session (~7 days). The ~500 ms post-callback settle (FEATURES §10.6)
 * is implicit in committing the session before the dashboard fetches `/me`.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Discord OAuth2 login and the current dashboard identity")
class AuthController(
    private val properties: DiscordProperties,
    private val currentUser: CurrentUser,
    private val mapper: ObjectMapper,
    private val roleService: DashboardRoleService,
    @Value("\${werewolf.dashboard.base-url}") private val dashboardBaseUrl: String,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val http = HttpClient.newHttpClient()

    @Operation(summary = "Begin login", description = "Redirect to Discord's OAuth2 authorize page.")
    @GetMapping("/login")
    fun login(): RedirectView {
        val scope = URLEncoder.encode("identify guilds guilds.members.read", StandardCharsets.UTF_8)
        val redirect = URLEncoder.encode(properties.redirectUri, StandardCharsets.UTF_8)
        val url = "https://discord.com/api/oauth2/authorize?client_id=${properties.clientId}" +
            "&response_type=code&scope=$scope&redirect_uri=$redirect"
        return RedirectView(url)
    }

    @Operation(summary = "OAuth callback", description = "Exchange the code, store the session, return to the dashboard.")
    @GetMapping("/callback")
    fun callback(@RequestParam code: String): RedirectView {
        try {
            val token = exchangeCode(code)
            val user = fetchUser(token)
            currentUser.login(user.id, user.username, user.avatarUrl)
        } catch (e: Exception) {
            log.error("OAuth callback failed: {}", e.message)
        }
        return RedirectView(dashboardBaseUrl)
    }

    @Operation(summary = "Current identity", description = "The logged-in user, or 401 if no session.")
    @GetMapping("/me")
    fun me(@RequestParam(required = false) guildId: String?): ResponseEntity<*> {
        val userId = currentUser.userId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("未登入"))
        val resolvedRole = guildId?.toLongOrNull()?.let { gId ->
            // An active player (holds a seat in the running game) is locked out before any role check,
            // so a fresh load / re-fetch reflects the lockout immediately (mirrors IdentityUtils).
            if (roleService.isActivePlayer(gId, userId)) "LOCKED_OUT"
            else roleService.roleFor(gId, userId).name
        } ?: "PENDING"
        val info = AuthInfo(
            userId = userId.toString(),
            username = currentUser.username() ?: "",
            avatar = currentUser.avatar(),
            role = resolvedRole,
            guildId = guildId,
        )
        return ResponseEntity.ok(AuthResponse(info))
    }

    @Operation(summary = "Log out", description = "Invalidate the session.")
    @PostMapping("/logout")
    fun logout(): ResponseEntity<ApiResponse> {
        currentUser.logout()
        return ResponseEntity.ok(ApiResponse.ok())
    }

    private data class DiscordUser(val id: Long, val username: String, val avatarUrl: String?)

    private fun exchangeCode(code: String): String {
        val form = mapOf(
            "client_id" to properties.clientId,
            "client_secret" to properties.clientSecret,
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to properties.redirectUri,
        ).entries.joinToString("&") {
            "${it.key}=${URLEncoder.encode(it.value, StandardCharsets.UTF_8)}"
        }
        val request = HttpRequest.newBuilder(URI.create("https://discord.com/api/oauth2/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        val node = mapper.readTree(response.body())
        return node.get("access_token").asText()
    }

    private fun fetchUser(accessToken: String): DiscordUser {
        val request = HttpRequest.newBuilder(URI.create("https://discord.com/api/users/@me"))
            .header("Authorization", "Bearer $accessToken")
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        val node = mapper.readTree(response.body())
        val id = node.get("id").asLong()
        val avatar = node.get("avatar")?.takeIf { !it.isNull }?.asText()
        return DiscordUser(
            id = id,
            username = node.get("username").asText(),
            avatarUrl = avatar?.let { "https://cdn.discordapp.com/avatars/$id/$it.png" },
        )
    }
}
