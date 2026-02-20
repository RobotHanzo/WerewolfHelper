package dev.robothanzo.werewolf.config

import dev.robothanzo.werewolf.database.documents.Session
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
        val dashboardUrl = Session.DASHBOARD_BASE_URL
        val isSecureEnvironment = dashboardUrl.startsWith("https://")
        serializer.setUseSecureCookie(isSecureEnvironment)

        return serializer
    }
}
