package dev.robothanzo.werewolf.ops

import dev.robothanzo.werewolf.domain.LogSeverity
import dev.robothanzo.werewolf.support.TestFixtures
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BulkOperationEngineTest {

    private val engine = BulkOperationEngine(TestFixtures.msg())

    private class RecordingSink : ProgressSink {
        data class Line(val percent: Int, val text: String, val severity: LogSeverity)
        val lines = mutableListOf<Line>()
        override fun emit(percent: Int, line: String, severity: LogSeverity) {
            lines.add(Line(percent, line, severity))
        }
    }

    @Test
    fun `every item runs and a failure does not abort the batch`() = runBlocking {
        val sink = RecordingSink()
        val phase = BulkPhase(
            "grant", 0, 100,
            listOf(
                BulkItem("玩家01 角色") {},
                BulkItem("玩家02 角色") { throw IllegalStateException("權限不足") },
                BulkItem("玩家03 角色") {},
            ),
        )
        val result = engine.execute(listOf(phase), sink)

        assertEquals(3, result.results.size) // all isolated, none aborted
        assertEquals(2, result.completed)
        assertEquals(1, result.failed.size)
        assertEquals("權限不足", result.failed.single().reason)
        assertFalse(result.timedOut)
    }

    @Test
    fun `success and failure lines use the right severity and wording`() = runBlocking {
        val sink = RecordingSink()
        engine.execute(
            listOf(BulkPhase("p", 0, 100, listOf(
                BulkItem("做好的事") {},
                BulkItem("壞掉的事") { throw RuntimeException("原因") },
            ))),
            sink,
        )
        assertTrue(sink.lines[0].text.startsWith("[完成]"))
        assertEquals(LogSeverity.INFO, sink.lines[0].severity)
        assertTrue(sink.lines[1].text.startsWith("[失敗]"))
        assertTrue(sink.lines[1].text.contains("原因"))
        assertEquals(LogSeverity.ALERT, sink.lines[1].severity)
    }

    @Test
    fun `multi-phase percent stays within each phase sub-range and ends at 100`() = runBlocking {
        val sink = RecordingSink()
        val phases = listOf(
            BulkPhase("delete", 0, 30, List(3) { BulkItem("刪除$it") {} }),
            BulkPhase("roles", 30, 60, List(3) { BulkItem("角色$it") {} }),
            BulkPhase("channels", 60, 100, List(4) { BulkItem("頻道$it") {} }),
        )
        engine.execute(phases, sink)

        // first phase tops out at 30, last phase reaches 100
        assertTrue(sink.lines.first().percent in 1..30)
        assertEquals(30, sink.lines[2].percent)
        assertEquals(100, sink.lines.last().percent)
        // monotonic non-decreasing
        val percents = sink.lines.map { it.percent }
        assertEquals(percents.sorted(), percents)
    }

    @Test
    fun `a phase that does not drain in time reports a timeout`() = runBlocking {
        val sink = RecordingSink()
        val phase = BulkPhase("slow", 0, 100, listOf(
            BulkItem("正常") {},
            BulkItem("慢") { delay(5_000) },
        ))
        val result = engine.execute(listOf(phase), sink, phaseTimeoutMillis = 100)

        assertTrue(result.timedOut)
        assertFalse(result.allSucceeded)
        assertTrue(sink.lines.last().text.contains("逾時"))
    }
}
