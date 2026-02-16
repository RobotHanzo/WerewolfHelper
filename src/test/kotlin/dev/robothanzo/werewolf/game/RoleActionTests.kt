package dev.robothanzo.werewolf.game.roles.actions

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.*
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Comprehensive tests for role actions
 */
@DisplayName("Role Actions Test Suite")
class RoleActionTests {

    private lateinit var session: Session
    private fun createPlayer(id: Int, userId: Long, roles: List<String> = listOf()): Player {
        return Player(
            id = id,
            roleId = userId,
            channelId = userId * 100,
            userId = userId
        ).apply {
            this.roles = roles.toMutableList()
        }
    }

    @BeforeEach
    fun setup() {
        val mockJda = mock<JDA>()
        whenever(mockJda.getUserById(any<Long>())).thenAnswer { invocation ->
            val userId = invocation.arguments[0] as Long
            val user = mock<User>()
            whenever(user.idLong).thenReturn(userId)
            user
        }
        WerewolfApplication.jda = mockJda

        session = Session(guildId = 123L)
        session.players = mutableMapOf(
            "1" to createPlayer(1, 1L),
            "2" to createPlayer(2, 2L),
            "3" to createPlayer(3, 3L),
            "4" to createPlayer(4, 4L),
            "5" to createPlayer(5, 5L)
        )
    }

    @Nested
    @DisplayName("Werewolf Kill Action Tests")
    inner class WerewolfKillActionTests {
        private lateinit var action: WerewolfKillAction

        @BeforeEach
        fun setup() {
            action = WerewolfKillAction()
        }

        @Test
        @DisplayName("Werewolf kill with single target")
        fun testWerewolfKillSingleTarget() {
            val targetId = 2
            val action = RoleActionInstance(
                actor = 1,
                actorRole = "狼人",
                actionDefinitionId = ActionDefinitionId.WEREWOLF_KILL,
                targets = mutableListOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )

            val result = this.action.execute(session, action, ActionExecutionResult())

            assertTrue(result.deaths.containsKey(DeathCause.WEREWOLF))
            assertTrue(result.deaths[DeathCause.WEREWOLF]?.contains(targetId) == true)
        }

        @Test
        @DisplayName("Werewolf kill with no targets")
        fun testWerewolfKillNoTarget() {
            val action = RoleActionInstance(
                actor = 1,
                actorRole = "狼人",
                actionDefinitionId = ActionDefinitionId.WEREWOLF_KILL,
                targets = mutableListOf(),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )

            val result = this.action.execute(session, action, ActionExecutionResult())

            assertTrue(result.deaths.isEmpty())
        }

        @Test
        @DisplayName("Multiple werewolves killing same target")
        fun testMultipleWerewolvesKillingSameTarget() {
            val targetId = 2
            val action1 = RoleActionInstance(
                actor = 1,
                actorRole = "狼人",
                actionDefinitionId = ActionDefinitionId.WEREWOLF_KILL,
                targets = mutableListOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )
            val action2 = RoleActionInstance(
                actor = 3,
                actorRole = "狼人",
                actionDefinitionId = ActionDefinitionId.WEREWOLF_KILL,
                targets = mutableListOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )

            var result = this.action.execute(session, action1, ActionExecutionResult())
            result = this.action.execute(session, action2, result)

            // Target should be in death list (may appear multiple times)
            val deaths = result.deaths[DeathCause.WEREWOLF] ?: emptyList()
            assertTrue(deaths.contains(targetId))
            assertTrue(deaths.isNotEmpty())
        }
    }

