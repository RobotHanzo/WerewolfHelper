package dev.robothanzo.werewolf.game.night

import dev.robothanzo.werewolf.domain.NightState
import org.springframework.stereotype.Service

/**
 * Folds the collected Discord submissions (the persisted [NightState]) into the [NightDeclarations]
 * the [NightResolver] consumes — including resolving the wolves' button votes into one kill target.
 * Pure and unit-tested.
 */
@Service
class NightDeclarationsBuilder {

    /** The wolves' agreed target: the most-voted seat (ties → lowest seat), or null if all declined. */
    fun wolfConsensus(votes: Map<Int, Int>): Int? {
        val targets = votes.values.filter { it >= 0 }
        if (targets.isEmpty()) return null
        val counts = targets.groupingBy { it }.eachCount()
        val max = counts.values.max()
        return counts.filterValues { it == max }.keys.min()
    }

    fun build(night: NightState): NightDeclarations {
        val byId = night.intents.associateBy { it.abilityId }
        fun action(id: String): NightAction? =
            byId[id]?.takeIf { !it.skipped }?.let { NightAction(it.actorSeats.toSet(), it.targets.firstOrNull()) }

        val swap = byId["magician.swap"]?.takeIf { !it.skipped && it.targets.size >= 2 }?.let { it.targets[0] to it.targets[1] }
        val fear = byId["nightmare.fear"]?.takeIf { !it.skipped }
        val wolfTarget = wolfConsensus(night.wolfVotes)
        val wolfKill = if (night.wolfParticipants.isNotEmpty()) NightAction(night.wolfParticipants, wolfTarget) else null
        val witch = byId["witch.potion"]?.takeIf { !it.skipped }
        val witchSave = if (witch?.meta?.get("save") == 1) wolfTarget else null
        val witchPoison = witch?.meta?.get("poison")

        return NightDeclarations(
            swap = swap,
            fearActor = fear?.actorSeats?.firstOrNull(),
            fearTarget = fear?.targets?.firstOrNull(),
            wolfKill = wolfKill,
            guard = action("guard.protect"),
            witchActor = witch?.actorSeats?.firstOrNull(),
            witchSave = witchSave,
            witchPoison = witchPoison,
            charm = action("wolf_beauty.charm"),
            hunt = action("demon_hunter.hunt"),
        )
    }
}
