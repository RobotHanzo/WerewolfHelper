package dev.robothanzo.werewolf.i18n

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import java.util.Locale

/**
 * zh-TW (繁體中文 · 台灣) is the default and first locale. Every player-facing string —
 * Discord embeds, button labels, log lines, API error messages — resolves through a key so
 * future locales are a drop-in resource bundle.
 */
@Configuration
class I18nConfig {

    @Bean
    fun messageSource(): ReloadableResourceBundleMessageSource {
        val source = ReloadableResourceBundleMessageSource()
        source.setBasename("classpath:i18n/messages")
        source.setDefaultEncoding("UTF-8")
        source.setDefaultLocale(DEFAULT_LOCALE)
        source.setFallbackToSystemLocale(false)
        source.setUseCodeAsDefaultMessage(true)
        return source
    }

    companion object {
        val DEFAULT_LOCALE: Locale = Locale.forLanguageTag("zh-TW")
    }
}
