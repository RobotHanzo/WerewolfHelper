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

@DisplayName("Magician Role Tests")
class MagicianTests {

    private lateinit var session: Session
    private lateinit var executor: RoleActionExecutor
    private lateinit var roleRegistry: RoleRegistry

    // Actions
    private lateinit var magicianSwapAction: MagicianSwapAction
    private lateinit var werewolfKillAction: WerewolfKillAction
    private lateinit var witchPoisonAction: WitchPoisonAction
    private lateinit var witchAntidoteAction: WitchAntidoteAction
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

        session.hydratedRoles["魔術師"] = godRole
        session.hydratedRoles["女巫"] = godRole
        session.hydratedRoles["預言家"] = godRole
        session.hydratedRoles["平民"] = villagerRole
        session.hydratedRoles["狼人"] = wolfRole

        // Mock RoleRegistry
        roleRegistry = mock()
        whenever(roleRegistry.getRole(any())).thenAnswer { inv ->
            val name = inv.arguments[0] as String
            session.hydratedRoles[name]
        }

        // Initialize Actions
        magicianSwapAction = MagicianSwapAction()
        werewolfKillAction = WerewolfKillAction()
        witchPoisonAction = WitchPoisonAction()
        witchAntidoteAction = WitchAntidoteAction()
        seerCheckAction = SeerCheckAction(roleRegistry)

        whenever(roleRegistry.getAction(ActionDefinitionId.MAGICIAN_SWAP)).thenReturn(magicianSwapAction)
        whenever(roleRegistry.getAction(ActionDefinitionId.WEREWOLF_KILL)).thenReturn(werewolfKillAction)
        whenever(roleRegistry.getAction(ActionDefinitionId.WITCH_POISON)).thenReturn(witchPoisonAction)
        whenever(roleRegistry.getAction(ActionDefinitionId.WITCH_ANTIDOTE)).thenReturn(witchAntidoteAction)
        whenever(roleRegistry.getAction(ActionDefinitionId.SEER_CHECK)).thenReturn(seerCheckAction)

        executor = RoleActionExecutor(roleRegistry)

