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
        
        // Use secure cookies for HTTPS environments (production)
        String dashboardUrl = System.getenv().getOrDefault("DASHBOARD_URL", "http://localhost:5173");
        boolean isSecureEnvironment = dashboardUrl.startsWith("https://");
        serializer.setUseSecureCookie(isSecureEnvironment);
        
        return serializer;
    }
}
