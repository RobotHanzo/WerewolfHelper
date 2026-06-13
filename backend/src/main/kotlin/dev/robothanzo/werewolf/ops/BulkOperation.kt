package dev.robothanzo.werewolf.ops

import dev.robothanzo.werewolf.domain.LogSeverity

/** A single Discord mutation within a bulk operation, isolated so one failure never aborts the batch. */
class BulkItem(
    val description: String,
    val run: suspend () -> Unit,
)

/**
 * A phase of a multi-phase operation, mapped onto a sub-range of the 0–100 % bar so the judge sees
 * one smooth progression (e.g. deletions 0–30 %, role creation 30–60 %, channels 60–95 %).
 */
class BulkPhase(
    val name: String,
    val percentStart: Int,
    val percentEnd: Int,
    val items: List<BulkItem>,
)

/** Outcome of one item. */
data class BulkItemResult(
    val description: String,
    val success: Boolean,
    val reason: String? = null,
)

/** Aggregate outcome; [timedOut] flags a barrier that didn't drain in time. */
data class BulkResult(
    val results: List<BulkItemResult>,
    val timedOut: Boolean = false,
) {
    val completed: Int get() = results.count { it.success }
    val failed: List<BulkItemResult> get() = results.filter { !it.success }
    val allSucceeded: Boolean get() = failed.isEmpty() && !timedOut
}

/** Streams live progress (percent + log line) to whoever is watching — the judge channel and WS. */
fun interface ProgressSink {
    fun emit(percent: Int, line: String, severity: LogSeverity)
}
