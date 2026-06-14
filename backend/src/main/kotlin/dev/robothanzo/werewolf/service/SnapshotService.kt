package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.controller.dto.FactionMeterDto
import dev.robothanzo.werewolf.controller.dto.GameSnapshot
import dev.robothanzo.werewolf.controller.dto.IdentityDto
import dev.robothanzo.werewolf.controller.dto.LogDto
import dev.robothanzo.werewolf.controller.dto.NightActionDto
import dev.robothanzo.werewolf.controller.dto.NightDto
import dev.robothanzo.werewolf.controller.dto.NightVoteDto
import dev.robothanzo.werewolf.controller.dto.NightWaveDto
import dev.robothanzo.werewolf.controller.dto.PollCandidateDto
import dev.robothanzo.werewolf.controller.dto.PollDto
import dev.robothanzo.werewolf.controller.dto.SeatDto
import dev.robothanzo.werewolf.controller.dto.SpeechDto
import dev.robothanzo.werewolf.controller.dto.WinnerDto
import dev.robothanzo.werewolf.discord.DiscordGateway
import dev.robothanzo.werewolf.domain.Faction
import dev.robothanzo.werewolf.domain.GameLogEntry
import dev.robothanzo.werewolf.domain.GameSession
import dev.robothanzo.werewolf.domain.NightState
import dev.robothanzo.werewolf.domain.Phase
import dev.robothanzo.werewolf.domain.Seat
import dev.robothanzo.werewolf.game.night.Effect
import dev.robothanzo.werewolf.game.night.NightAbility
import dev.robothanzo.werewolf.game.night.NightDeclarationsBuilder
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import dev.robothanzo.werewolf.game.vote.PollEngine
import dev.robothanzo.werewolf.game.win.WinConditionChecker
import dev.robothanzo.werewolf.i18n.Msg
import org.springframework.stereotype.Service

/**
 * Builds the wire [GameSnapshot] from a [GameSession]. Identities are localized here (the frontend
 * stays role-agnostic); meters and the win banner come from the same [WinConditionChecker] the
 * engine uses, so the dashboard and the rules never disagree.
 */
