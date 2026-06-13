package dev.robothanzo.werewolf

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * WerewolfHelper — hosts Chinese Werewolf (狼人殺) games on Discord with a real-time
 * web dashboard. One Discord guild = one game session.
 *
 * Both the Discord bot and the dashboard are thin interfaces over a single game engine;
 * after every state mutation the backend broadcasts a full snapshot to that guild's
 * WebSocket clients (FEATURES §10.5).
 */
@SpringBootApplication
@EnableScheduling
class WerewolfApplication

fun main(args: Array<String>) {
    runApplication<WerewolfApplication>(*args)
}
