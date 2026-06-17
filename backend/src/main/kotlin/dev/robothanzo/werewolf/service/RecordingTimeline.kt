package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.domain.CourtChatData
import dev.robothanzo.werewolf.domain.GameLogEntry
import dev.robothanzo.werewolf.domain.RecordedEffect
import dev.robothanzo.werewolf.domain.RecordedEvent
import dev.robothanzo.werewolf.domain.RecordedVote
import dev.robothanzo.werewolf.domain.RecordedVoteRow

/**
 * Pure mapping from a finished game's [GameLogEntry] log + captured court chat to the replay event
 * timeline. Kept side-effect-free (no Spring deps) so it's unit-tested directly against synthetic
 * logs — the engine already renders every line to zh-TW, so [RecordedEvent.text] reuses that verbatim
 * and this layer only adds structure (type / kind / effects / vote breakdown) + day/phase segments.
 *
 * Most structure is **derived** from the existing log key + params (deaths, police, winner, phase
 * banners); only the vote breakdown and the night-skill lines carry extra log metadata.
 */
object RecordingTimeline {

    /** A phase banner switches the current segment; [day] is null when only the phase changes (end). */
    private data class Marker(val phaseType: String, val day: Int?)

    fun build(entries: List<GameLogEntry>, court: List<CourtChatData>): List<RecordedEvent> {
        data class Raw(val atMs: Long, val event: RecordedEvent, val marker: Marker?)

        val raws = mutableListOf<Raw>()
        entries.forEach { e ->
            val atMs = e.timestamp.toEpochMilli()
            classify(e, atMs)?.let { (event, marker) -> raws += Raw(atMs, event, marker) }
        }
        // Court text chat folds into the stream as 發言 events, positioned by their own timestamps.
        court.forEach { c ->
            raws += Raw(
                c.at,
                RecordedEvent(
                    idx = 0, day = 0, phaseType = "day", type = "speech", kind = "speech",
                    actorSeat = c.seat, author = c.author, avatar = c.avatar, text = c.content, atMs = c.at,
                ),
                null,
            )
        }

        var phase = "night"
        var day = 1
        return raws.sortedBy { it.atMs }.mapIndexed { i, raw ->
            raw.marker?.let { m -> phase = m.phaseType; m.day?.let { day = it } }
            raw.event.copy(idx = i, day = day, phaseType = phase)
        }
    }

    /** The broad winning side recorded for the selector badge, or null if the game has no win log. */
    fun winnerOf(entries: List<GameLogEntry>): String? = entries.firstNotNullOfOrNull {
        when (it.messageKey) {
            "game.over.wolf" -> "WOLF"
            "game.over.good" -> "GOOD"
            else -> null
        }
    }

    private fun classify(e: GameLogEntry, atMs: Long): Pair<RecordedEvent, Marker?>? {
        val p = e.params
        fun seat(i: Int): Int? = p.getOrNull(i)?.trim()?.toIntOrNull()
        fun event(
            type: String,
            kind: String,
            actorSeat: Int? = null,
            effect: RecordedEffect? = null,
            vote: RecordedVote? = null,
        ) = RecordedEvent(
            idx = 0, day = 0, phaseType = "night", type = type, kind = kind, actorSeat = actorSeat,
            text = e.rendered, atMs = atMs, effect = effect, vote = vote,
        )

        return when (e.messageKey) {
            "night.start" -> event("system", "event") to Marker("night", seat(0) ?: 1)
            "game.day.start" -> event("system", "event") to Marker("day", seat(0) ?: 1)
            "game.over.wolf" -> event("result", "result", effect = RecordedEffect(winner = "WOLF")) to Marker("end", null)
            "game.over.good" -> event("result", "result", effect = RecordedEffect(winner = "GOOD")) to Marker("end", null)

            "night.death", "death.announce" ->
                seat(0)?.let { event("death", "death", actorSeat = it, effect = RecordedEffect(kill = listOf(it))) to null }

            "day.self_destruct", "day.duel.win", "day.duel.lose" ->
                event("death", "death", actorSeat = seat(0)) to null

            "day.revenge", "day.revenge.skip" -> event("hunter", "skill", actorSeat = seat(0)) to null

            "police.auto_elected" ->
                seat(0)?.let { event("police", "event", effect = RecordedEffect(police = it)) to null }
            "police.elected" -> {
                val s = seat(0)
                val vote = parseVote(e.metadata)
                if (vote != null) {
                    event("vote", "vote", effect = s?.let { RecordedEffect(police = it) }, vote = vote) to null
                } else {
                    s?.let { event("police", "event", effect = RecordedEffect(police = it)) to null }
                }
            }
            "expel.result" -> parseVote(e.metadata)?.let { event("vote", "vote", vote = it) to null }

            "replay.skill.seer" -> event("seer", "skill", actorSeat = actorSeat(e)) to null
            "replay.skill.witch.save", "replay.skill.witch.poison", "replay.skill.witch.idle" ->
                event("witch", "skill", actorSeat = actorSeat(e)) to null

            "day.idiot.revealed", "day.blood_moon.survive" -> event("system", "event") to null

            else -> null // operational noise (assign / reset / timer / provision / revive / …) — dropped
        }
    }

    private fun actorSeat(e: GameLogEntry): Int? = e.metadata["actorSeat"]?.toIntOrNull()

    /**
     * Decode the compact `seat:count:voterCsv;…` vote-breakdown metadata written by
     * `DayOrchestrator.encodeVoteMeta`. Returns null when no breakdown was attached.
     */
    private fun parseVote(meta: Map<String, String>): RecordedVote? {
        val rowsRaw = meta["voteRows"] ?: return null
        val rows = rowsRaw.split(";").filter { it.isNotBlank() }.mapNotNull { part ->
            val seg = part.split(":")
            val seat = seg.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val count = seg.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            val voters = seg.getOrNull(2)?.split(",").orEmpty().mapNotNull { it.toIntOrNull() }
            RecordedVoteRow(seat, count, voters)
        }
        return RecordedVote(
            kind = meta["voteKind"] ?: "exile",
            out = meta["voteOut"]?.toIntOrNull(),
            win = meta["voteWin"]?.toIntOrNull(),
            note = meta["voteNote"],
            rows = rows,
        )
    }
}
