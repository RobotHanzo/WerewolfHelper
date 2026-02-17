package dev.robothanzo.werewolf.game.roles

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.*
import dev.robothanzo.werewolf.game.roles.actions.*
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

@DisplayName("Ghost Rider Role Tests")
class GhostRiderTests {

    private lateinit var session: Session
    private lateinit var executor: RoleActionExecutor
    private lateinit var roleRegistry: RoleRegistry
    private lateinit var werewolfKillAction: WerewolfKillAction
    private lateinit var witchPoisonAction: WitchPoisonAction
    private lateinit var seerCheckAction: SeerCheckAction
    private lateinit var guardProtectAction: GuardProtectAction
    private lateinit var deathResolutionAction: DeathResolutionAction

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
        session.hydratedRoles["惡靈騎士"] = wolfRole
        session.hydratedRoles["夢魘"] = wolfRole

        // Mock RoleRegistry
        roleRegistry = mock()
        whenever(roleRegistry.getRole(any())).thenAnswer { inv ->
            val name = inv.arguments[0] as String
            session.hydratedRoles[name]
        }

        // Initialize Actions
        werewolfKillAction = WerewolfKillAction()
        witchPoisonAction = WitchPoisonAction()
        seerCheckAction = SeerCheckAction(roleRegistry)
        guardProtectAction = GuardProtectAction()
        deathResolutionAction = DeathResolutionAction()

        whenever(roleRegistry.getAction(ActionDefinitionId.WEREWOLF_KILL)).thenReturn(werewolfKillAction)
        whenever(roleRegistry.getAction(ActionDefinitionId.WITCH_POISON)).thenReturn(witchPoisonAction)
        whenever(roleRegistry.getAction(ActionDefinitionId.SEER_CHECK)).thenReturn(seerCheckAction)
        whenever(roleRegistry.getAction(ActionDefinitionId.GUARD_PROTECT)).thenReturn(guardProtectAction)
        whenever(roleRegistry.getAction(ActionDefinitionId.DEATH_RESOLUTION)).thenReturn(deathResolutionAction)

        executor = RoleActionExecutor(roleRegistry)

