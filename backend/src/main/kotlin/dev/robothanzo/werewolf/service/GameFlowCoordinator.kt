package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.domain.GameSession
import dev.robothanzo.werewolf.domain.Phase
import dev.robothanzo.werewolf.game.flow.GameFlowService
import dev.robothanzo.werewolf.game.flow.GameScheduler
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service

/**
 * Owns the coarse phase machine's *side* of [GameFlowService]: it enters each phase by running the
 * orchestrator that owns it, and — crucially — drives the **automatic** stage-to-stage progression
 * (FEATURES §6/§7). Once a day stage finishes its interactive work (its poll and speech are both
 * idle), the orchestrators call [advance] and the loop walks itself forward:
 *
 * ```
 * NIGHT → DAWN → [day 1: POLICE_ELECTION] → SPEECHES → EXPEL_VOTE → NIGHT(+1) → …
 * ```
 *
 * The judge's `/state/next` button remains an explicit override on top of this.
 *
 * To avoid a constructor cycle with the orchestrators (which call back into [advance]), this service
 * follows the repo's self-registration idiom: it depends on the orchestrators and registers itself
 * onto them in [wire] (`@PostConstruct`), so the orchestrators need only a settable back-reference.
 */
@Service
class GameFlowCoordinator(
    private val sessionService: GameSessionService,
    private val flow: GameFlowService,
    private val night: NightOrchestrator,
    private val day: DayOrchestrator,
    private val scheduler: GameScheduler,
    private val timer: TimerService,
) {

    @PostConstruct
    fun wire() {
        night.coordinator = this
        day.coordinator = this
    }

    /** Leave the lobby and enter the first night (the judge's "start game"). */
    fun start(guildId: Long) {
        val entered = sessionService.mutate(guildId) { s ->
            require(s.assigned) { "error.not_assigned" }
            val t = flow.start(); s.phase = t.phase; s.day = t.day; t.phase
        }
        enterPhase(guildId, entered)
    }

    /** Advance the coarse phase machine one step and run the orchestrator for the phase we enter. */
    fun advance(guildId: Long) {
        val entered = sessionService.mutate(guildId) { s ->
            if (s.phase == Phase.OVER) return@mutate null
            val t = flow.next(s.phase, s.day); s.phase = t.phase; s.day = t.day; t.phase
        }
        entered?.let { enterPhase(guildId, it) }
    }

    /**
     * Pause ⇄ resume every running countdown (speech turn, poll stage, night phase, public timer).
     *
     * Pausing cancels all in-memory scheduler jobs and stamps [GameSession.pausedAt]; the persisted
     * `endsAt` deadlines are intentionally left as-is, so the dashboard freezes its countdowns at that
     * instant. Resuming shifts every active deadline forward by the elapsed paused duration and re-arms
     * the jobs (via each owner's `resume*`), so the countdowns continue exactly where they stopped.
     */
    fun togglePause(guildId: Long) {
        val session = sessionService.find(guildId) ?: return
        if (session.paused) resume(guildId) else pause(guildId)
    }

    private fun pause(guildId: Long) {
        sessionService.mutate(guildId) { s ->
            if (s.paused) return@mutate
            s.paused = true
            s.pausedAt = System.currentTimeMillis()
        }
        // Freeze the wall clock: stop every running countdown; resume re-arms them from the shifted deadlines.
        scheduler.cancelAll(guildId)
    }

    private fun resume(guildId: Long) {
        sessionService.mutate(guildId) { s ->
            if (!s.paused) return@mutate
            val elapsed = System.currentTimeMillis() - (s.pausedAt ?: System.currentTimeMillis())
            shiftDeadlines(s, elapsed)
            s.paused = false
            s.pausedAt = null
        }
        // Re-arm the in-memory jobs from the freshly-shifted deadlines (each owner reschedules its own).
        night.resumeNight(guildId)
        day.resumeDay(guildId)
        timer.resume(guildId)
    }

    /** Push every active `endsAt` deadline forward by the paused duration so nothing fires early. */
    private fun shiftDeadlines(s: GameSession, elapsedMs: Long) {
        if (elapsedMs <= 0) return
        s.stepEndsAt = s.stepEndsAt?.plus(elapsedMs)
        s.timerEndsAt = s.timerEndsAt?.plus(elapsedMs)
        s.speech?.let { it.endsAt = it.endsAt?.plus(elapsedMs) }
        s.poll?.let { it.stageEndsAt = it.stageEndsAt?.plus(elapsedMs) }
        s.nightState.let { if (it.active && it.endsAt > 0) it.endsAt += elapsedMs }
    }

    /** Run the orchestrator that owns [phase] (night actions / day flow). */
    fun enterPhase(guildId: Long, phase: Phase) {
        when (phase) {
            Phase.NIGHT -> night.startNight(guildId)
            Phase.DAWN -> day.enterDawn(guildId)
            Phase.POLICE_ELECTION -> day.startPoliceElection(guildId)
            Phase.SPEECHES -> day.startSpeeches(guildId)
            Phase.EXPEL_VOTE -> day.startExpelVote(guildId)
            else -> {}
        }
    }
}
