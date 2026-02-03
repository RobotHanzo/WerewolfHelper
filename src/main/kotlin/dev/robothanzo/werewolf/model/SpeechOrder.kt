package dev.robothanzo.werewolf.model

import net.dv8tion.jda.api.entities.emoji.Emoji
import java.util.*

enum class SpeechOrder {
    UP, DOWN;

    override fun toString(): String {
        return if (this == UP) "往上" else "往下"
    }

    fun toEmoji(): Emoji {
        return if (this == UP) Emoji.fromUnicode("U+2b06") else Emoji.fromUnicode("U+2b07")
    }

    companion object {
        fun getRandomOrder(): SpeechOrder {
            return entries[(Math.random() * entries.size).toInt()]
        }

        fun fromString(s: String): SpeechOrder {
            return valueOf(s.uppercase(Locale.ROOT))
        }
    }
}
