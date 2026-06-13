package dev.robothanzo.werewolf.game.night

import org.springframework.stereotype.Service

/** One layer of the night plan; every ability in a wave is independent and prompts simultaneously. */
data class NightWave(val index: Int, val abilities: List<NightAbility>)

/** The full ordered plan for one night. */
data class NightPlan(val waves: List<NightWave>) {
    val abilityCount: Int get() = waves.sumOf { it.abilities.size }
    fun abilityIds(): List<List<String>> = waves.map { wave -> wave.abilities.map { it.id } }
}

/** Raised when the read/write declarations form a dependency cycle (fail fast — caught by tests). */
class NightCycleException(message: String) : RuntimeException(message)

/**
 * Turns a set of active night abilities into ordered **waves** from their [Effect] read/write
 * declarations, replacing the old linear `priority: Int`.
 *
 * Ability B depends on A when `B.reads ∩ A.writes ≠ ∅`. The longest-path layering below places each
 * ability in the earliest wave after all its dependencies, so independent abilities (the wolves,
 * the seer, the guard…) share a wave and act at the same time, while dependents (the witch, who
 * reads `WOLF_KILL`) fall into a later wave automatically.
 */
@Service
class NightPlanner {

    fun plan(active: List<NightAbility>): NightPlan {
        if (active.isEmpty()) return NightPlan(emptyList())

        // Edge A -> B when B reads a channel A writes (A != B).
        val dependsOn: Map<NightAbility, Set<NightAbility>> = active.associateWith { b ->
            active.filterTo(mutableSetOf()) { a -> a !== b && b.reads.intersect(a.writes).isNotEmpty() }
        }

        val indegree = active.associateWith { dependsOn.getValue(it).size }.toMutableMap()
        val level = active.associateWith { 0 }.toMutableMap()

        // Kahn's algorithm with longest-path levels.
        val ready = ArrayDeque(active.filter { indegree.getValue(it) == 0 })
        var processed = 0
        while (ready.isNotEmpty()) {
            val u = ready.removeFirst()
            processed++
            for (v in active) {
                if (u in dependsOn.getValue(v)) {
                    level[v] = maxOf(level.getValue(v), level.getValue(u) + 1)
                    indegree[v] = indegree.getValue(v) - 1
                    if (indegree.getValue(v) == 0) ready.addLast(v)
                }
            }
        }

        if (processed < active.size) {
            val unresolved = active.filter { indegree.getValue(it) > 0 }.map { it.id }
            throw NightCycleException("Night ability dependency cycle among: $unresolved")
        }

        return NightPlan(
            level.entries
                .groupBy({ it.value }, { it.key })
                .toSortedMap()
                .map { (lvl, abilities) -> NightWave(lvl, abilities.sortedBy { it.id }) },
        )
    }
}
