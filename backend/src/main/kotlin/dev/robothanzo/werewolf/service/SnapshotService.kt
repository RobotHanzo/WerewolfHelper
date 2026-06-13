package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.controller.dto.FactionMeterDto
import dev.robothanzo.werewolf.controller.dto.GameSnapshot
import dev.robothanzo.werewolf.controller.dto.IdentityDto
import dev.robothanzo.werewolf.controller.dto.LogDto
import dev.robothanzo.werewolf.controller.dto.SeatDto
import dev.robothanzo.werewolf.controller.dto.WinnerDto
import dev.robothanzo.werewolf.discord.DiscordGateway
import dev.robothanzo.werewolf.domain.Faction
import dev.robothanzo.werewolf.domain.GameLogEntry
import dev.robothanzo.werewolf.domain.GameSession
import dev.robothanzo.werewolf.domain.Phase
import dev.robothanzo.werewolf.domain.Seat
import dev.robothanzo.werewolf.game.roles.RoleRegistry
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
) {

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
            speech = null,
            poll = null,
            night = null,
            log = logs.map {
                LogDto(it.id, it.timestamp.toEpochMilli(), it.severity.name.lowercase(), it.rendered)
            },
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
