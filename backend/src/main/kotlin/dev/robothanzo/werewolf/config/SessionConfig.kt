package dev.robothanzo.werewolf.config

import org.mongodb.spring.session.JacksonMongoSessionConverter
import org.mongodb.spring.session.config.annotation.web.http.EnableMongoHttpSession
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Activates the Mongo-backed HTTP session store provided by `org.mongodb:mongodb-spring-session`.
 *
 * Spring Boot's auto-configuration detects [EnableMongoHttpSession] and wires the
 * [MongoIndexedSessionRepository] into the servlet filter chain so that every `HttpSession`
 * is transparently persisted to the `dashboard_sessions` collection (configured via
 * `spring.session.mongodb.collection-name` in `application.properties`).
 *
 * [JacksonMongoSessionConverter] is required for Spring Boot 4 / Jackson 3 interop —
 * the older `Jackson2MongoSessionConverter` has been removed in this release line.
 * Session timeout is governed by `spring.session.timeout=7d` in application.properties.
 */
@Configuration(proxyBeanMethods = false)
@EnableMongoHttpSession
class SessionConfig {

    @Bean
    fun jacksonMongoSessionConverter(): JacksonMongoSessionConverter =
        JacksonMongoSessionConverter()
}