    @Nested
    @DisplayName("Witch Antidote Action Tests")
    inner class WitchAntidoteActionTests {
        private lateinit var action: WitchAntidoteAction

        @BeforeEach
        fun setup() {
            action = WitchAntidoteAction()
        }

        @Test
        @DisplayName("Witch saves player from werewolf kill")
        fun testWitchSavesFromKill() {
            val targetId = 2
            val wolfKillAction = RoleActionInstance(
                actor = 1,
                actorRole = "狼人",
                actionDefinitionId = ActionDefinitionId.WEREWOLF_KILL,
                targets = mutableListOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )
            val witchAntidoteAction = RoleActionInstance(
                actor = 5,
                actorRole = "女巫",
                actionDefinitionId = ActionDefinitionId.WITCH_ANTIDOTE,
                targets = mutableListOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )

            val wolfKillExecutor = WerewolfKillAction()
            var result = wolfKillExecutor.execute(session, wolfKillAction, ActionExecutionResult())
            result = action.execute(session, witchAntidoteAction, result)

            assertTrue(result.saved.contains(targetId))
        }

        @Test
        @DisplayName("Witch cannot save player not being killed")
        fun testWitchCannotSaveNonTarget() {
            val targetId = 2
            val saveTargetId = 3
            val wolfKillAction = RoleActionInstance(
                actor = 1,
                actorRole = "狼人",
                actionDefinitionId = ActionDefinitionId.WEREWOLF_KILL,
                targets = mutableListOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )
            val witchAntidoteAction = RoleActionInstance(
                actor = 5,
                actorRole = "女巫",
                actionDefinitionId = ActionDefinitionId.WITCH_ANTIDOTE,
                targets = mutableListOf(saveTargetId),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )

            val wolfKillExecutor = WerewolfKillAction()
            var result = wolfKillExecutor.execute(session, wolfKillAction, ActionExecutionResult())
            val initialSavedCount = result.saved.size
            result = action.execute(session, witchAntidoteAction, result)

            // No additional saves
            assertEquals(initialSavedCount, result.saved.size)
            assertFalse(result.saved.contains(saveTargetId))
        }

        @Test
        @DisplayName("Witch can save self when setting allows")
        fun testWitchCanSaveSelfWhenAllowed() {
            session.settings.witchCanSaveSelf = true
            val witchId = 5
            val wolfKillAction = RoleActionInstance(
                actor = 1,
                actorRole = "狼人",
                actionDefinitionId = ActionDefinitionId.WEREWOLF_KILL,
                targets = mutableListOf(witchId),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )
            val witchAntidoteAction = RoleActionInstance(
                actor = witchId,
                actorRole = "女巫",
                actionDefinitionId = ActionDefinitionId.WITCH_ANTIDOTE,
                targets = mutableListOf(witchId),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )

            val wolfKillExecutor = WerewolfKillAction()
            var result = wolfKillExecutor.execute(session, wolfKillAction, ActionExecutionResult())
            result = action.execute(session, witchAntidoteAction, result)

            assertTrue(result.saved.contains(witchId))
        }

        @Test
        @DisplayName("Witch cannot save self when setting disallows")
        fun testWitchCannotSaveSelfWhenDisallowed() {
            session.settings.witchCanSaveSelf = false
            val witchId = 5
            val wolfKillAction = RoleActionInstance(
                actor = 1,
                actorRole = "狼人",
                actionDefinitionId = ActionDefinitionId.WEREWOLF_KILL,
                targets = mutableListOf(witchId),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )
            val witchAntidoteAction = RoleActionInstance(
                actor = witchId,
                actorRole = "女巫",
                actionDefinitionId = ActionDefinitionId.WITCH_ANTIDOTE,
                targets = mutableListOf(witchId),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )

            val wolfKillExecutor = WerewolfKillAction()
            var result = wolfKillExecutor.execute(session, wolfKillAction, ActionExecutionResult())
            result = action.execute(session, witchAntidoteAction, result)

            assertFalse(result.saved.contains(witchId))
        }
    }

    @Nested
    @DisplayName("Guard Protect Action Tests")
    inner class GuardProtectActionTests {
        private lateinit var action: GuardProtectAction

        @BeforeEach
        fun setup() {
            action = GuardProtectAction()
            session.day = 1
        }

        private fun setLastGuardProtectedId(targetId: Int, day: Int) {
            val guardAction = RoleActionInstance(
                actor = 4,
                actorRole = "守衛",
                actionDefinitionId = ActionDefinitionId.GUARD_PROTECT,
                targets = mutableListOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )
            session.stateData.executedActions.getOrPut(day) { mutableListOf() }.add(guardAction)
        }

        @Test
        @DisplayName("Guard protects player from werewolf kill")
        fun testGuardProtectsFromKill() {
            val targetId = 2
            val guardAction = RoleActionInstance(
                actor = 4,
                actorRole = "守衛",
                actionDefinitionId = ActionDefinitionId.GUARD_PROTECT,
                targets = mutableListOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )

            val result = action.execute(session, guardAction, ActionExecutionResult())

            assertTrue(result.protectedPlayers.contains(targetId))
        }

        @Test
        @DisplayName("Guard cannot protect same player on consecutive nights")
        fun testGuardCannotProtectSamePlayerConsecutively() {
            session.day = 2
            val targetId = 2
            // Mock protection on day 1
            setLastGuardProtectedId(targetId, 1)

            val guardAction = RoleActionInstance(
                actor = 4,
                actorRole = "守衛",
                actionDefinitionId = ActionDefinitionId.GUARD_PROTECT,
                targets = mutableListOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )

            var result = ActionExecutionResult()
            result = action.execute(session, guardAction, result)

            // Should not protect the same player
            assertFalse(result.protectedPlayers.contains(targetId))
        }

        @Test
        @DisplayName("Guard can protect same player on day 1")
        fun testGuardCanProtectSamePlayerOnDay1() {
            session.day = 1
            val targetId = 2
            // Mock protection on day 0 (hypothetical)
            setLastGuardProtectedId(targetId, 0)

            val guardAction = RoleActionInstance(
                actor = 4,
                actorRole = "守衛",
                actionDefinitionId = ActionDefinitionId.GUARD_PROTECT,
                targets = mutableListOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )

            val result = action.execute(session, guardAction, ActionExecutionResult())

            // Should protect on day 1 even if same as last night
            // Note: If Action logic 'day > 0' blocks it, this assertion fails.
            // We assume Day 1 logic allows it or day check handles day=1.
            assertTrue(result.protectedPlayers.contains(targetId))
        }

        @Test
        @DisplayName("Guard can protect different player after protecting one previously")
        fun testGuardCanProtectDifferentPlayer() {
            session.day = 2
            val lastTargetId = 2
            val newTargetId = 3
            // Mock protection on day 1
            setLastGuardProtectedId(lastTargetId, 1)

            val guardAction = RoleActionInstance(
                actor = 4,
                actorRole = "守衛",
                actionDefinitionId = ActionDefinitionId.GUARD_PROTECT,
                targets = mutableListOf(newTargetId),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )

            val result = action.execute(session, guardAction, ActionExecutionResult())

            // Should protect the new player
            assertTrue(result.protectedPlayers.contains(newTargetId))
            assertFalse(result.protectedPlayers.contains(lastTargetId))
        }
    }

