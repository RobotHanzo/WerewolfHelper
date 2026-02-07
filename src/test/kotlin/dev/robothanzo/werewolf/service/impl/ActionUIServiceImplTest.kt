package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionStatus
import dev.robothanzo.werewolf.game.model.ActionTiming
import dev.robothanzo.werewolf.game.model.RoleActionInstance
import dev.robothanzo.werewolf.game.roles.actions.ActionExecutionResult
import dev.robothanzo.werewolf.game.roles.actions.RoleAction
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.NightManager
import dev.robothanzo.werewolf.service.RoleActionService
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

class ActionUIServiceImplTest {
    @Mock
    private lateinit var nightManager: NightManager

    @Mock
    private lateinit var gameSessionService: GameSessionService

    @Mock
    private lateinit var roleActionService: RoleActionService

    @Mock
    private lateinit var mockChannel: TextChannel

    private lateinit var actionUIService: ActionUIServiceImpl

    // Helper to create a mock RoleAction
    private fun createMockRoleAction(
        id: String,
        name: String,
        optional: Boolean,
        targets: (Session, Int, List<Int>) -> List<Int>
    ): RoleAction {
        return object : RoleAction {
            override val actionId: String = id
            override val actionName: String = name
            override val priority: Int = 100
            override val timing: ActionTiming = ActionTiming.NIGHT
            override val targetCount: Int = 1
            override val usageLimit: Int = -1
            override val isOptional: Boolean = optional

            override fun execute(
                session: Session,
                action: RoleActionInstance,
                accumulatedState: ActionExecutionResult
            ): ActionExecutionResult {
                return accumulatedState
            }

            override fun eligibleTargets(
                session: Session,
                actor: Int,
                alivePlayers: List<Int>,
                accumulatedState: ActionExecutionResult
            ): List<Int> {
                return targets(session, actor, alivePlayers)
            }
        }
    }

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        actionUIService = ActionUIServiceImpl(nightManager)

