package dev.robothanzo.werewolf.game

import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.*
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import dev.robothanzo.werewolf.game.roles.actions.ActionExecutionResult
import dev.robothanzo.werewolf.game.roles.actions.DeathResolutionAction
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class DreamweaverProtectionVerificationTests {
    private lateinit var session: Session
    private lateinit var roleRegistry: RoleRegistry
    private lateinit var deathResolution: DeathResolutionAction

    @BeforeEach
    fun setup() {
        session = Session(guildId = 123L)
        roleRegistry = mock()
        deathResolution = DeathResolutionAction()
    }

    @Test
    fun `DeathResolution should IMMUNIZE current night sleepwalker from werewolf kill`() {
        val victimId = 2
        val dreamweaverId = 1

        session.day = 1
        session.currentState = "NIGHT_STEP"

        // Mock Dreamweaver
        val dwPlayer = Player(id = dreamweaverId, roleId = 111L, userId = 111L, channelId = 11100L)
        dwPlayer.roles = mutableListOf("攝夢人")
        session.players["1"] = dwPlayer

        // Mock target
        val victimPlayer = Player(id = victimId, roleId = 222L, userId = 222L, channelId = 22200L)
        session.players["2"] = victimPlayer

        // Setup Dreamweaver link action in submittedActions
        val linkAction = RoleActionInstance(
            actor = dreamweaverId,
            actorRole = "攝夢人",
            actionDefinitionId = ActionDefinitionId.DREAM_WEAVER_LINK,
            targets = mutableListOf(victimId),
            submittedBy = ActionSubmissionSource.PLAYER,
            status = ActionStatus.PROCESSED
        )
        session.stateData.submittedActions.add(linkAction)

        // Setup a Werewolf Kill on the same target
        val accumulatedState = ActionExecutionResult()
        accumulatedState.deaths[DeathCause.WEREWOLF] = mutableListOf(victimId)

        // Run Death Resolution
        val result = deathResolution.execute(session, mock(), accumulatedState)

        // The target should be REMOVED from deaths because of Dreamweaver immunity
        val werewolfDeaths = result.deaths[DeathCause.WEREWOLF] ?: emptyList<Int>()
        assertFalse(werewolfDeaths.contains(victimId), "Sleepwalker should be immune to werewolf kill tonight")
    }

    @Test
    fun `DeathResolution should KILL sleepwalker if linked two nights in a row`() {
        val victimId = 3
        val dreamweaverId = 1

        session.day = 2
        session.currentState = "NIGHT_STEP"

        // Mock Dreamweaver
        val dwPlayer = Player(id = dreamweaverId, roleId = 111L, userId = 111L, channelId = 11100L)
        dwPlayer.roles = mutableListOf("攝夢人")
        session.players["1"] = dwPlayer

        // Setup Dreamweaver link action in executedActions for Day 1
        val linkActionDay1 = RoleActionInstance(
            actor = dreamweaverId,
            actorRole = "攝夢人",
            actionDefinitionId = ActionDefinitionId.DREAM_WEAVER_LINK,
            targets = mutableListOf(victimId),
            submittedBy = ActionSubmissionSource.PLAYER,
            status = ActionStatus.PROCESSED
        )
        session.stateData.executedActions[1] = mutableListOf(linkActionDay1)

        // Setup Dreamweaver link action in submittedActions for Day 2
        val linkActionDay2 = RoleActionInstance(
            actor = dreamweaverId,
            actorRole = "攝夢人",
            actionDefinitionId = ActionDefinitionId.DREAM_WEAVER_LINK,
            targets = mutableListOf(victimId),
            submittedBy = ActionSubmissionSource.PLAYER,
            status = ActionStatus.PROCESSED
        )
        session.stateData.submittedActions.add(linkActionDay2)

        val accumulatedState = ActionExecutionResult()

        // Run Death Resolution
        val result = deathResolution.execute(session, mock(), accumulatedState)

        // The target should be ADDED to deaths because of consecutive links
        val dreamDeaths = result.deaths[DeathCause.DREAM_WEAVER] ?: emptyList<Int>()
        assertTrue(dreamDeaths.contains(victimId), "Sleepwalker should die if linked two nights in a row")
    }
}
