package dev.robothanzo.werewolf.game

import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.*
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import dev.robothanzo.werewolf.game.roles.actions.RoleAction
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NightmareFearVerificationTests {
    private lateinit var session: Session
    private lateinit var roleRegistry: RoleRegistry

    @BeforeEach
    fun setup() {
        session = Session(guildId = 123L)
        roleRegistry = mock()
    }

    @Test
    fun `isActionAvailable should return FALSE when player is feared tonight`() {
        println("DEBUG: Running testPlayerFearedTonight")
        val victimId = 2
        val nightmareId = 1

        // Mock player
        val player = Player(id = victimId, roleId = 222L, userId = 222L, channelId = 22200L)
        player.roles = mutableListOf("預言家")
        session.players["2"] = player

        // Setup Nightmare fear action in submittedActions
        val fearAction = RoleActionInstance(
            actor = nightmareId,
            actorRole = "夢魘",
            actionDefinitionId = ActionDefinitionId.NIGHTMARE_FEAR,
            targets = mutableListOf(victimId),
            submittedBy = ActionSubmissionSource.PLAYER,
            status = ActionStatus.PROCESSED // Should be PROCESSED after Nightmare phase
        )
        session.stateData.submittedActions.add(fearAction)

        // We need to set up the executed status for our new helper to find it
        fearAction.status = ActionStatus.PROCESSED

        session.currentState = "NIGHT_STEP"
        session.day = 1

        // Mock roles and actions for the check
        val seerRole = mock<Role>()
        val seerAction = mock<RoleAction>()
        whenever(seerAction.actionId).thenReturn(ActionDefinitionId.SEER_CHECK)
        whenever(seerAction.timing).thenReturn(ActionTiming.NIGHT)
        whenever(seerRole.getActions()).thenReturn(listOf(seerAction))

        whenever(roleRegistry.getAction(ActionDefinitionId.SEER_CHECK)).thenReturn(seerAction)
        whenever(roleRegistry.getRole("預言家")).thenReturn(seerRole)

        // The check
        val result = session.isActionAvailable(victimId, ActionDefinitionId.SEER_CHECK, roleRegistry)

        println("DEBUG: isActionAvailable result for feared player: $result")
        assertFalse(result, "Feared player should have no available actions tonight")
    }

    @Test
    fun `isActionAvailable should return TRUE when player was feared last night but NOT tonight`() {
        println("DEBUG: Running testPlayerFearedLastNight")
        val victimId = 3
        val nightmareId = 1

        session.day = 2
        session.currentState = "NIGHT_STEP"

        // Mock player
        val player = Player(id = victimId, roleId = 333L, userId = 333L, channelId = 33300L)
        player.roles = mutableListOf("預言家")
        session.players["3"] = player

        // Set up Nightmare fear action in executedActions for Day 1
        val fearAction = RoleActionInstance(
            actor = nightmareId,
            actorRole = "夢魘",
            actionDefinitionId = ActionDefinitionId.NIGHTMARE_FEAR,
            targets = mutableListOf(victimId),
            submittedBy = ActionSubmissionSource.PLAYER,
            status = ActionStatus.PROCESSED
        )
        session.stateData.executedActions[1] = mutableListOf(fearAction)

        // Mock roles and actions
        val seerRole = mock<Role>()
        val seerAction = mock<RoleAction>()
        whenever(seerAction.actionId).thenReturn(ActionDefinitionId.SEER_CHECK)
        whenever(seerAction.timing).thenReturn(ActionTiming.NIGHT)
        whenever(seerAction.usageLimit).thenReturn(-1)
        whenever(seerRole.getActions()).thenReturn(listOf(seerAction))

        whenever(roleRegistry.getAction(ActionDefinitionId.SEER_CHECK)).thenReturn(seerAction)
        whenever(roleRegistry.getRole("預言家")).thenReturn(seerRole)

        // The check
        val result = session.isActionAvailable(victimId, ActionDefinitionId.SEER_CHECK, roleRegistry)

        println("DEBUG: isActionAvailable result for player feared LAST night: $result")
        assertTrue(result, "Player feared last night should have actions tonight")
    }
}
