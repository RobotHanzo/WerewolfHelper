package dev.robothanzo.werewolf.game.day

import dev.robothanzo.werewolf.domain.Faction
import dev.robothanzo.werewolf.domain.GameSession
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import org.springframework.stereotype.Service

/** Outcome of a 騎士 決鬥: who dies, and whether the target was a wolf (which forces night). */
data class DuelResult(val targetIsWolf: Boolean, val deadSeat: Int)

/**
 * Pure resolution of the 騎士 決鬥 (ROLES.md 騎士): if the dueled seat is wolf-faction the target
 * dies (and the day ends, entering night); otherwise the knight dies "以死謝罪" and the day
 * continues. The death side-effects + phase transition are applied by `DayOrchestrator`.
 */
@Service
class DuelResolver(private val roles: RoleRegistry) {

    fun resolve(session: GameSession, knightSeat: Int, targetSeat: Int): DuelResult {
        val target = session.seat(targetSeat)
        val card = target?.livingCards()?.firstOrNull() ?: target?.cards?.firstOrNull()
        val faction = when {
            target == null || card == null -> Faction.GOD
            target.clone -> Faction.GOD
            else -> roles.factionOf(card.roleId)
        }
        val targetIsWolf = faction == Faction.WOLF
        return DuelResult(targetIsWolf, if (targetIsWolf) targetSeat else knightSeat)
    }
}
