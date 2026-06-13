package dev.robothanzo.werewolf.ops

import dev.robothanzo.werewolf.domain.LogSeverity
import dev.robothanzo.werewolf.i18n.Msg
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.springframework.stereotype.Service

/**
 * Runs bulk Discord mutations the way production demands (FEATURES §10.1–10.4):
 *
 * - **Barrier** — every item in a phase is awaited (success *or* failure) before the next phase.
 * - **Per-item fault isolation** — one member leaving or a permission error logs `[失敗] …: <原因>`
 *   and the batch continues; successes log `[完成] …`.
 * - **Percent mapping** — each phase fills its own sub-range so the bar moves smoothly across phases.
 * - **Timeout** — a phase that doesn't drain in time surfaces what completed (部分操作逾時) and stops.
 *
 * Items run sequentially, leaning on the Discord library's own rate-limit serialization rather than
 * manual sleeps or parallel blasts.
 */
@Service
class BulkOperationEngine(private val msg: Msg) {

    /** Default generous barrier timeout per phase. */
    val defaultPhaseTimeoutMillis: Long = 120_000

    suspend fun execute(
        phases: List<BulkPhase>,
        sink: ProgressSink,
        phaseTimeoutMillis: Long = defaultPhaseTimeoutMillis,
    ): BulkResult {
        val results = mutableListOf<BulkItemResult>()
        for (phase in phases) {
            val total = phase.items.size
            if (total == 0) continue
            try {
                withTimeout(phaseTimeoutMillis) {
                    phase.items.forEachIndexed { i, item ->
                        val result = runItem(item)
                        results += result
                        val percent = mapPercent(phase, i + 1, total)
                        if (result.success) {
                            sink.emit(percent, msg.msg("bulk.item.done", item.description), LogSeverity.INFO)
                        } else {
                            sink.emit(
                                percent,
                                msg.msg("bulk.item.failed", item.description, result.reason ?: ""),
                                LogSeverity.ALERT,
                            )
                        }
                    }
                }
            } catch (_: TimeoutCancellationException) {
                sink.emit(phase.percentEnd, msg.msg("bulk.timeout"), LogSeverity.ALERT)
                return BulkResult(results, timedOut = true)
            }
        }
        return BulkResult(results)
    }

    private suspend fun runItem(item: BulkItem): BulkItemResult =
        try {
            item.run()
            BulkItemResult(item.description, success = true)
        } catch (e: Exception) {
            BulkItemResult(item.description, success = false, reason = e.message ?: e::class.simpleName)
        }

    /** Linear interpolation of an item's position into the phase's percent sub-range. */
    private fun mapPercent(phase: BulkPhase, done: Int, total: Int): Int {
        val span = phase.percentEnd - phase.percentStart
        return phase.percentStart + (span * done / total)
    }
}
