package dev.robothanzo.werewolf.config

import org.mongodb.spring.session.JacksonMongoSessionConverter
import org.mongodb.spring.session.MongoIndexedSessionRepository
import org.mongodb.spring.session.config.annotation.web.http.EnableMongoHttpSession
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.convert.DurationStyle
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.session.config.SessionRepositoryCustomizer

/**
 * Persists dashboard HTTP sessions in MongoDB so a backend restart never logs anyone out
 * (FEATURES §10.6 — the OAuth callback parks the Discord user id on the session).
 *
 * We use the MongoDB-maintained `org.mongodb:mongodb-spring-session` module (new
 * `org.mongodb.spring.session` namespace — the older Spring-maintained
 * `spring-session-data-mongodb` is EOL). Spring Boot's session auto-configuration does **not**
 * recognise this namespace, so the store is activated explicitly via [EnableMongoHttpSession]
 * and is **not** driven by the Boot `spring.session.*` properties: the collection name and
 * inactivity timeout are applied through the [SessionRepositoryCustomizer] below instead.
 *
 * [JacksonMongoSessionConverter] serialises the session to BSON via Jackson 3 (Boot 4's primary
 * Jackson); the deprecated `Jackson2MongoSessionConverter` is for the old Jackson 2 line.
 */
@Configuration(proxyBeanMethods = false)
@EnableMongoHttpSession
class SessionConfig {

    @Bean
    fun jacksonMongoSessionConverter(): JacksonMongoSessionConverter = JacksonMongoSessionConverter()

    @Bean
    fun mongoSessionRepositoryCustomizer(
        @Value("\${werewolf.session.collection-name:dashboard_sessions}") collectionName: String,
        @Value("\${werewolf.session.timeout:7d}") timeout: String,
    ) = SessionRepositoryCustomizer<MongoIndexedSessionRepository> { repository ->
        repository.setCollectionName(collectionName)
        repository.setDefaultMaxInactiveInterval(DurationStyle.detectAndParse(timeout))
    }
}