    @Nested
    @DisplayName("Witch Poison Action Tests")
    inner class WitchPoisonActionTests {
        private lateinit var action: WitchPoisonAction

        @BeforeEach
        fun setup() {
            action = WitchPoisonAction()
        }

        @Test
        @DisplayName("Witch poisons player")
        fun testWitchPoisons() {
            val targetId = 2
            val poisonAction = RoleActionInstance(
                actor = 5,
                actorRole = "女巫",
                actionDefinitionId = ActionDefinitionId.WITCH_POISON,
                targets = mutableListOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )

            val result = action.execute(session, poisonAction, ActionExecutionResult())

            assertTrue(result.deaths.containsKey(DeathCause.POISON))
            assertTrue(result.deaths[DeathCause.POISON]?.contains(targetId) == true)
        }

        @Test
        @DisplayName("Witch poison is ignored by protection")
        fun testWitchPoisonIgnoresProtection() {
            val targetId = 2

            // Guard protects first
            val guardAction = RoleActionInstance(
                actor = 4,
                actorRole = "守衛",
                actionDefinitionId = ActionDefinitionId.GUARD_PROTECT,
                targets = mutableListOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )
            val guardExecutor = GuardProtectAction()
            var result = guardExecutor.execute(session, guardAction, ActionExecutionResult())

            // Witch poisons
            val poisonAction = RoleActionInstance(
                actor = 5,
                actorRole = "女巫",
                actionDefinitionId = ActionDefinitionId.WITCH_POISON,
                targets = mutableListOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )
            result = action.execute(session, poisonAction, result)

            // Poison should still kill despite protection
            assertTrue(result.deaths.containsKey(DeathCause.POISON))
            assertTrue(result.deaths[DeathCause.POISON]?.contains(targetId) == true)
        }
    }

    @Nested
    @DisplayName("Seer Check Action Tests")
    inner class SeerCheckActionTests {
        private lateinit var action: SeerCheckAction
        private lateinit var testSession: Session
        private lateinit var mockChannel: TextChannel

        @Suppress("DEPRECATION")
        private lateinit var mockMessageAction: MessageCreateAction

        @BeforeEach
        fun setup() {
            val wolfRole = mock<Role>()
            whenever(wolfRole.camp).thenReturn(Camp.WEREWOLF)

            val villagerRole = mock<Role>()
            whenever(villagerRole.camp).thenReturn(Camp.VILLAGER)

            val seerRole = mock<Role>()
            whenever(seerRole.camp).thenReturn(Camp.VILLAGER)

            val mockRoleRegistry = mock<dev.robothanzo.werewolf.game.roles.RoleRegistry>()
            action = SeerCheckAction(mockRoleRegistry)
            testSession = Session(guildId = 123L)
            testSession.hydratedRoles["狼人"] = wolfRole
            testSession.hydratedRoles["平民"] = villagerRole
            testSession.hydratedRoles["預言家"] = seerRole

            testSession.players = mutableMapOf(
                "1" to createPlayer(1, 1L, listOf("狼人")),
                "2" to createPlayer(2, 2L, listOf("平民")),
                "3" to createPlayer(3, 3L, listOf("預言家"))
            )

            mockChannel = mock<TextChannel>()
            @Suppress("DEPRECATION")
            mockMessageAction = mock<MessageCreateAction>()
            whenever(mockChannel.sendMessage(any<String>())).thenReturn(mockMessageAction)
            whenever(WerewolfApplication.jda.getTextChannelById(300L)).thenReturn(mockChannel)
        }

        @Test
        @DisplayName("Seer checks werewolf")
        fun testSeerChecksWerewolf() {
            val targetId = 1
            val checkAction = RoleActionInstance(
                actor = 3,
                actorRole = "預言家",
                actionDefinitionId = ActionDefinitionId.SEER_CHECK,
                targets = mutableListOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )

            action.execute(testSession, checkAction, ActionExecutionResult())

            assertEquals(ActionStatus.PROCESSED, checkAction.status)
            verify(mockChannel).sendMessage(org.mockito.ArgumentMatchers.contains("狼人"))
        }

        @Test
        @DisplayName("Seer checks villager")
        fun testSeerChecksVillager() {
            val targetId = 2
            val checkAction = RoleActionInstance(
                actor = 3,
                actorRole = "預言家",
                actionDefinitionId = ActionDefinitionId.SEER_CHECK,
                targets = mutableListOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )

            action.execute(testSession, checkAction, ActionExecutionResult())

            assertEquals(ActionStatus.PROCESSED, checkAction.status)
            verify(mockChannel).sendMessage(org.mockito.ArgumentMatchers.contains("好人"))
        }
    }