@Service
class SnapshotService(
    private val roles: RoleRegistry,
    private val win: WinConditionChecker,
    private val gateway: DiscordGateway,
    private val msg: Msg,
    private val declarations: NightDeclarationsBuilder,
    private val polls: PollEngine,
    abilities: List<NightAbility>,
) {
    private val abilitiesById = abilities.associateBy { it.id }
    private val wolfKillAbilityId = abilities.firstOrNull { Effect.WOLF_KILL in it.writes }?.id

    fun build(session: GameSession, logs: List<GameLogEntry>): GameSnapshot {
        val members = if (gateway.available) gateway.listMembers(session.guildId).associateBy { it.id } else emptyMap()

        val seats = session.seats.sortedBy { it.number }.map { seat ->
            SeatDto(
                seat = seat.number,
                label = seat.paddedNumber,
                memberId = seat.memberId?.toString(),
                displayName = seat.memberId?.let { members[it]?.displayName },
                avatar = seat.memberId?.let { members[it]?.avatarUrl },
                unassigned = !seat.assigned,
                alive = seat.alive,
                identities = seat.cards.map { card ->
                    val role = roles.byId(card.roleId)
                    IdentityDto(
                        roleId = card.roleId,
                        name = role?.let { msg.msg(it.nameKey) } ?: card.roleId,
                        faction = roles.factionOf(card.roleId).name,
                        dead = card.dead,
                    )
                },
                police = seat.police,
                goldenBaby = seat.goldenBaby,
                clone = seat.clone,
                idiot = seat.idiot,
                orderLocked = seat.orderLocked,
                revengePending = seat.revengePending,
                idiotRevealed = seat.idiotRevealed,
                loverSeat = seat.loverSeat,
                charmedSeat = seat.charmedSeat,
                learnedRoleId = seat.learnedRoleId,
                knifeArmed = seat.knifeArmed,
            )
        }

        val winResult = if (session.assigned) win.check(session) else null

        return GameSnapshot(
            guildId = session.guildId.toString(),
            phase = session.phase.name,
            day = session.day,
            paused = session.paused,
            started = session.phase != Phase.LOBBY,
            doubleIdentity = session.settings.doubleIdentity,
            muteAfterSpeech = session.settings.muteAfterSpeech,
            witchSelfSave = session.settings.witchSelfSave,
            hiddenWolfInheritsKnife = session.settings.hiddenWolfInheritsKnife,
            assigned = session.assigned,
            policeSeat = session.policeSeat,
            aliveCount = session.aliveSeats().size,
            totalSeats = session.playerCount,
            winner = winResult?.takeIf { it.over }?.let {
                WinnerDto(it.winner!!.name, it.reasonKey?.let { key -> msg.msg(key) } ?: "")
            },
            timerEndsAt = session.timerEndsAt,
            seats = seats,
            meters = meters(session),
            speech = buildSpeech(session),
            poll = buildPoll(session),
            night = buildNight(session),
            log = logs.map {
                LogDto(it.id, it.timestamp.toEpochMilli(), it.severity.name.lowercase(), it.rendered)
            },
            pool = session.pool,
        )
    }

    /** Live day speech flow (speaking order, current speaker, the parked direction choice). */
    private fun buildSpeech(session: GameSession): SpeechDto? {
        val flow = session.speech ?: return null
        val speaker = flow.order.getOrNull(flow.index)
        return SpeechDto(
            active = true,
            waiting = flow.waiting,
            direction = flow.direction.name,
            fromSeat = flow.from,
            speakerSeat = speaker,
            endsAt = flow.endsAt,
            order = flow.order,
            upcoming = if (flow.index + 1 <= flow.order.size) flow.order.drop(flow.index + 1) else emptyList(),
        )
    }

    /** Live poll (police election or expel vote) with weighted tallies and per-candidate voters. */
    private fun buildPoll(session: GameSession): PollDto? {
        val poll = session.poll ?: return null
        val ctx = SessionPollContext(session)
        val tally = polls.tally(poll, ctx)
        val voters = polls.voters(poll)
        val display = if (poll.pkRound) poll.pkCandidates.sorted() else poll.candidates.sorted()
        val candidates = display.map { seat ->
            PollCandidateDto(
                seat = seat,
                withdrawn = seat in poll.withdrawn,
                weight = tally[seat] ?: 0.0,
                voters = voters[seat] ?: emptyList(),
            )
        }
        val eligible = session.aliveSeats().map { it.number }.count { polls.canVote(poll, ctx, it) }
        return PollDto(
            kind = poll.kind.name,
            stage = poll.stage.name,
            endsAt = poll.stageEndsAt,
            candidates = candidates,
            eligibleVoters = if (poll.stage.name == "VOTING") eligible else session.aliveSeats().size,
            votesCast = poll.votes.size,
        )
    }

    /** Map the persisted night round onto the dashboard night board (no secrets beyond what the
     *  judge view already shows; spectator surfaces never request this). */
    private fun buildNight(session: GameSession): NightDto? {
        val night = session.nightState
        if (!night.active && !night.resolved) return null
        if (!night.active && night.resolved && session.phase != Phase.NIGHT) {
            // keep showing the just-resolved summary briefly during NIGHT; otherwise drop it
            if (session.phase != Phase.DAWN) return null
        }

        val allIds = night.waves.flatten()
        val waves = night.waves.mapIndexed { i, ids ->
            NightWaveDto(i, ids.map { actionDto(session, night, it) })
        }
        val submitted = allIds.count { submittedCount(night, it) }
        return NightDto(
            active = night.active,
            day = night.day,
            endsAt = night.endsAt.takeIf { night.active },
            submittedCount = submitted,
            totalCount = allIds.size,
            resolved = night.resolved,
            summary = night.summary,
            waves = waves,
        )
    }

    private fun submittedCount(night: NightState, abilityId: String): Boolean =
        if (abilityId == wolfKillAbilityId) night.wolfParticipants.isNotEmpty() && night.wolfParticipants.all { night.wolfVotes.containsKey(it) }
        else night.intents.any { it.abilityId == abilityId }

    private fun actionDto(session: GameSession, night: NightState, abilityId: String): NightActionDto {
        if (abilityId == wolfKillAbilityId) {
            val voted = night.wolfParticipants.all { night.wolfVotes.containsKey(it) }
            val votes = night.wolfParticipants.sorted().map { voter ->
                val choice = night.wolfVotes[voter]
                NightVoteDto(voter = voter, target = choice?.takeIf { it >= 0 }, skip = choice == -1)
            }
            return NightActionDto(
                abilityId = abilityId,
                roleId = "wolf",
                roleName = msg.msg("role.wolf"),
                faction = Faction.WOLF.name,
                actorSeats = night.wolfParticipants.sorted(),
                targetSeat = declarations.wolfConsensus(night.wolfVotes),
                status = if (voted) "submitted" else "acting",
                votes = votes,
            )
        }
        val roleId = abilitiesById[abilityId]?.roleId ?: abilityId
        val intent = night.intents.firstOrNull { it.abilityId == abilityId }
        val status = when {
            intent == null -> "acting"
            intent.skipped -> "skipped"
            else -> "submitted"
        }
        return NightActionDto(
            abilityId = abilityId,
            roleId = roleId,
            roleName = roles.localizedName(roleId),
            faction = roles.factionOf(roleId).name,
            actorSeats = intent?.actorSeats ?: session.aliveSeats().filter { s -> s.livingCards().any { it.roleId == roleId } }.map { it.number },
            targetSeat = intent?.targets?.firstOrNull(),
            status = status,
        )
    }

    /** wolves / gods / villagers — or 金寶寶 as the third meter in double-identity mode. */
    private fun meters(session: GameSession): List<FactionMeterDto> {
        fun factionCounts(faction: Faction): Pair<Int, Int> {
            var alive = 0
            var total = 0
            session.seats.filter { it.assigned }.forEach { seat ->
                seat.cards.forEach { card ->
                    val f = if (seat.clone) Faction.GOD else roles.factionOf(card.roleId)
                    if (f == faction) {
                        total++
                        if (!card.dead) alive++
                    }
                }
            }
            return alive to total
        }

        val wolf = factionCounts(Faction.WOLF)
        val god = factionCounts(Faction.GOD)
        val third = if (session.settings.doubleIdentity) {
            val gbaby = session.seats.filter { it.goldenBaby && it.assigned }
            FactionMeterDto("GBABY", gbaby.count { it.alive }, gbaby.size)
        } else {
            val vill = factionCounts(Faction.VILLAGER)
            FactionMeterDto(Faction.VILLAGER.name, vill.first, vill.second)
        }
        return listOf(
            FactionMeterDto(Faction.WOLF.name, wolf.first, wolf.second),
            FactionMeterDto(Faction.GOD.name, god.first, god.second),
            third,
        )
    }
}
