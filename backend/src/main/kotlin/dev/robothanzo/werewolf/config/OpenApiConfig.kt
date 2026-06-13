package dev.robothanzo.werewolf.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Mirrors the auto-branch OpenAPI bean; the Scalar UI renders at `/scalar`. */
@Configuration
class OpenApiConfig {
    @Bean
    fun werewolfOpenAPI(): OpenAPI = OpenAPI().info(
        Info()
            .title("WerewolfHelper API")
            .version("1.0.0")
            .description("狼人殺助手 — REST API for the judge dashboard and spectator view.")
            .license(License().name("Apache 2.0")),
    )
}