    @Nested
    @DisplayName("Death Resolution Action Tests")
    inner class DeathResolutionActionTests {
        private lateinit var action: DeathResolutionAction

        @BeforeEach
        fun setup() {
            action = DeathResolutionAction()
        }

        @Test
        @DisplayName("Saved player is removed from deaths")
        fun testSavedPlayerRemovedFromDeaths() {
            val targetId = 2
            val accumulatedState = ActionExecutionResult(
                deaths = mutableMapOf(DeathCause.WEREWOLF to mutableListOf(targetId)),
                saved = mutableListOf(targetId)
            )

            val dummyAction = RoleActionInstance(
                actor = 0,
                actorRole = "SYSTEM",
                actionDefinitionId = ActionDefinitionId.DEATH_RESOLUTION,
                targets = mutableListOf(),
                submittedBy = ActionSubmissionSource.JUDGE,
                status = ActionStatus.SUBMITTED
            )

            val result = action.execute(session, dummyAction, accumulatedState)

            // Player should not be in any death list
            result.deaths.forEach { (_, deaths) ->
                assertFalse(deaths.contains(targetId))
            }
        }

        @Test
        @DisplayName("Protected player is removed from werewolf kills only")
        fun testProtectedPlayerRemovedFromWerewolfKillsOnly() {
            val targetId = 2
            val accumulatedState = ActionExecutionResult(
                deaths = mutableMapOf(
                    DeathCause.WEREWOLF to mutableListOf(targetId),
                    DeathCause.POISON to mutableListOf(targetId)
                ),
                protectedPlayers = mutableSetOf(targetId)
            )

            val dummyAction = RoleActionInstance(
                actor = 0,
                actorRole = "SYSTEM",
                actionDefinitionId = ActionDefinitionId.DEATH_RESOLUTION,
                targets = mutableListOf(),
                submittedBy = ActionSubmissionSource.JUDGE,
                status = ActionStatus.SUBMITTED
            )

            val result = action.execute(session, dummyAction, accumulatedState)

            // Should be removed from werewolf kills
            assertFalse(result.deaths[DeathCause.WEREWOLF]?.contains(targetId) == true)
            // But not from poison
            assertTrue(result.deaths[DeathCause.POISON]?.contains(targetId) == true)
        }

        @Test
        @DisplayName("SPECIAL CASE: Witch save + guard protection = player dies (DOUBLE_PROTECTION)")
        fun testDoubleProtectionPlayerDies() {
            val targetId = 2
            // Player is killed by werewolf, saved by witch, AND protected by guard
            val accumulatedState = ActionExecutionResult(
                deaths = mutableMapOf(DeathCause.WEREWOLF to mutableListOf(targetId)),
                saved = mutableListOf(targetId),
                protectedPlayers = mutableSetOf(targetId)
            )

            val dummyAction = RoleActionInstance(
                actor = 0,
                actorRole = "SYSTEM",
                actionDefinitionId = ActionDefinitionId.DEATH_RESOLUTION,
                targets = mutableListOf(),
                submittedBy = ActionSubmissionSource.JUDGE,
                status = ActionStatus.SUBMITTED
            )

            val result = action.execute(session, dummyAction, accumulatedState)

            // Player should die from DOUBLE_PROTECTION
            assertTrue(result.deaths.containsKey(DeathCause.DOUBLE_PROTECTION))
            assertTrue(result.deaths[DeathCause.DOUBLE_PROTECTION]?.contains(targetId) == true)

            // Should NOT be in other death lists
            assertFalse(result.deaths[DeathCause.WEREWOLF]?.contains(targetId) == true)
        }

        @Test
        @DisplayName("SPECIAL CASE: Multiple players with double protection all die")
        fun testMultipleDoubleProtectionPlayersDie() {
            val target1 = 2
            val target2 = 3
            val accumulatedState = ActionExecutionResult(
                deaths = mutableMapOf(DeathCause.WEREWOLF to mutableListOf(target1, target2)),
                saved = mutableListOf(target1, target2),
                protectedPlayers = mutableSetOf(target1, target2)
            )

            val dummyAction = RoleActionInstance(
                actor = 0,
                actorRole = "SYSTEM",
                actionDefinitionId = ActionDefinitionId.DEATH_RESOLUTION,
                targets = mutableListOf(),
                submittedBy = ActionSubmissionSource.JUDGE,
                status = ActionStatus.SUBMITTED
            )

            val result = action.execute(session, dummyAction, accumulatedState)

            // Both should die from DOUBLE_PROTECTION
            assertTrue(result.deaths.containsKey(DeathCause.DOUBLE_PROTECTION))
            assertEquals(2, result.deaths[DeathCause.DOUBLE_PROTECTION]?.size)
            assertTrue(result.deaths[DeathCause.DOUBLE_PROTECTION]?.contains(target1) == true)
            assertTrue(result.deaths[DeathCause.DOUBLE_PROTECTION]?.contains(target2) == true)
        }

        @Test
        @DisplayName("SPECIAL CASE: Protection alone prevents death, not double protection")
        fun testProtectionAlonePreventsDeathNotDoublyProtected() {
            val targetId = 2
            // Only protected, not saved
            val accumulatedState = ActionExecutionResult(
                deaths = mutableMapOf(DeathCause.WEREWOLF to mutableListOf(targetId)),
                protectedPlayers = mutableSetOf(targetId)
            )

            val dummyAction = RoleActionInstance(
                actor = 0,
                actorRole = "SYSTEM",
                actionDefinitionId = ActionDefinitionId.DEATH_RESOLUTION,
                targets = mutableListOf(),
                submittedBy = ActionSubmissionSource.JUDGE,
                status = ActionStatus.SUBMITTED
            )

            val result = action.execute(session, dummyAction, accumulatedState)

            // Should NOT die - protection saves them
            result.deaths.forEach { (_, deaths) ->
                assertFalse(deaths.contains(targetId))
            }

            // Should NOT be double protected
            assertFalse(result.deaths.containsKey(DeathCause.DOUBLE_PROTECTION))
        }

        @Test
        @DisplayName("SPECIAL CASE: Witch save alone prevents death, not double protection")
        fun testWitchSaveAlonePreventsDeathNotDoublyProtected() {
            val targetId = 2
            // Only saved, not protected
            val accumulatedState = ActionExecutionResult(
                deaths = mutableMapOf(DeathCause.WEREWOLF to mutableListOf(targetId)),
                saved = mutableListOf(targetId)
            )

            val dummyAction = RoleActionInstance(
                actor = 0,
                actorRole = "SYSTEM",
                actionDefinitionId = ActionDefinitionId.DEATH_RESOLUTION,
                targets = mutableListOf(),
                submittedBy = ActionSubmissionSource.JUDGE,
                status = ActionStatus.SUBMITTED
            )

            val result = action.execute(session, dummyAction, accumulatedState)

            // Should NOT die - witch saves them
            result.deaths.forEach { (_, deaths) ->
                assertFalse(deaths.contains(targetId))
            }

            // Should NOT be double protected
            assertFalse(result.deaths.containsKey(DeathCause.DOUBLE_PROTECTION))
        }

        @Test
        @DisplayName("Empty death list is removed from results")
        fun testEmptyDeathListRemoved() {
            val accumulatedState = ActionExecutionResult(
                deaths = mutableMapOf(DeathCause.WEREWOLF to mutableListOf<Int>())
            )

            val dummyAction = RoleActionInstance(
                actor = 0,
                actorRole = "SYSTEM",
                actionDefinitionId = ActionDefinitionId.DEATH_RESOLUTION,
                targets = mutableListOf(),
                submittedBy = ActionSubmissionSource.JUDGE,
                status = ActionStatus.SUBMITTED
            )

            val result = action.execute(session, dummyAction, accumulatedState)

            // Empty death list should be removed
            assertFalse(result.deaths.containsKey(DeathCause.WEREWOLF))
        }
    }

