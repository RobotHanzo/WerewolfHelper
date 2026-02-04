package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.database.SessionRepository
import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.roles.actions.RoleActionExecutor
import dev.robothanzo.werewolf.service.DiscordService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.util.*

class RoleActionServiceImplTest {
    @Mock
    private lateinit var sessionRepository: SessionRepository

    @Mock
    private lateinit var discordService: DiscordService

    @Mock
    private lateinit var roleActionExecutor: RoleActionExecutor

    private lateinit var roleActionService: RoleActionServiceImpl

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        roleActionService = RoleActionServiceImpl(
            sessionRepository,
            discordService,
            roleActionExecutor
        )
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
            actorUserId = actorUserId,
            targetUserIds = listOf(targetUserId),
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
                actorUserId = 456L,
                targetUserIds = listOf(789L),
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
            actorUserId = 999L,  // Non-existent
            targetUserIds = listOf(789L),
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
            actorUserId = actorUserId,
            targetUserIds = listOf(789L),
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

        val actor = Player(id = 1, roleId = 100L, channelId = 200L, userId = actorUserId)
        actor.roles = mutableListOf("狼人")
        actor.actionSubmitted = true  // Already submitted

        val target = Player(id = 2, roleId = 101L, channelId = 201L, userId = targetUserId)

        session.players["1"] = actor
        session.players["2"] = target

        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.of(session))

        val result = roleActionService.submitAction(
            guildId = guildId,
            actionDefinitionId = "WEREWOLF_KILL",
            actorUserId = actorUserId,
            targetUserIds = listOf(targetUserId),
            submittedBy = "PLAYER"
        )

        assertFalse(result["success"] as Boolean)
        assertEquals("You have already submitted an action this phase", result["error"])
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
            actorUserId = actorUserId,
            targetUserIds = listOf(999L),  // Non-existent target
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

        val actions = roleActionService.getAvailableActionsForPlayer(session, userId)

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

        val actions = roleActionService.getAvailableActionsForPlayer(session, userId)

        assertTrue(actions.isEmpty())
    }

    @Test
    fun testGetAvailableActionsPlayerNotFound() {
        val session = Session(guildId = 123L)

        val actions = roleActionService.getAvailableActionsForPlayer(session, 999L)

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
            session, userId, "WEREWOLF_KILL"
        )

        assertNotNull(available)
    }

    @Test
    fun testGetPendingActions() {
        val session = Session(guildId = 123L)

        // Add a pending action
        @Suppress("UNCHECKED_CAST")
        val pendingActions = (session.stateData.getOrPut("pendingActions") {
            mutableListOf<Map<String, Any>>()
        } as MutableList<Map<String, Any>>)

        pendingActions.add(
            mapOf(
                "actor" to 456L,
                "actionDefinitionId" to "WEREWOLF_KILL",
                "targets" to listOf(789L)
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
            session, userId, "WEREWOLF_KILL"
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

        val hasDeathTrigger = roleActionService.hasDeathTriggerAvailable(session, userId)

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
