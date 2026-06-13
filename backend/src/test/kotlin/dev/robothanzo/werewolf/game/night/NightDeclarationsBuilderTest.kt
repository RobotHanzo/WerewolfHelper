package dev.robothanzo.werewolf.game.night

import dev.robothanzo.werewolf.domain.NightIntentData
import dev.robothanzo.werewolf.domain.NightState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NightDeclarationsBuilderTest {

    private val builder = NightDeclarationsBuilder()

    @Test
    fun `wolf consensus picks the most-voted target`() {
        assertEquals(9, builder.wolfConsensus(mapOf(1 to 9, 3 to 9, 8 to 12)))
    }

    @Test
    fun `wolf consensus breaks ties toward the lowest seat`() {
        assertEquals(7, builder.wolfConsensus(mapOf(1 to 7, 3 to 12)))
    }

    @Test
    fun `wolf consensus is null when everyone declines`() {
        assertNull(builder.wolfConsensus(mapOf(1 to -1, 3 to -1)))
        assertNull(builder.wolfConsensus(emptyMap()))
    }

    @Test
    fun `builds declarations from submitted intents and wolf votes`() {
        val night = NightState(
            active = true,
            wolfParticipants = mutableSetOf(1, 3),
            wolfVotes = mutableMapOf(1 to 9, 3 to 9),
            intents = mutableListOf(
                NightIntentData("guard.protect", "guard", mutableListOf(7), mutableListOf(2)),
                NightIntentData("seer.investigate", "seer", mutableListOf(2), mutableListOf(12)),
                NightIntentData("witch.potion", "witch", mutableListOf(5), meta = mutableMapOf("save" to 1)),
            ),
        )
        val d = builder.build(night)
        assertEquals(9, d.wolfKill?.target)
        assertEquals(setOf(1, 3), d.wolfKill?.actors)
        assertEquals(2, d.guard?.target)
        // witch chose "save" → saves the wolves' target (9)
        assertEquals(9, d.witchSave)
        assertEquals(5, d.witchActor)
    }

    @Test
    fun `witch poison maps to a poison target`() {
        val night = NightState(
            intents = mutableListOf(
                NightIntentData("witch.potion", "witch", mutableListOf(5), meta = mutableMapOf("poison" to 6)),
            ),
        )
        val d = builder.build(night)
        assertEquals(6, d.witchPoison)
        assertNull(d.witchSave)
    }

    @Test
    fun `skipped intents are dropped`() {
        val night = NightState(
            intents = mutableListOf(NightIntentData("guard.protect", "guard", mutableListOf(7), skipped = true)),
        )
        assertNull(builder.build(night).guard)
    }
}
