package dev.robothanzo.werewolf.config

import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {
    @Bean
    fun userDetailsService(): UserDetailsService {
        return InMemoryUserDetailsManager()
    }

    @Bean
    @Throws(Exception::class)
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.invoke {
            csrf { disable() }
            cors { configurationSource = corsConfigurationSource() }
            authorizeHttpRequests {
                authorize("/api/auth/**", permitAll)
                authorize("/ws/**", permitAll)
                authorize("/actuator/**", permitAll)
                authorize("/error", permitAll)
                // Swagger UI & OpenAPI access
                authorize("/swagger-ui.html", permitAll)
                authorize("/swagger-ui/**", permitAll)
                authorize("/v3/api-docs/**", permitAll)
                authorize(anyRequest, authenticated)
            }
            exceptionHandling {
                authenticationEntryPoint =
                    org.springframework.security.web.AuthenticationEntryPoint { _, response, _ ->
                        response.status = HttpServletResponse.SC_UNAUTHORIZED
                        response.contentType = "application/json"
                        response.writer.write("{\"success\":false,\"error\":\"Unauthorized\"}")
                    }
                accessDeniedHandler =
                    org.springframework.security.web.access.AccessDeniedHandler { _, response, _ ->
                        response.status = HttpServletResponse.SC_FORBIDDEN
                        response.contentType = "application/json"
                        response.writer.write("{\"success\":false,\"error\":\"Forbidden\"}")
                    }
            }
            addFilterBefore<UsernamePasswordAuthenticationFilter>(UserSessionFilter())
        }
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = listOf(
            "http://localhost:5173",
            "https://wolf.robothanzo.dev",
            "http://wolf.robothanzo.dev"
        )
        configuration.allowedMethods =
            listOf("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}
