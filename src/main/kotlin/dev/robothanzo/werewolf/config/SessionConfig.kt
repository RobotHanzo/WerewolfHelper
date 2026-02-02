package dev.robothanzo.werewolf.config

import org.mongodb.spring.session.config.annotation.web.http.EnableMongoHttpSession
import org.springframework.context.annotation.Configuration

@Configuration
@EnableMongoHttpSession(collectionName = "http_sessions")
class SessionConfig {
    @org.springframework.context.annotation.Bean
    fun cookieSerializer(): org.springframework.session.web.http.CookieSerializer {
        val serializer = org.springframework.session.web.http.DefaultCookieSerializer()
        serializer.setSameSite("Lax")
        serializer.setUseHttpOnlyCookie(true)

        // Use secure cookies for HTTPS environments (production)
        val dashboardUrl = System.getenv().getOrDefault("DASHBOARD_URL", "http://localhost:5173")
        val isSecureEnvironment = dashboardUrl.startsWith("https://")
        serializer.setUseSecureCookie(isSecureEnvironment)

        return serializer
    }
}