        // Setup Players
        session.players = mutableMapOf(
            "1" to createPlayer(1, "惡靈騎士"),
            "2" to createPlayer(2, "狼人"),
            "3" to createPlayer(3, "女巫"),
            "4" to createPlayer(4, "預言家"),
            "5" to createPlayer(5, "平民"),
            "6" to createPlayer(6, "守衛"),
            "7" to createPlayer(7, "獵人"),
            "8" to createPlayer(8, "夢魘")
        )
    }

    private fun createPlayer(id: Int, roleName: String): Player {
        return Player(
            id = id,
            roleId = id.toLong(),
            channelId = id.toLong() * 100,
            userId = id.toLong()
        ).apply {
            roles = mutableListOf(roleName)
            this.session = this@GhostRiderTests.session
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
    @DisplayName("Ghost Rider should be immune to Werewolf Kill and Poison")
    fun testGhostRiderImmunity() {
        val grId = 1
        val actions = listOf(
            createAction(2, ActionDefinitionId.WEREWOLF_KILL, listOf(grId)),
            createAction(3, ActionDefinitionId.WITCH_POISON, listOf(grId))
        )

        val result = executor.executeActions(session, actions)

        assertFalse(result.deaths.values.flatten().contains(grId), "Ghost Rider should not die")
    }

    @Test
    @DisplayName("Witch Poisoning Ghost Rider should cause Witch to die (Reflection)")
    fun testWitchReflection() {
        val grId = 1
        val witchId = 3
        val actions = listOf(
            createAction(witchId, ActionDefinitionId.WITCH_POISON, listOf(grId))
        )

        val result = executor.executeActions(session, actions)

        assertTrue(result.deaths[DeathCause.REFLECT]?.contains(witchId) == true, "Witch should die from reflection")
        assertFalse(result.deaths.values.flatten().contains(grId), "Ghost Rider should not die")
        assertTrue(session.stateData.ghostRiderReflected, "Reflection flag should be set")
    }

    @Test
    @DisplayName("Seer Checking Ghost Rider should cause Seer to die (Reflection)")
    fun testSeerReflection() {
        val grId = 1
        val seerId = 4

        // Mock JDA for Seer check message
        val mockChannel = mock<TextChannel>()
        whenever(WerewolfApplication.jda.getTextChannelById(any<Long>())).thenReturn(mockChannel)
        whenever(mockChannel.sendMessage(any<String>())).thenReturn(mock())

        val actions = listOf(
            createAction(seerId, ActionDefinitionId.SEER_CHECK, listOf(grId))
        )

        val result = executor.executeActions(session, actions)

        assertTrue(result.deaths[DeathCause.REFLECT]?.contains(seerId) == true, "Seer should die from reflection")
        assertFalse(result.deaths.values.flatten().contains(grId), "Ghost Rider should not die")
        assertTrue(session.stateData.ghostRiderReflected, "Reflection flag should be set")

        // Seer action should be skipped (no info sent)
        verify(mockChannel, never()).sendMessage(any<String>())
    }

    @Test
    @DisplayName("Guard Protecting Ghost Rider should NOT trigger reflection")
    fun testGuardNoReflection() {
        val grId = 1
        val guardId = 6
        val actions = listOf(
            createAction(guardId, ActionDefinitionId.GUARD_PROTECT, listOf(grId))
        )

        val result = executor.executeActions(session, actions)

        assertFalse(result.deaths.containsKey(DeathCause.REFLECT), "Reflection should not trigger for Guard")
        assertTrue(result.protectedPlayers.contains(grId), "Ghost Rider should be protected")
        assertFalse(session.stateData.ghostRiderReflected, "Reflection flag should NOT be set")
    }

    @Test
    @DisplayName("Reflection should only trigger once per game")
    fun testReflectionOnce() {
        val grId = 1
        val witchId = 3
        val seerId = 4

        // Both Witch (210) and Seer (300) target Ghost Rider.
        // Witch should reflect because she is earlier in priority.
        val actions = listOf(
            createAction(witchId, ActionDefinitionId.WITCH_POISON, listOf(grId)),
            createAction(seerId, ActionDefinitionId.SEER_CHECK, listOf(grId))
        )

        val result = executor.executeActions(session, actions)

        assertTrue(result.deaths[DeathCause.REFLECT]?.contains(witchId) == true, "Witch should die")
        assertFalse(
            result.deaths[DeathCause.REFLECT]?.contains(seerId) == true,
            "Seer should NOT die (already reflected once)"
        )
        assertTrue(session.stateData.ghostRiderReflected)
    }

    @Test
    @DisplayName("Wolf should not be able to target Ghost Rider or Nightmare for killing")
    fun testWolfKillRestriction() {
        val grId = 1
        val nightmareId = 8
        val wolfId = 2

        val alivePlayers = listOf(1, 2, 3, 4, 5, 8)

        val eligibleTargets = werewolfKillAction.eligibleTargets(session, wolfId, alivePlayers, ActionExecutionResult())

        assertFalse(eligibleTargets.contains(grId), "Ghost Rider should not be an eligible target")
        assertFalse(eligibleTargets.contains(nightmareId), "Nightmare should not be an eligible target")
        assertTrue(eligibleTargets.contains(3), "Witch should be eligible")

        // Assert validation fails
        val grError = werewolfKillAction.validate(session, wolfId, listOf(grId))
        assertNotNull(grError)
        assertTrue(grError!!.contains("不能自刀"))

        val nightmareError = werewolfKillAction.validate(session, wolfId, listOf(nightmareId))
        assertNotNull(nightmareError)
        assertTrue(nightmareError!!.contains("不能自刀"))
    }
}
