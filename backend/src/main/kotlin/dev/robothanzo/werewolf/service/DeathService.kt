package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.discord.NicknameService
import dev.robothanzo.werewolf.discord.syncNickname
import dev.robothanzo.werewolf.domain.Faction
import dev.robothanzo.werewolf.domain.GameSession
import dev.robothanzo.werewolf.domain.IdentityCard
import dev.robothanzo.werewolf.domain.Seat
import dev.robothanzo.werewolf.game.roles.RoleIds
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import dev.robothanzo.werewolf.game.roles.RoleTag
import net.dv8tion.jda.api.JDA
import org.springframework.stereotype.Service

/** Why a seat died — drives revenge arming and the 殉情 / 決鬥 interaction rules (ROLES.md). */
enum class DeathCause {
    NIGHT, POISON, EXPEL, KNIGHT_DUEL, SELF_DESTRUCT, JUDGE, LOVER, TEAM,
}

/** One applied death (a single identity card), returned so callers can log / announce / tally. */
data class DeathInfo(val seat: Int, val roleId: String, val cause: DeathCause)

/**
 * The single place a death is applied, shared by [NightOrchestrator], [GameActionService], and
 * [DayOrchestrator]. It marks the card dead and syncs the nickname, then runs the cross-cutting
 * ROLES.md rules in one place:
 *  - **death-revenge arming** (`DEATH_REVENGE`): 獵人 / 狼王 arm unless the death is suppressed
 *    (毒殺 / 殉情 / 決鬥 / 自爆 for 狼王); 白狼王 (`REVENGE_ON_SELF_DESTRUCT_ONLY`) arms only via 自爆.
 *  - **殉情 cascade** (`loverSeat` / 狼美人 `charmedSeat`): the bonded seat dies too, revenge
 *    suppressed — except a 騎士 duel on the 狼美人 spares the charmed seat.
 *  - **隱狼 auto-death**: with 隱狼繼承狼刀 off, a lone 隱狼 dies once no other wolf is left alive.
 *
 * Logging stays with the callers (which own the guild-scoped log + announcements); this returns the
 * full list of deaths it produced so they can render them.
 */
@Service
class DeathService(
    private val roles: RoleRegistry,
    private val nicknames: NicknameService,
    private val jda: JDA?,
) {

    fun applyDeath(
        session: GameSession,
        seatNumber: Int,
        card: IdentityCard,
        cause: DeathCause,
        suppressRevenge: Boolean = false,
    ): List<DeathInfo> {
        if (card.dead) return emptyList()
        val seat = session.seat(seatNumber) ?: return emptyList()
        card.dead = true
        syncNickname(session, seat)
        val out = mutableListOf(DeathInfo(seatNumber, card.roleId, cause))

        armRevenge(seat, card, cause, suppressRevenge)
        out += cascadeBonds(session, seat, cause)
        out += hiddenWolfAutoDeath(session)
        return out
    }

    /** Convenience: kill the first living identity of a seat (judge kill / expel / duel target). */
    fun killSeat(
        session: GameSession,
        seatNumber: Int,
        cause: DeathCause,
        suppressRevenge: Boolean = false,
    ): List<DeathInfo> {
        val seat = session.seat(seatNumber) ?: return emptyList()
        val card = seat.cards.firstOrNull { !it.dead } ?: return emptyList()
        return applyDeath(session, seatNumber, card, cause, suppressRevenge)
    }

    private fun armRevenge(seat: Seat, card: IdentityCard, cause: DeathCause, suppressRevenge: Boolean) {
        val role = roles.byId(card.roleId) ?: return
        if (!role.hasTag(RoleTag.DEATH_REVENGE)) return
        val arm = if (role.hasTag(RoleTag.REVENGE_ON_SELF_DESTRUCT_ONLY)) {
            cause == DeathCause.SELF_DESTRUCT // 白狼王 — 只有自爆可帶人
        } else {
            !suppressRevenge // 獵人 / 狼王 — 毒殺 / 殉情 / 決鬥 / 自爆(狼王) suppress the shot
        }
        if (arm) seat.revengePending = true
    }

    /** 殉情: kill the lover / 魅惑 victim bonded to the seat that just died (revenge suppressed). */
    private fun cascadeBonds(session: GameSession, seat: Seat, cause: DeathCause): List<DeathInfo> {
        val out = mutableListOf<DeathInfo>()
        val bonded = mutableSetOf<Int>()
        seat.loverSeat?.let { bonded += it }
        // 騎士 撞死狼美人 → 魅惑的人不會死 (ROLES.md 騎士 / 狼美人).
        val charmExempt = cause == DeathCause.KNIGHT_DUEL && seat.cards.any { it.roleId == RoleIds.WOLF_BEAUTY }
        if (!charmExempt) seat.charmedSeat?.let { bonded += it }
        for (b in bonded) {
            val bondedSeat = session.seat(b) ?: continue
            if (!bondedSeat.alive) continue
            out += killSeat(session, b, DeathCause.LOVER, suppressRevenge = true)
        }
        return out
    }

    /**
     * 隱狼 與狼隊共享勝利條件但互不相認: with 隱狼繼承狼刀 off it dies the moment no other wolf is
     * alive; with the setting on it survives as the last wolf and inherits the knife (Phase 6).
     */
    private fun hiddenWolfAutoDeath(session: GameSession): List<DeathInfo> {
        if (session.settings.hiddenWolfInheritsKnife) return emptyList()
        val hidden = session.aliveSeats().firstOrNull { s ->
            s.livingCards().any { it.roleId == RoleIds.HIDDEN_WOLF }
        } ?: return emptyList()
        val otherWolfAlive = session.aliveSeats().any { s ->
            s.number != hidden.number && s.livingCards().any { roles.factionOf(it.roleId) == Faction.WOLF }
        }
        if (otherWolfAlive) return emptyList()
        return killSeat(session, hidden.number, DeathCause.TEAM, suppressRevenge = true)
    }

    private fun syncNickname(session: GameSession, seat: Seat) {
        jda?.syncNickname(session, seat, nicknames.nicknameFor(seat))
    }
}
