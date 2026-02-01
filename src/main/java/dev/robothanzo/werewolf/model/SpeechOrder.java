package dev.robothanzo.werewolf.model;

import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.util.Locale;

public enum SpeechOrder {
    UP, DOWN;

    public static SpeechOrder getRandomOrder() {
        return values()[(int) (Math.random() * values().length)];
    }

    public static SpeechOrder fromString(String s) {
        return valueOf(s.toUpperCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return this == UP ? "往上" : "往下";
    }

    public Emoji toEmoji() {
        return this == UP ? Emoji.fromUnicode("U+2b06") : Emoji.fromUnicode("U+2b07");
    }
}