        // Setup Players
        session.players = mutableMapOf(
            "1" to createPlayer(1, "魔術師"),
            "2" to createPlayer(2, "狼人"),
            "3" to createPlayer(3, "女巫"),
            "4" to createPlayer(4, "預言家"),
            "5" to createPlayer(5, "平民"),
            "6" to createPlayer(6, "平民")
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
            this.session = this@MagicianTests.session
        }
    }

    private fun createAction(
        actorId: Int,
        actionId: ActionDefinitionId,
        targets: List<Int>,
        status: ActionStatus = ActionStatus.SUBMITTED
    ): RoleActionInstance {
        val player = session.getPlayer(actorId)!!
        return RoleActionInstance(
            actor = actorId,
            actorRole = player.roles.first(),
            actionDefinitionId = actionId,
            targets = targets.toMutableList(),
            submittedBy = ActionSubmissionSource.PLAYER,
            status = status
        )
    }

    @Test
    @DisplayName("Magician Swap Validation: Cannot swap player who was already swapped")
    fun testSwapValidation() {
        val magicianId = 1

        // Simulate previous swap of player 5
        session.stateData.executedActions[session.day - 1] = mutableListOf(
            createAction(magicianId, ActionDefinitionId.MAGICIAN_SWAP, listOf(5, 6), ActionStatus.PROCESSED)
        )

        // Try to swap 5 again with someone else
        val error = magicianSwapAction.validate(session, magicianId, listOf(5, 2))

        assertNotNull(error)
        assertTrue(error!!.contains("已經被交換過"))
    }

    @Test
    @DisplayName("Wolf Kill Redirect: Swap A & B, Wolf kills A -> B dies")
    fun testWolfKillRedirect() {
        val magicianId = 1
        val targetA = 5 // Villager A
        val targetB = 6 // Villager B
        val wolfId = 2

        // 1. Submit Magician Swap (A <-> B)
        val swapAction = createAction(magicianId, ActionDefinitionId.MAGICIAN_SWAP, listOf(targetA, targetB))
        session.stateData.submittedActions.add(swapAction)

        // 2. Wolf Kills A
        val killAction = createAction(wolfId, ActionDefinitionId.WEREWOLF_KILL, listOf(targetA))
        val actions = listOf(killAction)

        val result = executor.executeActions(session, actions)

        assertFalse(result.deaths[DeathCause.WEREWOLF]?.contains(targetA) == true, "Target A should NOT die")
        assertTrue(result.deaths[DeathCause.WEREWOLF]?.contains(targetB) == true, "Target B SHOULD die (redirected)")
    }

    @Test
    @DisplayName("Seer Check Redirect: Swap A (Villager) & B (Wolf). Seer checks A -> Result: Wolf")
    fun testSeerCheckRedirect() {
        val magicianId = 1
        val targetA = 5 // Villager
        val targetB = 2 // Wolf
        val seerId = 4

        // Mock JDA for Seer check message
        val mockChannel = mock<TextChannel>()
        whenever(WerewolfApplication.jda.getTextChannelById(any<Long>())).thenReturn(mockChannel)
        whenever(mockChannel.sendMessage(any<String>())).thenReturn(mock())

        // 1. Submit Magician Swap (A <-> B)
        val swapAction = createAction(magicianId, ActionDefinitionId.MAGICIAN_SWAP, listOf(targetA, targetB))
        session.stateData.submittedActions.add(swapAction)

        // 2. Seer Checks A (Expect to see B's role = Wolf)
        val checkAction = createAction(seerId, ActionDefinitionId.SEER_CHECK, listOf(targetA))
        val actions = listOf(checkAction)

        executor.executeActions(session, actions)

        // Verify message sent to Seer contains "Wolf" or equivalent logic
        // SeerCheckAction sends message: "${target.nickname} 是 **$resultText**"
        // Target is B (Wolf), so result text should be "狼人"
        // But the NAME displayed should be A (the one they clicked), to confuse them or just reflect their action.
        verify(mockChannel).sendMessage(check<String> { msg ->
            assertTrue(msg.contains("狼人"), "Seer should see 'Wolf' result")
            assertTrue(msg.contains(session.getPlayer(targetA)!!.nickname), "Message should refer to the SELECTED target (A) name")
        })
    }

    @Test
    @DisplayName("Witch Poison Redirect: Swap A & B. Witch poisons A -> B dies")
    fun testWitchPoisonRedirect() {
        val magicianId = 1
        val targetA = 5 // Villager A
        val targetB = 6 // Villager B
        val witchId = 3

        // 1. Submit Magician Swap (A <-> B)
        val swapAction = createAction(magicianId, ActionDefinitionId.MAGICIAN_SWAP, listOf(targetA, targetB))
        session.stateData.submittedActions.add(swapAction)

        // 2. Witch Poisons A
        val poisonAction = createAction(witchId, ActionDefinitionId.WITCH_POISON, listOf(targetA))
        val actions = listOf(poisonAction)

        val result = executor.executeActions(session, actions)

        assertFalse(result.deaths[DeathCause.POISON]?.contains(targetA) == true, "Target A should NOT die")
        assertTrue(result.deaths[DeathCause.POISON]?.contains(targetB) == true, "Target B SHOULD die (redirected)")
    }

    @Test
    @DisplayName("Witch Antidote Logic: Wolf kills A (real: B). Witch saves A (real: B). B lives.")
    fun testWitchAntidoteRedirect() {
        val magicianId = 1
        val targetA = 5 // Villager A
        val targetB = 6 // Villager B
        val wolfId = 2
        val witchId = 3

        // 1. Submit Magician Swap (A <-> B)
        val swapAction = createAction(magicianId, ActionDefinitionId.MAGICIAN_SWAP, listOf(targetA, targetB))
        session.stateData.submittedActions.add(swapAction)

        // 2. Wolf Kills A (Redirects to B)
        // We simulate that this action ran and populate 'accumulatedState' or 'nightWolfKillTargetId'
        // But WitchAntidoteAction relies on `nightWolfKillTargetId` which comes from submittedActions!

        val killAction = createAction(wolfId, ActionDefinitionId.WEREWOLF_KILL, listOf(targetA))
        session.stateData.submittedActions.add(killAction)

        // Verify nightlySwap is active
        assertTrue(session.stateData.nightlySwap.containsKey(targetA))
        assertEquals(targetB, session.stateData.nightlySwap[targetA])

        // Verify real target of wolf kill
        val realWolfTarget = session.stateData.getRealTarget(session.stateData.nightWolfKillTargetId!!)
        assertEquals(targetB, realWolfTarget)

        // 3. Witch Saves A
        // Note: In client, Witch sees "A is killed" (because Wolf targeted A).
        // She selects A to save.
        // Action targets = [A]
        val antidoteAction = createAction(witchId, ActionDefinitionId.WITCH_ANTIDOTE, listOf(targetA))

        // EXECUTION
        // RoleActionExecutor executes actions.
        // Logic:
        // 1. Execute Wolf Kill (A) -> Deaths: [B] (because getRealTarget(A)=B)
        // 2. Execute Antidote (A) -> getRealTarget(A)=B.
        //    Check if B is in Deaths? Yes.
        //    Remove B from Deaths (or add to Saved)

        val actions = listOf(killAction, antidoteAction)
        val result = executor.executeActions(session, actions)

        // B should be SAVED.
        assertTrue(result.saved.contains(targetB), "Target B should be saved")
        // deaths map might still contain it if not cleaned up in executor, but usually result.saved is what matters for DeathResolution

        // Let's verify DeathResolution logic too if possible, but executeActions returns 'result'.
        // DeathResolutionAction is usually run separately or at end.
        // But simply checking 'saved' list is enough for Unit Test of AntidoteAction.

        // Also ensure A is NOT saved.
        assertFalse(result.saved.contains(targetA), "Target A should not be in saved list")
    }
}
