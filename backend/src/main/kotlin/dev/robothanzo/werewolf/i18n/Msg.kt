package dev.robothanzo.werewolf.i18n

import org.springframework.context.MessageSource
import org.springframework.stereotype.Service

/**
 * Thin wrapper over Spring's [MessageSource], pinned to the default locale (zh-TW today).
 *
 * Game log entries persist both the message key + params *and* the rendered text, so a future
 * locale switch can re-render history while old records stay readable.
 */
@Service
class Msg(private val messageSource: MessageSource) {

    /** Render [key] with positional [args] in the default locale. */
    fun msg(key: String, vararg args: Any?): String =
        messageSource.getMessage(key, args.map { it ?: "" }.toTypedArray(), I18nConfig.DEFAULT_LOCALE)

    /** Render a [Localizable] (a key + its captured params). */
    fun render(localizable: Localizable): String =
        msg(localizable.key, *localizable.params.toTypedArray())
}

/** A message key together with the arguments captured when the event happened. */
data class Localizable(val key: String, val params: List<Any?> = emptyList()) {
    companion object {
        fun of(key: String, vararg params: Any?): Localizable = Localizable(key, params.toList())
    }
}
