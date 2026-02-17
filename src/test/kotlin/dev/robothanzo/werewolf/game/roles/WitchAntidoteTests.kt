package dev.robothanzo.werewolf.game.roles

import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.*
import dev.robothanzo.werewolf.game.roles.actions.*
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WitchAntidoteTests {
    private lateinit var session: Session
    private lateinit var roleRegistry: RoleRegistry
    private lateinit var executor: RoleActionExecutor

    @BeforeEach
    fun setup() {
        session = Session()
        session.sessionId = "test-session"
        session.day = 1

        // Mock Roles
        val witchRole = object : BaseRole("女巫", Camp.GOD) {
            override fun getActions() = listOf(WitchAntidoteAction(), WitchPoisonAction())
        }
        val werewolfRole = object : BaseRole("狼人", Camp.WEREWOLF) {}
        val villagerRole = object : BaseRole("平民", Camp.VILLAGER) {}
        val ghostRiderRole = object : BaseRole("惡靈騎士", Camp.WEREWOLF) {}

        roleRegistry = RoleRegistry(
            listOf(witchRole, werewolfRole, villagerRole, ghostRiderRole),
            listOf(
                WerewolfKillAction(),
                WitchAntidoteAction(),
                WitchPoisonAction(),
                GuardProtectAction(),
                DeathResolutionAction()
            )
        )
        executor = RoleActionExecutor(roleRegistry)

        // Setup Players
        session.players["1"] = Player(id = 1, roles = mutableListOf("狼人"))
        session.players["2"] = Player(id = 2, roles = mutableListOf("女巫"))
        session.players["3"] = Player(id = 3, roles = mutableListOf("平民"))
        session.players["4"] = Player(id = 4, roles = mutableListOf("惡靈騎士"))

        session.players.values.forEach { it.session = session }
    }

    @Test
    fun testWitchAntidoteSavesVillager() {
        val wolfKill = RoleActionInstance(
            actor = 1,
            actorRole = "狼人",
            actionDefinitionId = ActionDefinitionId.WEREWOLF_KILL,
            targets = mutableListOf(3),
            submittedBy = ActionSubmissionSource.PLAYER,
            status = ActionStatus.SUBMITTED
        )
        val antidote = RoleActionInstance(
            actor = 2,
            actorRole = "女巫",
            actionDefinitionId = ActionDefinitionId.WITCH_ANTIDOTE,
            targets = mutableListOf(3),
            submittedBy = ActionSubmissionSource.PLAYER,
            status = ActionStatus.SUBMITTED
        )

        val result = executor.executeActions(session, listOf(wolfKill, antidote))

        assertFalse(result.deaths.values.flatten().contains(3), "Villager should be saved by Antidote")
        assertTrue(result.saved.contains(3), "Villager ID should be in saved list")
    }

    @Test
    fun testWitchAntidoteOnGhostRider() {
        // Wolves target GR (though they shouldn't usually, but for testing logic)
        val wolfKill = RoleActionInstance(
            actor = 1,
            actorRole = "狼人",
            actionDefinitionId = ActionDefinitionId.WEREWOLF_KILL,
            targets = mutableListOf(4),
            submittedBy = ActionSubmissionSource.PLAYER,
            status = ActionStatus.SUBMITTED
        )
        // Witch tries to save GR
        val antidote = RoleActionInstance(
            actor = 2,
            actorRole = "女巫",
            actionDefinitionId = ActionDefinitionId.WITCH_ANTIDOTE,
            targets = mutableListOf(4),
            submittedBy = ActionSubmissionSource.PLAYER,
            status = ActionStatus.SUBMITTED
        )

        val result = executor.executeActions(session, listOf(wolfKill, antidote))

        // According to current logic: Witch targets GR -> Reflect!
        assertTrue(result.deaths[DeathCause.REFLECT]?.contains(2) == true, "Witch should be reflected")
        // The antidote should NOT have worked because reflection returns accumulatedState (skipping the action)
        assertFalse(result.saved.contains(4), "Antidote should have been skipped due to reflection")

        // GR is immune to wolf kill at night anyway
        assertFalse(result.deaths.values.flatten().contains(4), "Ghost Rider should be immune to wolf kill at night")
    }
}
