package dev.robothanzo.werewolf.game.roles

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.*
import dev.robothanzo.werewolf.game.roles.actions.*
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

@DisplayName("Wolf Mechanic Role Tests")
class WolfMechanicTests {

    private lateinit var session: Session
    private lateinit var executor: RoleActionExecutor
    private lateinit var roleRegistry: RoleRegistry

    // Actions needed
    private lateinit var learnAction: WolfMechanicLearnAction
    private lateinit var mechanicSeerAction: WolfMechanicSeerCheckAction
    private lateinit var mechanicPoisonAction: WolfMechanicPoisonAction
    private lateinit var mechanicGuardAction: WolfMechanicGuardProtectAction
    private lateinit var mechanicExtraKillAction: WolfMechanicExtraKillAction

    private lateinit var werewolfKillAction: WerewolfKillAction
    private lateinit var seerCheckAction: SeerCheckAction

    @BeforeEach
    fun setup() {
        val mockJda = mock<JDA>()
        WerewolfApplication.jda = mockJda

        session = Session(guildId = 123L)

        // Mock Roles
        val godRole = mock<Role>()
        whenever(godRole.camp).thenReturn(Camp.GOD)

        val wolfRole = mock<Role>()
        whenever(wolfRole.camp).thenReturn(Camp.WEREWOLF)

        val villagerRole = mock<Role>()
        whenever(villagerRole.camp).thenReturn(Camp.VILLAGER)

        session.hydratedRoles["女巫"] = godRole
        session.hydratedRoles["預言家"] = godRole
        session.hydratedRoles["獵人"] = godRole
        session.hydratedRoles["守衛"] = godRole
        session.hydratedRoles["平民"] = villagerRole
        session.hydratedRoles["狼人"] = wolfRole
        session.hydratedRoles["機械狼"] = wolfRole

        // Mock RoleRegistry
        roleRegistry = mock()
        whenever(roleRegistry.getRole(any())).thenAnswer { inv ->
            val name = inv.arguments[0] as String
            session.hydratedRoles[name]
        }

        // Initialize Actions
        learnAction = WolfMechanicLearnAction(roleRegistry)
        mechanicSeerAction = WolfMechanicSeerCheckAction(roleRegistry)
        mechanicPoisonAction = WolfMechanicPoisonAction()
        mechanicGuardAction = WolfMechanicGuardProtectAction()
        mechanicExtraKillAction = WolfMechanicExtraKillAction()

        werewolfKillAction = WerewolfKillAction()
        seerCheckAction = SeerCheckAction(roleRegistry)

        whenever(roleRegistry.getAction(ActionDefinitionId.WOLF_MECHANIC_LEARN)).thenReturn(learnAction)
        whenever(roleRegistry.getAction(ActionDefinitionId.WOLF_MECHANIC_SEER_CHECK)).thenReturn(mechanicSeerAction)
        whenever(roleRegistry.getAction(ActionDefinitionId.WOLF_MECHANIC_POISON)).thenReturn(mechanicPoisonAction)
        whenever(roleRegistry.getAction(ActionDefinitionId.WOLF_MECHANIC_GUARD_PROTECT)).thenReturn(mechanicGuardAction)
        whenever(roleRegistry.getAction(ActionDefinitionId.WOLF_MECHANIC_EXTRA_KILL)).thenReturn(mechanicExtraKillAction)

        whenever(roleRegistry.getAction(ActionDefinitionId.WEREWOLF_KILL)).thenReturn(werewolfKillAction)
        whenever(roleRegistry.getAction(ActionDefinitionId.SEER_CHECK)).thenReturn(seerCheckAction)

        executor = RoleActionExecutor(roleRegistry)

        // Setup Players
        session.players = mutableMapOf(
            "1" to createPlayer(1, "機械狼"),
            "2" to createPlayer(2, "狼人"),
            "3" to createPlayer(3, "女巫"),
            "4" to createPlayer(4, "預言家"),
            "5" to createPlayer(5, "平民"),
            "6" to createPlayer(6, "守衛"),
            "7" to createPlayer(7, "獵人")
        )

        session.day = 1
    }

