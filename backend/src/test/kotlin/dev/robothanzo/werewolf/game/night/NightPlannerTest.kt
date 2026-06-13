package dev.robothanzo.werewolf.game.night

import dev.robothanzo.werewolf.game.night.abilities.GuardProtect
import dev.robothanzo.werewolf.game.night.abilities.MagicianSwap
import dev.robothanzo.werewolf.game.night.abilities.NightmareFear
import dev.robothanzo.werewolf.game.night.abilities.PsychicInvestigate
import dev.robothanzo.werewolf.game.night.abilities.SeerInvestigate
import dev.robothanzo.werewolf.game.night.abilities.WitchPotion
import dev.robothanzo.werewolf.game.night.abilities.WolfKill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NightPlannerTest {

    private val planner = NightPlanner()

    /** A minimal test ability for cycle/independence checks. */
    private fun ability(id: String, reads: Set<Effect>, writes: Set<Effect>): NightAbility =
        object : NightAbility {
            override val id = id
            override val roleId = id
            override val reads = reads
            override val writes = writes
        }

    @Test
    fun `canonical board layers into the expected waves`() {
        val plan = planner.plan(
            listOf(WitchPotion(), WolfKill(), SeerInvestigate(), GuardProtect(), NightmareFear(), MagicianSwap(), PsychicInvestigate()),
        )

        val ids = plan.abilityIds()
        // wave 0: magician swap (reads nothing)
        assertEquals(listOf("magician.swap"), ids[0])
        // wave 1: nightmare fear (reads SWAP)
        assertEquals(listOf("nightmare.fear"), ids[1])
        // wave 2: the independent actions (read SWAP+FEAR, write distinct channels) run together
        assertEquals(
            listOf("guard.protect", "psychic.investigate", "seer.investigate", "wolf.kill"),
            ids[2],
        )
        // wave 3: witch (reads WOLF_KILL) lands strictly after the wolves
        assertEquals(listOf("witch.potion"), ids[3])
    }

    @Test
    fun `independent abilities share a wave (simultaneity)`() {
        val plan = planner.plan(listOf(WolfKill(), GuardProtect(), SeerInvestigate()))
        // none reads another's writes → all in wave 0
        assertEquals(1, plan.waves.size)
        assertEquals(3, plan.waves[0].abilities.size)
    }

    @Test
    fun `the witch always lands after the wolves`() {
        val plan = planner.plan(listOf(WitchPotion(), WolfKill()))
        val witchWave = plan.waves.first { w -> w.abilities.any { it.id == "witch.potion" } }.index
        val wolfWave = plan.waves.first { w -> w.abilities.any { it.id == "wolf.kill" } }.index
        assertTrue(wolfWave < witchWave)
    }

    @Test
    fun `dependency cycles fail fast`() {
        val a = ability("a", reads = setOf(Effect.SWAP), writes = setOf(Effect.FEAR))
        val b = ability("b", reads = setOf(Effect.FEAR), writes = setOf(Effect.SWAP))
        assertThrows(NightCycleException::class.java) { planner.plan(listOf(a, b)) }
    }

    @Test
    fun `empty plan is handled`() {
        assertTrue(planner.plan(emptyList()).waves.isEmpty())
    }
}
