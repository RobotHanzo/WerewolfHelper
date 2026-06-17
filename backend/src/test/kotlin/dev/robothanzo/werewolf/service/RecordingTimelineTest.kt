package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.domain.CourtChatData
import dev.robothanzo.werewolf.domain.GameLogEntry
import dev.robothanzo.werewolf.domain.LogSeverity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class RecordingTimelineTest {

    private var seq = 0L
    private fun entry(
        key: String,
        params: List<String> = emptyList(),
        rendered: String = key,
        metadata: Map<String, String> = emptyMap(),
    ) = GameLogEntry(
        id = "e${seq}",
        guildId = 1L,
        timestamp = Instant.ofEpochMilli(1_000L + seq++ * 1_000L),
        severity = LogSeverity.INFO,
        messageKey = key,
        params = params,
        rendered = rendered,
        metadata = metadata,
    )

    @Test
    fun `phase banners drive day and phase segments`() {
        val events = RecordingTimeline.build(
            listOf(
                entry("night.start", listOf("1")),
                entry("night.death", listOf("05", "狼人"), rendered = "玩家05 夜晚出局"),
                entry("game.day.start", listOf("1")),
                entry("police.auto_elected", listOf("02")),
                entry("night.start", listOf("2")),
            ),
            emptyList(),
        )
        assertEquals(listOf(0, 1, 2, 3, 4), events.map { it.idx })
        assertEquals("night", events[0].phaseType)
        assertEquals(1, events[0].day)
        assertEquals("night", events[1].phaseType) // night.death stays in 夜1
        assertEquals("day", events[3].phaseType) // police elected in 日1
        assertEquals(1, events[3].day)
        assertEquals("night", events[4].phaseType)
        assertEquals(2, events[4].day)
    }

    @Test
    fun `deaths carry a kill effect derived from the seat param`() {
        val e = RecordingTimeline.build(listOf(entry("death.announce", listOf("06", "狼人"))), emptyList()).single()
        assertEquals("death", e.type)
        assertEquals(listOf(6), e.effect?.kill)
        assertEquals(6, e.actorSeat)
    }

    @Test
    fun `police elected with a tally becomes a vote event with breakdown and police effect`() {
        val e = RecordingTimeline.build(
            listOf(
                entry(
                    "police.elected", listOf("02", "6"),
                    metadata = mapOf("voteKind" to "police", "voteWin" to "2", "voteRows" to "2:6:1,4,7;6:3:3,8,11"),
                ),
            ),
            emptyList(),
        ).single()
        assertEquals("vote", e.type)
        assertEquals(2, e.effect?.police)
        assertEquals("police", e.vote?.kind)
        assertEquals(2, e.vote?.win)
        assertEquals(2, e.vote?.rows?.size)
        assertEquals(6.0, e.vote?.rows?.first()?.count)
        assertEquals(listOf(1, 4, 7), e.vote?.rows?.first()?.voters)
    }

    @Test
    fun `expel result becomes an exile vote with no effect`() {
        val e = RecordingTimeline.build(
            listOf(
                entry(
                    "expel.result", listOf("06", "7.5"),
                    metadata = mapOf("voteKind" to "exile", "voteOut" to "6", "voteRows" to "6:7.5:1,2,4;2:3:3,8"),
                ),
            ),
            emptyList(),
        ).single()
        assertEquals("vote", e.type)
        assertEquals(6, e.vote?.out)
        assertEquals(7.5, e.vote?.rows?.first()?.count)
        assertNull(e.effect)
    }

    @Test
    fun `winner becomes a result event in the end segment`() {
        val e = RecordingTimeline.build(listOf(entry("game.over.good")), emptyList()).single()
        assertEquals("result", e.type)
        assertEquals("end", e.phaseType)
        assertEquals("GOOD", e.effect?.winner)
        assertEquals("GOOD", RecordingTimeline.winnerOf(listOf(entry("game.over.good"))))
    }

    @Test
    fun `court chat folds in as speech events positioned by timestamp`() {
        val night = entry("night.start", listOf("1"))
        val day = entry("game.day.start", listOf("1"))
        val court = listOf(
            CourtChatData(seat = 3, userId = 99L, author = "大鵬", avatar = null, content = "我是預言家", at = day.timestamp.toEpochMilli() + 10),
        )
        val events = RecordingTimeline.build(listOf(night, day), court)
        val speech = events.single { it.kind == "speech" }
        assertEquals("我是預言家", speech.text)
        assertEquals(3, speech.actorSeat)
        assertEquals("day", speech.phaseType) // arrived after the 天亮 banner
    }

    @Test
    fun `operational noise is dropped`() {
        val events = RecordingTimeline.build(
            listOf(entry("timer.start", listOf("60")), entry("game.reset"), entry("provision.completed")),
            emptyList(),
        )
        assertTrue(events.isEmpty())
    }
}