    private fun createPlayer(id: Int, roleName: String): Player {
        val channelId = id.toLong() * 100
        val mockChannel = mock<TextChannel>()
        val mockMessageCreateAction = mock<MessageCreateAction>()
        whenever(mockMessageCreateAction.queue()).then {}
        whenever(mockChannel.sendMessage(any<CharSequence>())).thenReturn(mockMessageCreateAction)

        whenever(WerewolfApplication.jda.getTextChannelById(channelId)).thenReturn(mockChannel)

        return Player(
            id = id,
            roleId = id.toLong(),
            channelId = channelId,
            userId = id.toLong()
        ).apply {
            roles = mutableListOf(roleName)
            this.session = this@WolfMechanicTests.session
        }
    }

    private fun createAction(actorId: Int, actionId: ActionDefinitionId, targets: List<Int>): RoleActionInstance {
        val player = session.getPlayer(actorId)!!
        return RoleActionInstance(
            actor = actorId,
            actorRole = player.roles.first(),
            actionDefinitionId = actionId,
            targets = targets.toMutableList(),
            submittedBy = ActionSubmissionSource.PLAYER,
            status = ActionStatus.SUBMITTED
        )
    }

    @Test
    @DisplayName("Mechanic learns a role and gains skill correctly")
    fun testLearnWitch() {
        val mechanicId = 1
        val targetId = 3 // Witch

        val actions1 = listOf(createAction(mechanicId, ActionDefinitionId.WOLF_MECHANIC_LEARN, listOf(targetId)))
        session.stateData.submittedActions.addAll(actions1)
        executor.executeActions(session, actions1)

        assertEquals("女巫", session.wolfMechanicLearnedRole[mechanicId])
        assertEquals(
            1,
            session.stateData.playerOwnedActions[mechanicId]?.get(ActionDefinitionId.WOLF_MECHANIC_POISON.toString())
        )

        // Assert action available from next night
        assertFalse(mechanicPoisonAction.isAvailable(session, mechanicId), "Should not be available same night")

        session.day = 2
        assertTrue(mechanicPoisonAction.isAvailable(session, mechanicId), "Should be available next night")
    }

    @Test
    @DisplayName("Mechanic poisoning someone")
    fun testPoisonExecution() {
        val mechanicId = 1

        // Simulate learning on Day 1
        val learnAction = createAction(mechanicId, ActionDefinitionId.WOLF_MECHANIC_LEARN, listOf(3)) // target Witch
        learnAction.status = ActionStatus.PROCESSED
        session.stateData.executedActions[1] = mutableListOf(learnAction)

        session.stateData.playerOwnedActions[mechanicId] =
            mutableMapOf(ActionDefinitionId.WOLF_MECHANIC_POISON.toString() to 1)
        session.day = 2

        val targetId = 5
        val actions = listOf(createAction(mechanicId, ActionDefinitionId.WOLF_MECHANIC_POISON, listOf(targetId)))
        val result = executor.executeActions(session, actions)

        assertTrue(result.deaths[DeathCause.POISON]?.contains(targetId) == true)
        assertNull(session.stateData.playerOwnedActions[mechanicId]?.get(ActionDefinitionId.WOLF_MECHANIC_POISON.toString()))
    }

