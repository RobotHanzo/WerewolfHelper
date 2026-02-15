package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.*
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import dev.robothanzo.werewolf.game.roles.actions.ActionExecutionResult
import dev.robothanzo.werewolf.game.roles.actions.RoleAction
import dev.robothanzo.werewolf.game.roles.actions.RoleActionExecutor
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.NightManager
import dev.robothanzo.werewolf.service.RoleEventService
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import org.junit.jupiter.api.Assertions.*
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
    private lateinit var roleRegistry: RoleRegistry

    @Mock
    private lateinit var roleActionExecutor: RoleActionExecutor

    @Mock
    private lateinit var roleEventService: RoleEventService

    @Mock
    private lateinit var mockChannel: TextChannel

    @Mock
    private lateinit var mockMessageAction: MessageCreateAction

    private lateinit var actionUIService: ActionUIServiceImpl

    // Helper to create a mock RoleAction
    private fun createMockRoleAction(
        id: String,
        optional: Boolean,
        targets: (Session, Int, List<Int>) -> List<Int>
    ): RoleAction {
        return object : RoleAction {
            override val actionId: ActionDefinitionId =
                ActionDefinitionId.fromString(id) ?: ActionDefinitionId.WEREWOLF_KILL
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

            override fun validate(
                session: Session,
                actor: Int,
                targets: List<Int>
            ): String? {
                return null
            }

            override fun onSubmitted(
                session: Session,
                actor: Int,
                targets: List<Int>
            ) {
                // No-op
            }
        }
    }

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)

        // Setup JDA mocks for message sending
        val mockJda = mock<JDA>()
        whenever(mockJda.getTextChannelById(any<Long>())).thenReturn(mockChannel)
        whenever(mockChannel.sendMessage(any<String>())).thenReturn(mockMessageAction)
        WerewolfApplication.jda = mockJda

        // Setup static service access
        WerewolfApplication.gameSessionService = gameSessionService
        WerewolfApplication.nightManager = nightManager
        WerewolfApplication.roleEventService = roleEventService

        actionUIService = ActionUIServiceImpl(nightManager, roleRegistry, roleActionExecutor, roleEventService)
    }

    @Test
    @DisplayName("Wolf Younger Brother picks random target on timeout")
    fun testWolfBrotherRandomTarget() {
        val session = Session(guildId = 1L)
        session.currentState = "NIGHT"
        session.stateData.phaseEndTime = System.currentTimeMillis() - 1000 // Ensure phase expired

        val wbId = 1
        val targetId = 2

        // Create pending action for Wolf Younger Brother extra kill
        val pendingAction = RoleActionInstance(
            actor = wbId,
            actorRole = "狼弟",
            actionDefinitionId = ActionDefinitionId.WOLF_YOUNGER_BROTHER_EXTRA_KILL,
            targets = mutableListOf(), // Empty initially
            submittedBy = ActionSubmissionSource.SYSTEM,
            status = ActionStatus.ACTING
        )
        session.stateData.submittedActions.add(pendingAction)

        // Mock player setup
        val player = Player(id = wbId, roleId = 100L, channelId = 200L)
        player.roles = mutableListOf("狼弟")
        player.session = session

        val villager = Player(id = targetId, roleId = 101L, channelId = 201L)
        villager.roles = mutableListOf("平民")
        villager.session = session

        session.players = mutableMapOf(
            wbId.toString() to player,
            targetId.toString() to villager
        )

        // Mock role registry to return our action
        val mandatoryRoleAction = createMockRoleAction(
            id = "WOLF_YOUNGER_BROTHER_EXTRA_KILL",
            optional = false,
            targets = { _, _, _ -> listOf(targetId) }
        )

        // Mock role to return actions
        val mockRole = mock<Role>()
        whenever(mockRole.getActions()).thenReturn(listOf(mandatoryRoleAction))
        whenever(roleRegistry.getRole("狼弟")).thenReturn(mockRole)
        whenever(roleRegistry.getAction(ActionDefinitionId.WOLF_YOUNGER_BROTHER_EXTRA_KILL)).thenReturn(
            mandatoryRoleAction
        )

        // Execute cleanup
        actionUIService.cleanupExpiredPrompts(session)

        // Verify that the action in session state was updated to SUBMITTED with random target
        // BUT wait, session.validateAndSubmitAction creates a NEW action instance or updates existing?
        // It updates `session.stateData.submittedActions` by removing old and adding new.

        val submittedAction = session.stateData.submittedActions.find {
            it.actor == wbId && it.actionDefinitionId == ActionDefinitionId.WOLF_YOUNGER_BROTHER_EXTRA_KILL
        }

        assertNotNull(submittedAction)
        assertEquals(ActionStatus.SUBMITTED, submittedAction?.status)
        assertEquals(ActionSubmissionSource.SYSTEM, submittedAction?.submittedBy)
        assertTrue(submittedAction?.targets?.contains(targetId) == true)

        // Verify notification
        val messageCaptor = argumentCaptor<String>()
        verify(mockChannel).sendMessage(messageCaptor.capture())
        assertTrue(messageCaptor.firstValue.contains("隨機選擇"))
    }

    @Test
    @DisplayName("Mandatory action uses random target on timeout")
    fun testMandatoryActionRandomTarget() {
        val session = Session(guildId = 1L)
        session.currentState = "NIGHT"
        session.stateData.phaseEndTime = System.currentTimeMillis() - 1000

        val seerId = 1
        val targetId = 2

        // Create pending mandatory action (Seer Check)
        val pendingAction = RoleActionInstance(
            actor = seerId,
            actorRole = "預言家",
            actionDefinitionId = ActionDefinitionId.SEER_CHECK,
            targets = mutableListOf(),
            submittedBy = ActionSubmissionSource.SYSTEM,
            status = ActionStatus.ACTING
        )
        session.stateData.submittedActions.add(pendingAction)

        // Setup players
        val seer = Player(id = seerId, roleId = 101L, channelId = 201L)
        seer.roles = mutableListOf("預言家")
        seer.session = session

        val villager = Player(id = targetId, roleId = 102L, channelId = 202L)
        villager.roles = mutableListOf("平民")
        villager.session = session

        session.players = mutableMapOf(
            seerId.toString() to seer,
            targetId.toString() to villager
        )

        // Mock role registry
        val mandatoryRoleAction = createMockRoleAction(
            id = "SEER_CHECK",
            optional = false,
            targets = { _, _, _ -> listOf(targetId) }
        )

        val mockRole = mock<Role>()
        whenever(mockRole.getActions()).thenReturn(listOf(mandatoryRoleAction))
        whenever(roleRegistry.getRole("預言家")).thenReturn(mockRole)
        whenever(roleRegistry.getAction(ActionDefinitionId.SEER_CHECK)).thenReturn(mandatoryRoleAction)

        // Execute cleanup
        actionUIService.cleanupExpiredPrompts(session)

        // Verify action submitted
        val submittedAction = session.stateData.submittedActions.find {
            it.actor == seerId && it.actionDefinitionId == ActionDefinitionId.SEER_CHECK
        }

        assertNotNull(submittedAction)
        assertEquals(ActionStatus.SUBMITTED, submittedAction?.status)
        assertEquals(ActionSubmissionSource.SYSTEM, submittedAction?.submittedBy)
        assertTrue(submittedAction?.targets?.contains(targetId) == true)

        val messageCaptor = argumentCaptor<String>()
        verify(mockChannel).sendMessage(messageCaptor.capture())
        assertTrue(messageCaptor.firstValue.contains("隨機選擇"))
    }
}
