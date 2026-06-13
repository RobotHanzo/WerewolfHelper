package dev.robothanzo.werewolf.game.roles

import dev.robothanzo.werewolf.domain.Faction
import dev.robothanzo.werewolf.support.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoleRegistryTest {

    private val registry = TestFixtures.registry()

    @Test
    fun `all 27 canonical identities are registered`() {
        assertEquals(27, registry.all.size)
    }

    @Test
    fun `every role has a unique stable id`() {
        val ids = registry.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `names resolve to zh-TW`() {
        assertEquals("狼人", registry.localizedName(RoleIds.WOLF))
        assertEquals("預言家", registry.localizedName(RoleIds.SEER))
        assertEquals("平民", registry.localizedName(RoleIds.VILLAGER))
    }

    @Test
    fun `faction classification follows FEATURES section 2`() {
        // name contains 狼
        assertEquals(Faction.WOLF, registry.factionOf(RoleIds.WOLF))
        assertEquals(Faction.WOLF, registry.factionOf(RoleIds.WOLF_KING))
        assertEquals(Faction.WOLF, registry.factionOf(RoleIds.MECHANIC_WOLF))
        // wolf exceptions (no 狼 in name)
        assertEquals(Faction.WOLF, registry.factionOf(RoleIds.GARGOYLE))
        assertEquals(Faction.WOLF, registry.factionOf(RoleIds.BLOOD_MOON))
        assertEquals(Faction.WOLF, registry.factionOf(RoleIds.EVIL_KNIGHT))
        // villager
        assertEquals(Faction.VILLAGER, registry.factionOf(RoleIds.VILLAGER))
        // gods
        assertEquals(Faction.GOD, registry.factionOf(RoleIds.SEER))
        assertEquals(Faction.GOD, registry.factionOf(RoleIds.NIGHTMARE))
    }

    @Test
    fun `unknown id falls back to name rule`() {
        assertEquals(Faction.WOLF, registry.factionOf("狼人"))
        assertEquals(Faction.VILLAGER, registry.factionOf("平民"))
        assertEquals(Faction.GOD, registry.factionOf("某神職"))
    }

    @Test
    fun `wolf-chat team includes wolves and the nightmare`() {
        assertTrue(registry.require(RoleIds.WOLF).hasTag(RoleTag.WOLF_CHAT))
        assertTrue(registry.require(RoleIds.NIGHTMARE).hasTag(RoleTag.WOLF_CHAT))
        assertTrue(!registry.require(RoleIds.SEER).hasTag(RoleTag.WOLF_CHAT))
    }

    @Test
    fun `autocomplete is capped at 25 entries`() {
        assertTrue(registry.autocompleteNames("").size <= 25)
    }

    @Test
    fun `lookup by localized name works`() {
        assertNotNull(registry.byName("女巫"))
        assertEquals(RoleIds.WITCH, registry.byName("女巫")!!.id)
    }
}