    @Test
    @DisplayName("Seer checks Mechanic, sees learned role")
    fun testSeerCheckMechanic() {
        val seerId = 4
        val mechanicId = 1

        val seerPlayer = session.getPlayer(seerId)!!
        val channelId = seerPlayer.channelId ?: 0L
        val mockChannel = mock<TextChannel>()
        val mockAction = mock<MessageCreateAction>()
        whenever(mockAction.queue()).then {}
        whenever(mockChannel.sendMessage(any<CharSequence>())).thenReturn(mockAction)
        whenever(WerewolfApplication.jda.getTextChannelById(channelId)).thenReturn(mockChannel)

        // Case 1: unlearned -> Wolf
        val actions1 = listOf(createAction(seerId, ActionDefinitionId.SEER_CHECK, listOf(mechanicId)))
        executor.executeActions(session, actions1)
        verify(mockChannel).sendMessage(argThat<CharSequence> { contains("查驗結果") && contains("狼人") })

        reset(mockChannel)
        whenever(mockChannel.sendMessage(any<CharSequence>())).thenReturn(mockAction)

        // Case 2: Learned Witch (Good)
        val learnActionWitch =
            createAction(mechanicId, ActionDefinitionId.WOLF_MECHANIC_LEARN, listOf(3)) // Which is Witch
        learnActionWitch.status = ActionStatus.PROCESSED
        session.stateData.executedActions[1] = mutableListOf(learnActionWitch)

        val actions2 = listOf(createAction(seerId, ActionDefinitionId.SEER_CHECK, listOf(mechanicId)))
        executor.executeActions(session, actions2)
        verify(mockChannel).sendMessage(argThat<CharSequence> { contains("查驗結果") && contains("好人") })

        // Case 3: Inherited -> Wolf again
        reset(mockChannel)
        whenever(mockChannel.sendMessage(any<CharSequence>())).thenReturn(mockAction)
        session.getPlayer(2)!!.deadRoles.add("狼人") // Normal wolf dies

        val actions3 = listOf(createAction(seerId, ActionDefinitionId.SEER_CHECK, listOf(mechanicId)))
        executor.executeActions(session, actions3)
        verify(mockChannel).sendMessage(argThat<CharSequence> { contains("查驗結果") && contains("狼人") })
    }

    @Test
    @DisplayName("Mechanic performs Seer check when learning Seer")
    fun testMechanicPerformsSeerCheck() {
        val mechanicId = 1
        val seerId = 4
        val targetId = 3 // Witch (Good)

        // Mechanic learned Seer
        val learnAction = createAction(mechanicId, ActionDefinitionId.WOLF_MECHANIC_LEARN, listOf(seerId))
        learnAction.status = ActionStatus.PROCESSED
        session.stateData.executedActions[1] = mutableListOf(learnAction)

        val mechanicPlayer = session.getPlayer(mechanicId)!!
        val channelId = mechanicPlayer.channelId ?: 0L
        val mockChannel = mock<TextChannel>()
        val mockAction = mock<MessageCreateAction>()
        whenever(mockAction.queue()).then {}
        whenever(mockChannel.sendMessage(any<CharSequence>())).thenReturn(mockAction)
        whenever(WerewolfApplication.jda.getTextChannelById(channelId)).thenReturn(mockChannel)

        val checkAction = createAction(mechanicId, ActionDefinitionId.WOLF_MECHANIC_SEER_CHECK, listOf(targetId))
        executor.executeActions(session, listOf(checkAction))
        verify(mockChannel).sendMessage(argThat<CharSequence> { contains("好人") })
    }

    @Test
    @DisplayName("Mechanic performs Psychic check when learning Psychic")
    fun testMechanicPerformsPsychicCheck() {
        val mechanicId = 1
        val psychicId = 8 // We need to add Psychic

        val godRole = mock<Role>()
        whenever(godRole.camp).thenReturn(Camp.GOD)
        session.hydratedRoles["通靈師"] = godRole
        session.players["8"] = createPlayer(8, "通靈師")

        val targetId = 3 // Witch

        // Mechanic learned Psychic
        val learnAction = createAction(mechanicId, ActionDefinitionId.WOLF_MECHANIC_LEARN, listOf(psychicId))
        learnAction.status = ActionStatus.PROCESSED
        session.stateData.executedActions[1] = mutableListOf(learnAction)

        val mechanicPlayer = session.getPlayer(mechanicId)!!
        val channelId = mechanicPlayer.channelId ?: 0L
        val mockChannel = mock<TextChannel>()
        val mockAction = mock<MessageCreateAction>()
        whenever(mockAction.queue()).then {}
        whenever(mockChannel.sendMessage(any<CharSequence>())).thenReturn(mockAction)
        whenever(WerewolfApplication.jda.getTextChannelById(channelId)).thenReturn(mockChannel)

        val checkAction = createAction(mechanicId, ActionDefinitionId.WOLF_MECHANIC_SEER_CHECK, listOf(targetId))
        executor.executeActions(session, listOf(checkAction))
        verify(mockChannel).sendMessage(argThat<CharSequence> { contains("女巫") })
    }

