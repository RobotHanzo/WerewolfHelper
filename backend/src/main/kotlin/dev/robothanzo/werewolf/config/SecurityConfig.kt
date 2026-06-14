package dev.robothanzo.werewolf.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Authorization is enforced per-endpoint by the method-level guards (`@CanManageGuild` /
 * `@CanViewGuild`) backed by `identityUtils`, which read the Mongo-backed HTTP session. The filter
 * chain therefore permits requests through and lets the guards (and the controllers' own session
 * checks) decide — the same shape the auto branch used.
 */
@Configuration
@EnableMethodSecurity
class SecurityConfig(
    /**
     * CORS only bites in dev: the Vite proxy forwards the browser's original `Origin` (e.g. the
     * Tailscale/LAN host the judge opened the dashboard on) to the backend, so an origin outside
     * this list is rejected with "Invalid CORS request". Override via `CORS_ALLOWED_ORIGINS`
     * (comma-separated patterns). The default covers localhost plus the common private and
     * Tailscale (100.64.0.0/10) ranges so self-hosting over a LAN/VPN works out of the box.
     */
    @Value(
        "\${werewolf.cors.allowed-origin-patterns:" +
            "http://localhost:*,https://localhost:*,http://127.0.0.1:*," +
            "http://192.168.*:*,https://192.168.*:*," +
            "http://10.*:*,https://10.*:*," +
            "http://100.*:*,https://100.*:*}",
    )
    private val allowedOriginPatterns: List<String>,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
            .authorizeHttpRequests { auth -> auth.anyRequest().permitAll() }
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOriginPatterns = this@SecurityConfig.allowedOriginPatterns
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
        }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", config) }
    }
}
