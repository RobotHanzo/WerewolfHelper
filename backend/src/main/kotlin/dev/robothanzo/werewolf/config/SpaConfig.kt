package dev.robothanzo.werewolf.config

import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.resource.PathResourceResolver

/**
 * Serves the bundled SPA (`classpath:/static`, packed by `bootJar`) and falls back to `index.html`
 * for client-side routes (`/server/:guildId/...`) so a full-page load or refresh on a deep link
 * works. Real files (JS/CSS/assets) are served as-is; the API and WebSocket routes are left to
 * their own handlers (which take precedence anyway) and never fall through to the SPA.
 */
@Configuration
class SpaConfig : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .resourceChain(true)
            .addResolver(object : PathResourceResolver() {
                override fun getResource(resourcePath: String, location: Resource): Resource? {
                    if (resourcePath.startsWith("api/") || resourcePath == "ws" || resourcePath.startsWith("ws/")) {
                        return null
                    }
                    val requested = location.createRelative(resourcePath)
                    if (requested.exists() && requested.isReadable) return requested
                    val index = ClassPathResource("static/index.html")
                    return if (index.exists()) index else null
                }
            })
    }
}