    @Nested
    @DisplayName("Complex Action Scenarios")
    inner class ComplexActionScenarios {

        @Test
        @DisplayName("Scenario: Werewolf kills, witch saves, guard protects = player dies")
        fun testCompleteDoubleProtectionScenario() {
            val werewolfId = 1
            val targetId = 2
            val witchId = 5
            val guardId = 4

            // Step 1: Werewolf kills
            val wolfKillAction = RoleActionInstance(
                actor = werewolfId,
                actorRole = "狼人",
                actionDefinitionId = ActionDefinitionId.WEREWOLF_KILL,
                targets = mutableListOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )
            val wolfExecutor = WerewolfKillAction()
            var result = wolfExecutor.execute(session, wolfKillAction, ActionExecutionResult())
            assertTrue(result.deaths[DeathCause.WEREWOLF]?.contains(targetId) == true)

            // Step 2: Witch saves
            val witchSaveAction = RoleActionInstance(
                actor = witchId,
                actorRole = "女巫",
                actionDefinitionId = ActionDefinitionId.WITCH_ANTIDOTE,
                targets = mutableListOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )
            val witchExecutor = WitchAntidoteAction()
            result = witchExecutor.execute(session, witchSaveAction, result)
            assertTrue(result.saved.contains(targetId))

            // Step 3: Guard protects
            val guardProtectAction = RoleActionInstance(
                actor = guardId,
                actorRole = "守衛",
                actionDefinitionId = ActionDefinitionId.GUARD_PROTECT,
                targets = mutableListOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )
            val guardExecutor = GuardProtectAction()
            session.day = 1
            result = guardExecutor.execute(session, guardProtectAction, result)
            assertTrue(result.protectedPlayers.contains(targetId))

            // Step 4: Death resolution
            val deathResolutionAction = RoleActionInstance(
                actor = 0,
                actorRole = "SYSTEM",
                actionDefinitionId = ActionDefinitionId.DEATH_RESOLUTION,
                targets = mutableListOf(),
                submittedBy = ActionSubmissionSource.JUDGE,
                status = ActionStatus.SUBMITTED
            )
            val deathExecutor = DeathResolutionAction()
            result = deathExecutor.execute(session, deathResolutionAction, result)

            // FINAL: Player should die from DOUBLE_PROTECTION
            assertTrue(result.deaths.containsKey(DeathCause.DOUBLE_PROTECTION))
            assertTrue(result.deaths[DeathCause.DOUBLE_PROTECTION]?.contains(targetId) == true)
        }

        @Test
        @DisplayName("Scenario: Two werewolves kill, witch saves one, guard protects the other")
        fun testComplexMultiKillScenario() {
            val wolf1 = 1
            val wolf2 = 3
            val target1 = 2
            val target2 = 4
            val witchId = 5
            val guardId = 4

            var result = ActionExecutionResult()

            // Wolf 1 kills target 1
            val wolfKill1 = RoleActionInstance(
                actor = wolf1,
                actorRole = "狼人",
                actionDefinitionId = ActionDefinitionId.WEREWOLF_KILL,
                targets = mutableListOf(target1),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )
            result = WerewolfKillAction().execute(session, wolfKill1, result)

            // Wolf 2 kills target 2
            val wolfKill2 = RoleActionInstance(
                actor = wolf2,
                actorRole = "狼人",
                actionDefinitionId = ActionDefinitionId.WEREWOLF_KILL,
                targets = mutableListOf(target2),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )
            result = WerewolfKillAction().execute(session, wolfKill2, result)

            assertEquals(2, result.deaths[DeathCause.WEREWOLF]?.size)

            // Witch saves target 1
            val witchSave = RoleActionInstance(
                actor = witchId,
                actorRole = "女巫",
                actionDefinitionId = ActionDefinitionId.WITCH_ANTIDOTE,
                targets = mutableListOf(target1),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )
            result = WitchAntidoteAction().execute(session, witchSave, result)
            assertTrue(result.saved.contains(target1))

            // Guard protects target 2
            val guardProtect = RoleActionInstance(
                actor = guardId,
                actorRole = "守衛",
                actionDefinitionId = ActionDefinitionId.GUARD_PROTECT,
                targets = mutableListOf(target2),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )
            session.day = 1
            result = GuardProtectAction().execute(session, guardProtect, result)
            assertTrue(result.protectedPlayers.contains(target2))

            // Death resolution
            val deathResolution = RoleActionInstance(
                actor = 0,
                actorRole = "SYSTEM",
                actionDefinitionId = ActionDefinitionId.DEATH_RESOLUTION,
                targets = mutableListOf(),
                submittedBy = ActionSubmissionSource.JUDGE,
                status = ActionStatus.SUBMITTED
            )
            result = DeathResolutionAction().execute(session, deathResolution, result)

            // Both should survive (one from save, one from protection)
            result.deaths.forEach { (_, deaths) ->
                assertFalse(deaths.contains(target1))
                assertFalse(deaths.contains(target2))
            }
        }
    }

