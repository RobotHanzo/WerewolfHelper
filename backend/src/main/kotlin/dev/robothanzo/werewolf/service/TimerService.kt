package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.discord.DiscordBot
import dev.robothanzo.werewolf.discord.SoundCue
import dev.robothanzo.werewolf.domain.LogSeverity
import dev.robothanzo.werewolf.game.flow.GameScheduler
import org.springframework.stereotype.Service

/**
 * The judge's public countdown timer (FEATURES §6). Extracted out of the controller so the firing
 * logic (final completion + the 30 s-remaining warning) can be re-armed verbatim when the game is
 * resumed from a pause. The deadline lives on [dev.robothanzo.werewolf.domain.GameSession.timerEndsAt]
 * (absolute epoch-millis, so the dashboard ticks client-side); this service only owns the in-memory
 * scheduler jobs that drive the sound cues and the end announcement.
 */
@Service
class TimerService(
    private val sessionService: GameSessionService,
    private val scheduler: GameScheduler,
    private val discord: DiscordBot,
    private val announcer: CourtAnnouncer,
) {

    fun start(guildId: Long, seconds: Int) {
        val endsAt = System.currentTimeMillis() + seconds * 1000L
        sessionService.mutate(guildId) { s ->
            s.timerEndsAt = endsAt
            sessionService.log(guildId, LogSeverity.ACTION, "timer.start", seconds)
        }
        announcer.announce(guildId, "timer.start", seconds)
        arm(guildId, seconds * 1000L)
    }

    fun stop(guildId: Long) {
        sessionService.mutate(guildId) { s ->
            s.timerEndsAt = null
            sessionService.log(guildId, LogSeverity.ACTION, "timer.stopped")
        }
        announcer.announce(guildId, "timer.stopped")
        cancel(guildId)
    }

    /** Re-arm the timer jobs after a pause resume, from the (already-shifted) persisted deadline. */
    fun resume(guildId: Long) {
        val endsAt = sessionService.find(guildId)?.timerEndsAt ?: return
        arm(guildId, endsAt - System.currentTimeMillis())
    }

    private fun cancel(guildId: Long) {
        scheduler.cancel(guildId, GameScheduler.TIMER)
        scheduler.cancel(guildId, TIMER_WARN)
    }

    /** Schedule the end + the "30 s remaining" warning for a run with [remainMs] left. */
    private fun arm(guildId: Long, remainMs: Long) {
        scheduler.schedule(guildId, GameScheduler.TIMER, remainMs.coerceAtLeast(0)) {
            sessionService.mutate(guildId) { s ->
                s.timerEndsAt = null
                sessionService.log(guildId, LogSeverity.ALERT, "timer.ended")
            }
            discord.playSound(guildId, SoundCue.TIMER_ENDED)
            announcer.announce(guildId, "timer.ended")
        }
        val warnMs = remainMs - WARNING_AT_SECONDS * 1000L
        if (warnMs > 0) {
            scheduler.schedule(guildId, TIMER_WARN, warnMs) {
                discord.playSound(guildId, SoundCue.TIMER_THIRTY_SECONDS)
            }
        }
    }

    private companion object {
        const val TIMER_WARN = "timer.warn"
        const val WARNING_AT_SECONDS = 30
    }
}