    @Test
    @DisplayName("Mechanic inheritance and extra kill")
    fun testMechanicInheritance() {
        val mechanicId = 1
        val normalWolfId = 2

        // Before inheriting
        val learnActionWolf =
            createAction(mechanicId, ActionDefinitionId.WOLF_MECHANIC_LEARN, listOf(2)) // Target Normal Wolf
        learnActionWolf.status = ActionStatus.PROCESSED
        session.stateData.executedActions[1] = mutableListOf(learnActionWolf)

        session.day = 2

        assertFalse(session.isWolfMechanicInherited(), "Mechanic is not inherited yet")
        assertFalse(mechanicExtraKillAction.isAvailable(session, mechanicId), "Extra kill not available yet")

        // Normal wolf dies
        session.getPlayer(normalWolfId)!!.deadRoles.add("狼人")
        assertTrue(session.isWolfMechanicInherited(), "Mechanic is inherited now")
        assertTrue(mechanicExtraKillAction.isAvailable(session, mechanicId), "Extra kill available")

        // Execute extra kill
        val victimId = 4
        val actions = listOf(createAction(mechanicId, ActionDefinitionId.WOLF_MECHANIC_EXTRA_KILL, listOf(victimId)))
        val result = executor.executeActions(session, actions)

        assertTrue(result.deaths[DeathCause.WEREWOLF]?.contains(victimId) == true)
    }

    @Test
    @DisplayName("Mechanic only gets available actions based on learned role")
    fun testMechanicOnlyGetsAvailableActions() {
        val mechanicId = 1
        val witchId = 3

        session.currentState = "NIGHT_STEP"
        session.day = 2 // Next night

        // Set up the state as if the mechanic learned the Witch on day 1
        val submittedLearnAction = createAction(mechanicId, ActionDefinitionId.WOLF_MECHANIC_LEARN, listOf(witchId))
        submittedLearnAction.status = ActionStatus.PROCESSED
        session.stateData.executedActions[1] = mutableListOf(submittedLearnAction)

        session.stateData.playerOwnedActions[mechanicId] = mutableMapOf(
            ActionDefinitionId.WOLF_MECHANIC_POISON.toString() to 1
        )

        // Ensure isAvailable logic properly acts
        whenever(roleRegistry.getAction(any())).thenAnswer { inv ->
            val actionId = inv.arguments[0] as ActionDefinitionId
            when (actionId) {
                ActionDefinitionId.WOLF_MECHANIC_LEARN -> learnAction
                ActionDefinitionId.WOLF_MECHANIC_SEER_CHECK -> mechanicSeerAction
                ActionDefinitionId.WOLF_MECHANIC_POISON -> mechanicPoisonAction
                ActionDefinitionId.WOLF_MECHANIC_GUARD_PROTECT -> mechanicGuardAction
                ActionDefinitionId.WOLF_MECHANIC_EXTRA_KILL -> mechanicExtraKillAction
                else -> null
            }
        }

        // Let's create a mocked role that returns all actions like the real one does
        val mechanicRole = mock<Role>()
        whenever(mechanicRole.getActions()).thenReturn(
            listOf(
                learnAction,
                mechanicSeerAction,
                mechanicPoisonAction,
                mechanicGuardAction,
                mechanicExtraKillAction
            )
        )
        session.hydratedRoles["機械狼"] = mechanicRole
        whenever(roleRegistry.getRole("機械狼")).thenReturn(mechanicRole)

        val availableActions = session.getAvailableActionsForPlayer(mechanicId, roleRegistry)

        // It should ONLY contain the POISON action
        assertTrue(
            availableActions.any { it.actionId == ActionDefinitionId.WOLF_MECHANIC_POISON },
            "Should have poison action"
        )
        assertFalse(
            availableActions.any { it.actionId == ActionDefinitionId.WOLF_MECHANIC_SEER_CHECK },
            "Should NOT have seer action"
        )
        assertFalse(
            availableActions.any { it.actionId == ActionDefinitionId.WOLF_MECHANIC_GUARD_PROTECT },
            "Should NOT have guard action"
        )

        // Learn action usage limit is 1, so it shouldn't be available anymore
        assertFalse(
            availableActions.any { it.actionId == ActionDefinitionId.WOLF_MECHANIC_LEARN },
            "Should NOT have learn action"
        )
    }
}