    @Nested
    @DisplayName("Dark Merchant Action Tests")
    inner class DarkMerchantActionTests {
        private lateinit var tradeAction: DarkMerchantTradeSeerAction
        private lateinit var seerGift: MerchantSeerCheckAction
        private lateinit var testSession: Session

        @BeforeEach
        fun setup() {
            tradeAction = DarkMerchantTradeSeerAction()
            val roleRegistry = mock<dev.robothanzo.werewolf.game.roles.RoleRegistry>()
            seerGift = MerchantSeerCheckAction(roleRegistry)
            testSession = Session(guildId = 1L)

            // Mock static bridge
            WerewolfApplication.gameSessionService = mock()
        }

        @Test
        @DisplayName("Dark Merchant trading with werewolf should die")
        fun testTradeWithWolf() {
            val merchantId = 1
            val wolfId = 2
            val merchant = createPlayer(merchantId, 101L, listOf("黑市商人"))
            val wolf = createPlayer(wolfId, 102L, listOf("狼人"))

            testSession.players[merchantId.toString()] = merchant
            testSession.players[wolfId.toString()] = wolf

            val actionInstance = RoleActionInstance(
                actor = merchantId,
                actorRole = "黑市商人",
                actionDefinitionId = ActionDefinitionId.DARK_MERCHANT_TRADE_SEER,
                targets = mutableListOf(wolfId),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )

            val result = tradeAction.execute(testSession, actionInstance, ActionExecutionResult())

            assertTrue(result.deaths.containsKey(DeathCause.TRADED_WITH_WOLF))
            assertTrue(result.deaths[DeathCause.TRADED_WITH_WOLF]?.contains(merchantId) == true)
        }

        @Test
        @DisplayName("Dark Merchant trading with villager should succeed and grant skill")
        fun testTradeWithVillager() {
            val merchantId = 1
            val villagerId = 2
            val merchant = createPlayer(merchantId, 101L, listOf("黑市商人"))
            val villager = createPlayer(villagerId, 102L, listOf("平民"))

            testSession.players[merchantId.toString()] = merchant
            testSession.players[villagerId.toString()] = villager

            val actionInstance = RoleActionInstance(
                actor = merchantId,
                actorRole = "黑市商人",
                actionDefinitionId = ActionDefinitionId.DARK_MERCHANT_TRADE_SEER,
                targets = mutableListOf(villagerId),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )

            val result = tradeAction.execute(testSession, actionInstance, ActionExecutionResult())

            assertFalse(result.deaths.containsKey(DeathCause.TRADED_WITH_WOLF))

            // Verify skill was granted in playerOwnedActions
            val villagerActions = testSession.stateData.playerOwnedActions[villagerId]
            assertNotNull(villagerActions)
            assertEquals(1, villagerActions!![ActionDefinitionId.MERCHANT_SEER_CHECK.toString()])
        }

        @Test
        @DisplayName("Gifted action should be available through getAvailableActionsForPlayer")
        fun testGiftedActionAvailability() {
            val villagerId = 2
            val villager = createPlayer(villagerId, 102L, listOf("平民"))
            testSession.players[villagerId.toString()] = villager
            testSession.currentState = "NIGHT_PHASE"

            // Grant a gift
            testSession.stateData.playerOwnedActions[villagerId] = mutableMapOf(
                ActionDefinitionId.MERCHANT_SEER_CHECK.toString() to 1
            )

            val roleRegistry = mock<dev.robothanzo.werewolf.game.roles.RoleRegistry>()
            val seerGiftAction = mock<RoleAction>()
            whenever(seerGiftAction.actionId).thenReturn(ActionDefinitionId.MERCHANT_SEER_CHECK)
            whenever(seerGiftAction.timing).thenReturn(ActionTiming.NIGHT)
            whenever(seerGiftAction.usageLimit).thenReturn(1)
            whenever(seerGiftAction.getUsageCount(any(), any())).thenReturn(0)

            whenever(roleRegistry.getAction(ActionDefinitionId.MERCHANT_SEER_CHECK)).thenReturn(seerGiftAction)

            val availableActions = testSession.getAvailableActionsForPlayer(villagerId, roleRegistry)

            assertTrue(availableActions.any { it.actionId == ActionDefinitionId.MERCHANT_SEER_CHECK })
        }

        @Test
        @DisplayName("Dark Merchant cannot trade with self")
        fun testCannotTradeWithSelf() {
            val merchantId = 1
            val merchant = createPlayer(merchantId, 101L, listOf("黑市商人"))
            testSession.players[merchantId.toString()] = merchant

            // Check eligible targets
            val eligible =
                tradeAction.eligibleTargets(testSession, merchantId, listOf(merchantId), ActionExecutionResult())
            assertFalse(eligible.contains(merchantId))
        }

        @Test
        @DisplayName("Dark Merchant actions are mutually exclusive")
        fun testMutualExclusion() {
            val merchantId = 1
            // Setup player owned actions logic is not needed for isAvailable calc of the merchant himself usually,
            // unless it's death trigger. Dark Merchant is NIGHT timing.

            // 1. If one action is executed history, others are unavailable
            val executedAction = RoleActionInstance(
                actor = merchantId,
                actorRole = "黑市商人",
                actionDefinitionId = ActionDefinitionId.DARK_MERCHANT_TRADE_POISON, // different type from tradeAction (SEER)
                targets = mutableListOf(2),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.PROCESSED
            )
            testSession.stateData.executedActions[1] = mutableListOf(executedAction)

            assertFalse(
                tradeAction.isAvailable(testSession, merchantId),
                "Should be unavailable if Poison trade was executed"
            )

            // Reset
            testSession.stateData.executedActions.clear()

            // 2. If one action is currently submitted, others are unavailable
            val submittedAction = RoleActionInstance(
                actor = merchantId,
                actorRole = "黑市商人",
                actionDefinitionId = ActionDefinitionId.DARK_MERCHANT_TRADE_GUN, // different from SEER
                targets = mutableListOf(2),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )
            testSession.stateData.submittedActions.add(submittedAction)

            assertFalse(
                tradeAction.isAvailable(testSession, merchantId),
                "Should be unavailable if Gun trade is submitted"
            )

            // 3. Ensure 'isAvailable' logic relies on prefix logic primarily for exclusion
            // (The usage limit check in super would also block it if it was the SAME action, but here we test DIFFERENT action)
        }
    }