        // Mock static service access
        WerewolfApplication.gameSessionService = gameSessionService
        WerewolfApplication.roleActionService = roleActionService
    }

    @Test
    @DisplayName("Wolf Younger Brother picks random target on timeout")
    fun testWolfBrotherRandomTarget() {
        val session = Session(guildId = 1L)
        session.stateData.phaseEndTime = System.currentTimeMillis() - 1000 // Ensure phase expired

        val wbId = 1
        val targetId = 2

        // Create pending action for Wolf Younger Brother extra kill
        val pendingAction = RoleActionInstance(
            actor = wbId,
            actorRole = "狼弟",
            actionDefinitionId = "WOLF_YOUNGER_BROTHER_EXTRA_KILL",
            targets = emptyList(),
            submittedBy = dev.robothanzo.werewolf.game.model.ActionSubmissionSource.SYSTEM,
            status = ActionStatus.ACTING
        )
        session.stateData.submittedActions.add(pendingAction)

        // Mock player and channel
        val player = Player(id = wbId, roleId = 100L, channelId = 200L)
        player.roles = mutableListOf("狼弟")
        player.session = session

        val villager = Player(id = targetId, roleId = 101L, channelId = 201L)
        villager.roles = mutableListOf("平民")
        villager.session = session

        // Mock JDA to return channel for player
        val mockJda = mock<net.dv8tion.jda.api.JDA>()
        whenever(mockJda.getTextChannelById(200L)).thenReturn(mockChannel)
        whenever(mockJda.getTextChannelById(201L)).thenReturn(mockChannel)
        WerewolfApplication.jda = mockJda

        val playerMap = mutableMapOf(
            wbId.toString() to player,
            targetId.toString() to villager
        )

        // Reflection to set players map since it's cleaner than heavy mocking of Session
        val playersField = Session::class.java.getDeclaredField("players")
        playersField.isAccessible = true
        playersField.set(session, playerMap)

        // Mock roleActionService responses using the helper
        val mandatoryRoleAction = createMockRoleAction(
            id = "WOLF_YOUNGER_BROTHER_EXTRA_KILL",
            name = "額外襲擊",
            optional = false,
            targets = { _, _, _ -> listOf(targetId) }
        )

        whenever(roleActionService.getAvailableActionsForPlayer(any(), eq(wbId)))
            .thenReturn(listOf(mandatoryRoleAction))

        actionUIService.cleanupExpiredPrompts(1L, session)

        // Verify that submitAction was called with the random target (targetId)
        val guildIdCaptor = argumentCaptor<Long>()
        val actionIdCaptor = argumentCaptor<String>()
        val actorIdCaptor = argumentCaptor<Int>()
        val targetsCaptor = argumentCaptor<List<Int>>()
        val sourceCaptor = argumentCaptor<String>()

        verify(roleActionService).submitAction(
            guildIdCaptor.capture(),
            actionIdCaptor.capture(),
            actorIdCaptor.capture(),
            targetsCaptor.capture(),
            sourceCaptor.capture()
        )

        assertEquals(1L, guildIdCaptor.firstValue)
        assertEquals("WOLF_YOUNGER_BROTHER_EXTRA_KILL", actionIdCaptor.firstValue)
        assertEquals(wbId, actorIdCaptor.firstValue)
        assertTrue(targetsCaptor.firstValue.contains(targetId))
        assertEquals("SYSTEM", sourceCaptor.firstValue)

        // Verify notification
        val messageCaptor = argumentCaptor<String>()
        verify(mockChannel).sendMessage(messageCaptor.capture())
        assertTrue(messageCaptor.firstValue.contains("隨機選擇"))
    }

    @Test
    @DisplayName("Mandatory action uses random target on timeout")
    fun testMandatoryActionRandomTarget() {
        val session = Session(guildId = 1L)
        val seerId = 1
        val targetId = 2

        // Create pending mandatory action (Seer Check)
        val pendingAction = RoleActionInstance(
            actor = seerId,
            actorRole = "預言家",
            actionDefinitionId = "SEER_CHECK",
            targets = emptyList(),
            submittedBy = dev.robothanzo.werewolf.game.model.ActionSubmissionSource.SYSTEM,
            status = ActionStatus.ACTING
        )
        session.stateData.submittedActions.add(pendingAction)

        // Setup players: Seer and one eligible target
        val seer = Player(id = seerId, roleId = 101L, channelId = 201L)
        seer.roles = mutableListOf("預言家")
        seer.session = session

        val villager = Player(id = targetId, roleId = 102L, channelId = 202L)
        villager.roles = mutableListOf("平民")
        villager.session = session

        // Mock JDA for Seer's channel
        val mockJda = mock<net.dv8tion.jda.api.JDA>()
        whenever(mockJda.getTextChannelById(201L)).thenReturn(mockChannel)
        WerewolfApplication.jda = mockJda

        val playerMap = mutableMapOf(
            seerId.toString() to seer,
            targetId.toString() to villager
        )

        // Reflection to set players map since it's cleaner than heavy mocking of Session
        val playersField = Session::class.java.getDeclaredField("players")
        playersField.isAccessible = true
        playersField.set(session, playerMap)

        // Mock roleActionService availability using the helper
        val mandatoryRoleAction = createMockRoleAction(
            id = "SEER_CHECK",
            name = "查驗",
            optional = false,
            targets = { _, _, _ -> listOf(targetId) }
        )

        whenever(roleActionService.getAvailableActionsForPlayer(any(), eq(seerId)))
            .thenReturn(listOf(mandatoryRoleAction))

        actionUIService.cleanupExpiredPrompts(1L, session)

        // Verify usage of roleActionService.submitAction instead of direct manipulation
        val guildIdCaptor = argumentCaptor<Long>()
        val actionIdCaptor = argumentCaptor<String>()
        val actorIdCaptor = argumentCaptor<Int>()
        val targetsCaptor = argumentCaptor<List<Int>>()
        val sourceCaptor = argumentCaptor<String>()

        verify(roleActionService).submitAction(
            guildIdCaptor.capture(),
            actionIdCaptor.capture(),
            actorIdCaptor.capture(),
            targetsCaptor.capture(),
            sourceCaptor.capture()
        )

        assertEquals(1L, guildIdCaptor.firstValue)
        assertEquals("SEER_CHECK", actionIdCaptor.firstValue)
        assertEquals(seerId, actorIdCaptor.firstValue)
        assertTrue(targetsCaptor.firstValue.contains(targetId))
        assertEquals("SYSTEM", sourceCaptor.firstValue)

        val messageCaptor = argumentCaptor<String>()
        verify(mockChannel).sendMessage(messageCaptor.capture())
        assertTrue(messageCaptor.firstValue.contains("隨機選擇"))
    }
}
