package dev.robothanzo.werewolf.utils

import net.dv8tion.jda.api.requests.RestAction
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class ActionTask(
    val action: RestAction<*>,
    val description: String,
    val onSuccess: ((Any?) -> Unit)? = null
)

/**
 * Executes a collection of Discord actions with progress tracking and status logging.
 */
@Throws(Exception::class)
fun Collection<ActionTask>.runActions(
    statusLogger: ((String) -> Unit)? = null,
    progressCallback: ((Int) -> Unit)? = null,
    startPercent: Int = 0,
    endPercent: Int = 100,
    timeoutSeconds: Int = 30
) {
    val total = this.size
    if (total == 0) {
        progressCallback?.invoke(endPercent)
        return
    }
    val completed = AtomicInteger(0)
    val allDone = CompletableFuture<Void>()
    val range = endPercent - startPercent

    for (task in this) {
        task.action.queue({ success: Any? ->
            statusLogger?.invoke("  - [完成] " + task.description)
            task.onSuccess?.invoke(success)
            handleTaskCompletion(completed, total, allDone, progressCallback, startPercent, range)
        }) { error: Throwable ->
            statusLogger?.invoke("  - [失敗] " + task.description + ": " + error.message)
            handleTaskCompletion(completed, total, allDone, progressCallback, startPercent, range)
        }
    }
    try {
        allDone.get(timeoutSeconds.toLong(), TimeUnit.SECONDS)
    } catch (e: Exception) {
        statusLogger?.invoke("警告: 部分 Discord 變更操作逾時或中斷 (" + e.message + ")")
        throw e
    }
}

private fun handleTaskCompletion(
    completed: AtomicInteger,
    total: Int,
    allDone: CompletableFuture<Void>,
    progressCallback: ((Int) -> Unit)?,
    startPercent: Int,
    range: Int
) {
    val c = completed.incrementAndGet()
    if (progressCallback != null) {
        val currentProgress = startPercent + (c / total.toDouble() * range).toInt()
        progressCallback(currentProgress)
    }
    if (c == total) {
        allDone.complete(null)
    }
}