    @Nested
    @DisplayName("Wolf Brother Special Tests")
    inner class WolfBrotherTests {
        private lateinit var seerAction: SeerCheckAction
        private lateinit var testSession: Session
        private lateinit var mockChannel: TextChannel

        @Suppress("DEPRECATION")
        private lateinit var mockMessageAction: MessageCreateAction

        @BeforeEach
        fun setup() {
            val roleRegistry = mock<dev.robothanzo.werewolf.game.roles.RoleRegistry>()
            val wolfRole = mock<Role>()
            whenever(wolfRole.camp).thenReturn(Camp.WEREWOLF)
            whenever(roleRegistry.getRole("狼人")).thenReturn(wolfRole)
            whenever(roleRegistry.getRole("狼弟")).thenReturn(wolfRole)

            seerAction = SeerCheckAction(roleRegistry)
            testSession = Session(guildId = 1L)

            mockChannel = mock<TextChannel>()
            mockMessageAction = mock<MessageCreateAction>()
            whenever(mockChannel.sendMessage(any<String>())).thenReturn(mockMessageAction)
        }

        @Test
        @DisplayName("Seer checking Wolf Younger Brother when Wolf Brother is alive should return Good")
        fun testSeerCheckYoungerBrotherAlive() {
            val seerId = 1
            val youngerBrotherId = 2
            val brotherId = 3

            val seer = createPlayer(seerId, 101L, listOf("預言家")).apply {
                session = testSession
                whenever(WerewolfApplication.jda.getTextChannelById(channelId)).thenReturn(
                    mockChannel
                )
            }
            val youngerBrother = createPlayer(youngerBrotherId, 102L, listOf("狼弟"))
            val brother = createPlayer(brotherId, 103L, listOf("狼兄"))

            testSession.players[seerId.toString()] = seer
            testSession.players[youngerBrotherId.toString()] = youngerBrother
            testSession.players[brotherId.toString()] = brother

            val actionInstance = RoleActionInstance(
                actor = seerId,
                actorRole = "預言家",
                actionDefinitionId = ActionDefinitionId.SEER_CHECK,
                targets = mutableListOf(youngerBrotherId),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )

            seerAction.execute(testSession, actionInstance, ActionExecutionResult())

            verify(mockChannel).sendMessage(org.mockito.kotlin.check<String> { content ->
                assertTrue(content.contains("好人"))
            })
        }

        @Test
        @DisplayName("Seer checking Wolf Younger Brother when Wolf Brother is dead should return Wolf")
        fun testSeerCheckYoungerBrotherDead() {
            val seerId = 1
            val youngerBrotherId = 2
            val brotherId = 3

            val seer = createPlayer(seerId, 101L, listOf("預言家")).apply {
                this.session = testSession
                whenever(WerewolfApplication.jda.getTextChannelById(channelId)).thenReturn(
                    mockChannel
                )
            }
            val youngerBrother = createPlayer(youngerBrotherId, 102L, listOf("狼弟"))
            val brother = createPlayer(brotherId, 103L, listOf("狼兄")).apply {
                deadRoles = mutableListOf("狼兄") // Mark as dead
            }

            testSession.players[seerId.toString()] = seer
            testSession.players[youngerBrotherId.toString()] = youngerBrother
            testSession.players[brotherId.toString()] = brother

            val actionInstance = RoleActionInstance(
                actor = seerId,
                actorRole = "預言家",
                actionDefinitionId = ActionDefinitionId.SEER_CHECK,
                targets = mutableListOf(youngerBrotherId),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = ActionStatus.SUBMITTED
            )

            seerAction.execute(testSession, actionInstance, ActionExecutionResult())

            verify(mockChannel).sendMessage(org.mockito.kotlin.check<String> { content ->
                assertTrue(content.contains("狼人"))
            })
        }
    }
}
