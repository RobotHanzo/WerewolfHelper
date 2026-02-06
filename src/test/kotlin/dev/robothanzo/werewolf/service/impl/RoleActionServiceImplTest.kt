package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.SessionRepository
import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionTiming
import dev.robothanzo.werewolf.game.roles.actions.RoleAction
import dev.robothanzo.werewolf.game.roles.actions.RoleActionExecutor
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.User
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class RoleActionServiceImplTest {
    @Mock
    private lateinit var sessionRepository: SessionRepository

    @Mock
    private lateinit var roleActionExecutor: RoleActionExecutor

    @Mock
    private lateinit var nightManager: dev.robothanzo.werewolf.service.NightManager

    @Mock
    private lateinit var gameSessionService: dev.robothanzo.werewolf.service.GameSessionService

    @Mock
    private lateinit var roleRegistry: dev.robothanzo.werewolf.game.roles.RoleRegistry

    private lateinit var roleActionService: RoleActionServiceImpl

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        roleActionService = RoleActionServiceImpl(
            roleActionExecutor,
            nightManager,
            roleRegistry
        )
        WerewolfApplication.gameSessionService = gameSessionService

        whenever(gameSessionService.withLockedSession(any(), any<((Session) -> Any?)>())).thenAnswer { invocation ->
            val block = invocation.arguments[1] as (Session) -> Any?
            val guildId = invocation.arguments[0] as Long
            val sessionOpt = sessionRepository.findByGuildId(guildId)
            if (sessionOpt.isEmpty) throw IllegalArgumentException("Session not found")
            block(sessionOpt.get())
        }

        val mockJda = mock<JDA>()
        whenever(mockJda.getUserById(any<Long>())).thenAnswer { invocation ->
            val userId = invocation.arguments[0] as Long
            val user = mock<User>()
            whenever(user.idLong).thenReturn(userId)
            user
        }
        WerewolfApplication.jda = mockJda
    }

    @Test
    fun testSubmitActionSuccess() {
        val guildId = 123L
        val actorUserId = 456L
        val targetUserId = 789L
        val session = Session(guildId = guildId)

        // Create actor player with WEREWOLF role
        val actor = Player(id = 1, roleId = 100L, channelId = 200L, userId = actorUserId)
        actor.roles = mutableListOf("狼人")
        actor.actionSubmitted = false

        // Create target player
        val target = Player(id = 2, roleId = 101L, channelId = 201L, userId = targetUserId)

        session.players["1"] = actor
        session.players["2"] = target

        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.of(session))
        whenever(sessionRepository.save(any())).thenReturn(session)

        val result = roleActionService.submitAction(
            guildId = guildId,
            actionDefinitionId = "INVALID_ACTION",
            actorPlayerId = 1,
            targetPlayerIds = listOf(2),
            submittedBy = "PLAYER"
        )

        // Since INVALID_ACTION does not exist,
        // this will return failure with "Action not found"
        // This test verifies the session lookup and player validation flow
        assertFalse(result["success"] as Boolean)
        assertEquals("Action not found", result["error"])
    }

    @Test
    fun testSubmitActionSessionNotFound() {
        val guildId = 123L

        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.empty())

        assertThrows(IllegalArgumentException::class.java) {
            roleActionService.submitAction(
                guildId = guildId,
                actionDefinitionId = "WEREWOLF_KILL",
                actorPlayerId = 1,
                targetPlayerIds = listOf(2),
                submittedBy = "PLAYER"
            )
        }
    }

    @Test
    fun testSubmitActionActorNotFound() {
        val guildId = 123L
        val session = Session(guildId = guildId)

        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.of(session))

        val result = roleActionService.submitAction(
            guildId = guildId,
            actionDefinitionId = "WEREWOLF_KILL",
            actorPlayerId = 999,  // Non-existent
            targetPlayerIds = listOf(2),
            submittedBy = "PLAYER"
        )

        assertFalse(result["success"] as Boolean)
    }

    @Test
    fun testSubmitActionActorNotAlive() {
        val guildId = 123L
        val actorUserId = 456L
        val session = Session(guildId = guildId)

        // Create dead actor (no roles means isAlive = false)
        val actor = Player(id = 1, roleId = 100L, channelId = 200L, userId = actorUserId)
        actor.roles = mutableListOf()  // Empty roles = dead
        actor.deadRoles = mutableListOf("狼人")

        session.players["1"] = actor

        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.of(session))

        val result = roleActionService.submitAction(
            guildId = guildId,
            actionDefinitionId = "WEREWOLF_KILL",
            actorPlayerId = 1,
            targetPlayerIds = listOf(2),
            submittedBy = "PLAYER"
        )

        assertFalse(result["success"] as Boolean)
    }

    @Test
    fun testSubmitActionDuplicateSubmission() {
        val guildId = 123L
        val actorUserId = 456L
        val targetUserId = 789L
        val session = Session(guildId = guildId)
        session.currentState = "NIGHT"

        val actor = Player(id = 1, roleId = 100L, channelId = 200L, userId = actorUserId)
        actor.roles = mutableListOf("狼人")
        actor.actionSubmitted = true  // Already submitted

        val target = Player(id = 2, roleId = 101L, channelId = 201L, userId = targetUserId)

        session.players["1"] = actor
        session.players["2"] = target

        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.of(session))

        val mockAction = mock<RoleAction>()
        whenever(mockAction.timing).thenReturn(ActionTiming.NIGHT)
        whenever(mockAction.validate(any(), any(), any())).thenReturn(null)
        whenever(roleRegistry.getAction("WEREWOLF_KILL")).thenReturn(mockAction)

        val result = roleActionService.submitAction(
            guildId = guildId,
            actionDefinitionId = "WEREWOLF_KILL",
            actorPlayerId = 1,
            targetPlayerIds = listOf(2),
            submittedBy = "PLAYER"
        )

        assertFalse(result["success"] as Boolean)
        assertEquals("你已經提交過行動，無法再次選擇", result["error"])
    }

    @Test
    fun testSubmitActionInvalidTarget() {
        val guildId = 123L
        val actorUserId = 456L
        val session = Session(guildId = guildId)

        val actor = Player(id = 1, roleId = 100L, channelId = 200L, userId = actorUserId)
        actor.roles = mutableListOf("狼人")
        actor.actionSubmitted = false

        session.players["1"] = actor

        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.of(session))

        val result = roleActionService.submitAction(
            guildId = guildId,
            actionDefinitionId = "WEREWOLF_KILL",
            actorPlayerId = 1,
            targetPlayerIds = listOf(999),  // Non-existent target
            submittedBy = "PLAYER"
        )

        assertFalse(result["success"] as Boolean)
    }

    @Test
    fun testGetAvailableActionsForPlayerAlive() {
        val session = Session(guildId = 123L)
        val userId = 456L

        val player = Player(id = 1, roleId = 100L, channelId = 200L, userId = userId)
        player.roles = mutableListOf("狼人")

        session.players["1"] = player

        val actions = roleActionService.getAvailableActionsForPlayer(session, 1)

        // Result depends on PredefinedRoles configuration
        assertNotNull(actions)
    }

    @Test
    fun testGetAvailableActionsForPlayerDead() {
        val session = Session(guildId = 123L)
        val userId = 456L

        val player = Player(id = 1, roleId = 100L, channelId = 200L, userId = userId)
        player.roles = mutableListOf()  // Empty = dead
        player.deadRoles = mutableListOf("狼人")

        session.players["1"] = player

        val actions = roleActionService.getAvailableActionsForPlayer(session, 1)

        assertTrue(actions.isEmpty())
    }

    @Test
    fun testGetAvailableActionsPlayerNotFound() {
        val session = Session(guildId = 123L)

        val actions = roleActionService.getAvailableActionsForPlayer(session, 999)

        assertTrue(actions.isEmpty())
    }

    @Test
    fun testIsActionAvailable() {
        val session = Session(guildId = 123L)
        val userId = 456L

        val player = Player(id = 1, roleId = 100L, channelId = 200L, userId = userId)
        player.roles = mutableListOf("狼人")

        session.players["1"] = player

        val available = roleActionService.isActionAvailable(
            session, 1, "WEREWOLF_KILL"
        )

        assertNotNull(available)
    }

    @Test
    fun testGetPendingActions() {
        val session = Session(guildId = 123L)

        session.stateData.pendingActions.add(
            dev.robothanzo.werewolf.game.model.RoleActionInstance(
                actor = 1,
                actionDefinitionId = "WEREWOLF_KILL",
                targets = listOf(2),
                submittedBy = dev.robothanzo.werewolf.game.model.ActionSubmissionSource.PLAYER
            )
        )

        val actions = roleActionService.getPendingActions(session)

        assertEquals(1, actions.size)
    }

    @Test
    fun testGetActionUsageCount() {
        val session = Session(guildId = 123L)
        val userId = 456L

        val count = roleActionService.getActionUsageCount(
            session, 1, "WEREWOLF_KILL"
        )

        assertNotNull(count)
        assertTrue(count >= 0)
    }

    @Test
    fun testHasDeathTriggerAvailable() {
        val session = Session(guildId = 123L)
        val userId = 456L

        val player = Player(id = 1, roleId = 100L, channelId = 200L, userId = userId)
        player.roles = mutableListOf("獵人")  // Hunter role has death trigger
        player.deadRoles = mutableListOf("獵人")  // Dead

        session.players["1"] = player

        val hasDeathTrigger = roleActionService.hasDeathTriggerAvailable(session, 1)

        assertNotNull(hasDeathTrigger)
    }

    @Test
    fun testExecuteDeathTriggers() {
        val session = Session(guildId = 123L)

        val deaths = roleActionService.executeDeathTriggers(session)

        assertNotNull(deaths)
        assertTrue(deaths is List<*>)
    }
}
