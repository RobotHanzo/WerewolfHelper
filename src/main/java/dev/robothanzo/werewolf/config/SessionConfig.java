package dev.robothanzo.werewolf.config;

import org.mongodb.spring.session.config.annotation.web.http.EnableMongoHttpSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableMongoHttpSession(collectionName = "http_sessions")
public class SessionConfig {
    
    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setSameSite("Lax");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(!"http://localhost:5173".equals(System.getenv().getOrDefault("DASHBOARD_URL", "http://localhost:5173")));
        return serializer;
    }
}
