package dev.robothanzo.werewolf.game.flow

import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-guild scheduler for timed transitions (speech turns, vote/stage deadlines, timers). Every job
 * is keyed so it can be cancelled deterministically — a hard operational requirement: every path
 * that interrupts a flow must actually stop the running countdown (past deadlock bugs lived here).
 *
 * Deadlines themselves are persisted on the session as epoch-millis so countdowns survive a restart;
 * this scheduler only owns the in-memory firing.
 */
@Service
class GameScheduler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = ConcurrentHashMap<Long, ConcurrentHashMap<String, Job>>()

    /** Schedule [action] after [delayMillis]; replaces any existing job under ([guildId], [key]). */
    fun schedule(guildId: Long, key: String, delayMillis: Long, action: suspend () -> Unit) {
        cancel(guildId, key)
        val guildJobs = jobs.getOrPut(guildId) { ConcurrentHashMap() }
        val job = scope.launch {
            delay(delayMillis.coerceAtLeast(0))
            action()
        }
        guildJobs[key] = job
        job.invokeOnCompletion { guildJobs.remove(key, job) }
    }

    fun cancel(guildId: Long, key: String) {
        jobs[guildId]?.remove(key)?.cancel()
    }

    /** Cancel every running countdown for a guild — used by interrupts, reset, and bot removal. */
    fun cancelAll(guildId: Long) {
        jobs.remove(guildId)?.values?.forEach { it.cancel() }
    }

    fun isScheduled(guildId: Long, key: String): Boolean = jobs[guildId]?.containsKey(key) == true

    @PreDestroy
    fun shutdown() {
        scope.cancel()
    }

    companion object Keys {
        const val SPEECH = "speech"
        const val POLL_STAGE = "poll.stage"
        const val TIMER = "timer"
        const val NIGHT = "night"
        /** Per-phase "still pending" reminders DM'd to the seat channels (one key per mark). */
        const val NIGHT_REMINDER = "night.remind"
        const val ORDER_LOCK = "order.lock"
        /** Per-mark "order locks soon" reminders DM'd to the seat channels (one key per mark). */
        const val ORDER_LOCK_REMINDER = "order.lock.remind"
        const val TEN_SECOND_WARNING = "warn.10s"
    }
}
