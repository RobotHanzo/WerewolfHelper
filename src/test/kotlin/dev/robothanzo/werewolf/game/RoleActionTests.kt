package dev.robothanzo.werewolf.game.roles.actions

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionSubmissionSource
import dev.robothanzo.werewolf.game.model.DeathCause
import dev.robothanzo.werewolf.game.model.RoleActionInstance
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.User
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
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
            val targetId = 2L
            val action = RoleActionInstance(
                actor = 1L,
                actionDefinitionId = "WEREWOLF_KILL",
                targets = listOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER
            )

            val result = this.action.execute(session, action, ActionExecutionResult())

            assertTrue(result.deaths.containsKey(DeathCause.WEREWOLF))
            assertTrue(result.deaths[DeathCause.WEREWOLF]?.contains(targetId) == true)
        }

        @Test
        @DisplayName("Werewolf kill with no targets")
        fun testWerewolfKillNoTarget() {
            val action = RoleActionInstance(
                actor = 1L,
                actionDefinitionId = "WEREWOLF_KILL",
                targets = emptyList(),
                submittedBy = ActionSubmissionSource.PLAYER
            )

            val result = this.action.execute(session, action, ActionExecutionResult())

            assertTrue(result.deaths.isEmpty())
        }

        @Test
        @DisplayName("Multiple werewolves killing same target")
        fun testMultipleWerewolvesKillingSameTarget() {
            val targetId = 2L
            val action1 = RoleActionInstance(
                actor = 1L,
                actionDefinitionId = "WEREWOLF_KILL",
                targets = listOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER
            )
            val action2 = RoleActionInstance(
                actor = 3L,
                actionDefinitionId = "WEREWOLF_KILL",
                targets = listOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER
            )

            var result = this.action.execute(session, action1, ActionExecutionResult())
            result = this.action.execute(session, action2, result)

            // Target should be in death list (may appear multiple times)
            val deaths = result.deaths[DeathCause.WEREWOLF] ?: emptyList()
            assertTrue(deaths.contains(targetId))
            assertTrue(deaths.size >= 1)
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
            val targetId = 2L
            val wolfKillAction = RoleActionInstance(
                actor = 1L,
                actionDefinitionId = "WEREWOLF_KILL",
                targets = listOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER
            )
            val witchAntidoteAction = RoleActionInstance(
                actor = 5L,
                actionDefinitionId = "WITCH_ANTIDOTE",
                targets = listOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER
            )

            val wolfKillExecutor = WerewolfKillAction()
            var result = wolfKillExecutor.execute(session, wolfKillAction, ActionExecutionResult())
            result = action.execute(session, witchAntidoteAction, result)

            assertTrue(result.saved.contains(targetId))
        }

        @Test
        @DisplayName("Witch cannot save player not being killed")
        fun testWitchCannotSaveNonTarget() {
            val targetId = 2L
            val saveTargetId = 3L
            val wolfKillAction = RoleActionInstance(
                actor = 1L,
                actionDefinitionId = "WEREWOLF_KILL",
                targets = listOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER
            )
            val witchAntidoteAction = RoleActionInstance(
                actor = 5L,
                actionDefinitionId = "WITCH_ANTIDOTE",
                targets = listOf(saveTargetId),
                submittedBy = ActionSubmissionSource.PLAYER
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
            session.stateData["settings"] = mapOf("witchCanSaveSelf" to true)
            val witchId = 5L
            val wolfKillAction = RoleActionInstance(
                actor = 1L,
                actionDefinitionId = "WEREWOLF_KILL",
                targets = listOf(witchId),
                submittedBy = ActionSubmissionSource.PLAYER
            )
            val witchAntidoteAction = RoleActionInstance(
                actor = witchId,
                actionDefinitionId = "WITCH_ANTIDOTE",
                targets = listOf(witchId),
                submittedBy = ActionSubmissionSource.PLAYER
            )

            val wolfKillExecutor = WerewolfKillAction()
            var result = wolfKillExecutor.execute(session, wolfKillAction, ActionExecutionResult())
            result = action.execute(session, witchAntidoteAction, result)

            assertTrue(result.saved.contains(witchId))
        }

        @Test
        @DisplayName("Witch cannot save self when setting disallows")
        fun testWitchCannotSaveSelfWhenDisallowed() {
            session.stateData["settings"] = mapOf("witchCanSaveSelf" to false)
            val witchId = 5L
            val wolfKillAction = RoleActionInstance(
                actor = 1L,
                actionDefinitionId = "WEREWOLF_KILL",
                targets = listOf(witchId),
                submittedBy = ActionSubmissionSource.PLAYER
            )
            val witchAntidoteAction = RoleActionInstance(
                actor = witchId,
                actionDefinitionId = "WITCH_ANTIDOTE",
                targets = listOf(witchId),
                submittedBy = ActionSubmissionSource.PLAYER
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

        @Test
        @DisplayName("Guard protects player from werewolf kill")
        fun testGuardProtectsFromKill() {
            val targetId = 2L
            val guardAction = RoleActionInstance(
                actor = 4L,
                actionDefinitionId = "GUARD_PROTECT",
                targets = listOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER
            )

            val result = action.execute(session, guardAction, ActionExecutionResult())

            assertTrue(result.protectedPlayers.contains(targetId))
        }

        @Test
        @DisplayName("Guard cannot protect same player on consecutive nights")
        fun testGuardCannotProtectSamePlayerConsecutively() {
            session.day = 2
            val targetId = 2L
            session.stateData["lastGuardProtected"] = targetId

            val guardAction = RoleActionInstance(
                actor = 4L,
                actionDefinitionId = "GUARD_PROTECT",
                targets = listOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER
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
            val targetId = 2L
            session.stateData["lastGuardProtected"] = targetId

            val guardAction = RoleActionInstance(
                actor = 4L,
                actionDefinitionId = "GUARD_PROTECT",
                targets = listOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER
            )

            val result = action.execute(session, guardAction, ActionExecutionResult())

            // Should protect on day 1 even if same as last night
            assertTrue(result.protectedPlayers.contains(targetId))
        }

        @Test
        @DisplayName("Guard can protect different player after protecting one previously")
        fun testGuardCanProtectDifferentPlayer() {
            session.day = 2
            val lastTargetId = 2L
            val newTargetId = 3L
            session.stateData["lastGuardProtected"] = lastTargetId

            val guardAction = RoleActionInstance(
                actor = 4L,
                actionDefinitionId = "GUARD_PROTECT",
                targets = listOf(newTargetId),
                submittedBy = ActionSubmissionSource.PLAYER
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
            val targetId = 2L
            val poisonAction = RoleActionInstance(
                actor = 5L,
                actionDefinitionId = "WITCH_POISON",
                targets = listOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER
            )

            val result = action.execute(session, poisonAction, ActionExecutionResult())

            assertTrue(result.deaths.containsKey(DeathCause.POISON))
            assertTrue(result.deaths[DeathCause.POISON]?.contains(targetId) == true)
        }

        @Test
        @DisplayName("Witch poison is ignored by protection")
        fun testWitchPoisonIgnoresProtection() {
            val targetId = 2L

            // Guard protects first
            val guardAction = RoleActionInstance(
                actor = 4L,
                actionDefinitionId = "GUARD_PROTECT",
                targets = listOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER
            )
            val guardExecutor = GuardProtectAction()
            var result = guardExecutor.execute(session, guardAction, ActionExecutionResult())

            // Witch poisons
            val poisonAction = RoleActionInstance(
                actor = 5L,
                actionDefinitionId = "WITCH_POISON",
                targets = listOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER
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

        @BeforeEach
        fun setup() {
            action = SeerCheckAction()
            testSession = Session(guildId = 123L)
            testSession.players = mutableMapOf(
                "1" to createPlayer(1, 1L, listOf("狼人")),
                "2" to createPlayer(2, 2L, listOf("平民")),
                "3" to createPlayer(3, 3L, listOf("預言家"))
            )
        }

        @Test
        @DisplayName("Seer checks werewolf")
        fun testSeerChecksWerewolf() {
            val targetId = 1L
            val checkAction = RoleActionInstance(
                actor = 3L,
                actionDefinitionId = "SEER_CHECK",
                targets = listOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER
            )

            val result = action.execute(testSession, checkAction, ActionExecutionResult())

            @Suppress("UNCHECKED_CAST")
            val seerChecks = result.metadata["seerChecks"] as? Map<Long, String>
            assertEquals("werewolf", seerChecks?.get(3L))
        }

        @Test
        @DisplayName("Seer checks villager")
        fun testSeerChecksVillager() {
            val targetId = 2L
            val checkAction = RoleActionInstance(
                actor = 3L,
                actionDefinitionId = "SEER_CHECK",
                targets = listOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER
            )

            val result = action.execute(testSession, checkAction, ActionExecutionResult())

            @Suppress("UNCHECKED_CAST")
            val seerChecks = result.metadata["seerChecks"] as? Map<Long, String>
            assertEquals("villager", seerChecks?.get(3L))
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
            val targetId = 2L
            val accumulatedState = ActionExecutionResult(
                deaths = mutableMapOf(DeathCause.WEREWOLF to mutableListOf(targetId)),
                saved = mutableListOf(targetId)
            )

            val dummyAction = RoleActionInstance(
                actor = 0L,
                actionDefinitionId = "DEATH_RESOLUTION",
                targets = emptyList(),
                submittedBy = ActionSubmissionSource.JUDGE
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
            val targetId = 2L
            val accumulatedState = ActionExecutionResult(
                deaths = mutableMapOf(
                    DeathCause.WEREWOLF to mutableListOf(targetId),
                    DeathCause.POISON to mutableListOf(targetId)
                ),
                protectedPlayers = mutableSetOf(targetId)
            )

            val dummyAction = RoleActionInstance(
                actor = 0L,
                actionDefinitionId = "DEATH_RESOLUTION",
                targets = emptyList(),
                submittedBy = ActionSubmissionSource.JUDGE
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
            val targetId = 2L
            // Player is killed by werewolf, saved by witch, AND protected by guard
            val accumulatedState = ActionExecutionResult(
                deaths = mutableMapOf(DeathCause.WEREWOLF to mutableListOf(targetId)),
                saved = mutableListOf(targetId),
                protectedPlayers = mutableSetOf(targetId)
            )

            val dummyAction = RoleActionInstance(
                actor = 0L,
                actionDefinitionId = "DEATH_RESOLUTION",
                targets = emptyList(),
                submittedBy = ActionSubmissionSource.JUDGE
            )

            val result = action.execute(session, dummyAction, accumulatedState)

            // Player should die from DOUBLE_PROTECTION
            assertTrue(result.deaths.containsKey(DeathCause.DOUBLE_PROTECTION))
            assertTrue(result.deaths[DeathCause.DOUBLE_PROTECTION]?.contains(targetId) == true)

            // Should NOT be in other death lists
            assertFalse(result.deaths[DeathCause.WEREWOLF]?.contains(targetId) == true)

            @Suppress("UNCHECKED_CAST")
            val doubleProtected = result.metadata["doubleProtectedPlayers"] as? List<Long>
            assertTrue(doubleProtected?.contains(targetId) == true)
        }

        @Test
        @DisplayName("SPECIAL CASE: Multiple players with double protection all die")
        fun testMultipleDoubleProtectionPlayersDie() {
            val target1 = 2L
            val target2 = 3L
            val accumulatedState = ActionExecutionResult(
                deaths = mutableMapOf(DeathCause.WEREWOLF to mutableListOf(target1, target2)),
                saved = mutableListOf(target1, target2),
                protectedPlayers = mutableSetOf(target1, target2)
            )

            val dummyAction = RoleActionInstance(
                actor = 0L,
                actionDefinitionId = "DEATH_RESOLUTION",
                targets = emptyList(),
                submittedBy = ActionSubmissionSource.JUDGE
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
            val targetId = 2L
            // Only protected, not saved
            val accumulatedState = ActionExecutionResult(
                deaths = mutableMapOf(DeathCause.WEREWOLF to mutableListOf(targetId)),
                protectedPlayers = mutableSetOf(targetId)
            )

            val dummyAction = RoleActionInstance(
                actor = 0L,
                actionDefinitionId = "DEATH_RESOLUTION",
                targets = emptyList(),
                submittedBy = ActionSubmissionSource.JUDGE
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
            val targetId = 2L
            // Only saved, not protected
            val accumulatedState = ActionExecutionResult(
                deaths = mutableMapOf(DeathCause.WEREWOLF to mutableListOf(targetId)),
                saved = mutableListOf(targetId)
            )

            val dummyAction = RoleActionInstance(
                actor = 0L,
                actionDefinitionId = "DEATH_RESOLUTION",
                targets = emptyList(),
                submittedBy = ActionSubmissionSource.JUDGE
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
                deaths = mutableMapOf(DeathCause.WEREWOLF to mutableListOf<Long>())
            )

            val dummyAction = RoleActionInstance(
                actor = 0L,
                actionDefinitionId = "DEATH_RESOLUTION",
                targets = emptyList(),
                submittedBy = ActionSubmissionSource.JUDGE
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
            val werewolfId = 1L
            val targetId = 2L
            val witchId = 5L
            val guardId = 4L

            // Step 1: Werewolf kills
            val wolfKillAction = RoleActionInstance(
                actor = werewolfId,
                actionDefinitionId = "WEREWOLF_KILL",
                targets = listOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER
            )
            val wolfExecutor = WerewolfKillAction()
            var result = wolfExecutor.execute(session, wolfKillAction, ActionExecutionResult())
            assertTrue(result.deaths[DeathCause.WEREWOLF]?.contains(targetId) == true)

            // Step 2: Witch saves
            val witchSaveAction = RoleActionInstance(
                actor = witchId,
                actionDefinitionId = "WITCH_ANTIDOTE",
                targets = listOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER
            )
            val witchExecutor = WitchAntidoteAction()
            result = witchExecutor.execute(session, witchSaveAction, result)
            assertTrue(result.saved.contains(targetId))

            // Step 3: Guard protects
            val guardProtectAction = RoleActionInstance(
                actor = guardId,
                actionDefinitionId = "GUARD_PROTECT",
                targets = listOf(targetId),
                submittedBy = ActionSubmissionSource.PLAYER
            )
            val guardExecutor = GuardProtectAction()
            session.day = 1
            result = guardExecutor.execute(session, guardProtectAction, result)
            assertTrue(result.protectedPlayers.contains(targetId))

            // Step 4: Death resolution
            val deathResolutionAction = RoleActionInstance(
                actor = 0L,
                actionDefinitionId = "DEATH_RESOLUTION",
                targets = emptyList(),
                submittedBy = ActionSubmissionSource.JUDGE
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
            val wolf1 = 1L
            val wolf2 = 3L
            val target1 = 2L
            val target2 = 4L
            val witchId = 5L
            val guardId = 4L

            var result = ActionExecutionResult()

            // Wolf 1 kills target 1
            val wolfKill1 = RoleActionInstance(
                actor = wolf1,
                actionDefinitionId = "WEREWOLF_KILL",
                targets = listOf(target1),
                submittedBy = ActionSubmissionSource.PLAYER
            )
            result = WerewolfKillAction().execute(session, wolfKill1, result)

            // Wolf 2 kills target 2
            val wolfKill2 = RoleActionInstance(
                actor = wolf2,
                actionDefinitionId = "WEREWOLF_KILL",
                targets = listOf(target2),
                submittedBy = ActionSubmissionSource.PLAYER
            )
            result = WerewolfKillAction().execute(session, wolfKill2, result)

            assertEquals(2, result.deaths[DeathCause.WEREWOLF]?.size)

            // Witch saves target 1
            val witchSave = RoleActionInstance(
                actor = witchId,
                actionDefinitionId = "WITCH_ANTIDOTE",
                targets = listOf(target1),
                submittedBy = ActionSubmissionSource.PLAYER
            )
            result = WitchAntidoteAction().execute(session, witchSave, result)
            assertTrue(result.saved.contains(target1))

            // Guard protects target 2
            val guardProtect = RoleActionInstance(
                actor = guardId,
                actionDefinitionId = "GUARD_PROTECT",
                targets = listOf(target2),
                submittedBy = ActionSubmissionSource.PLAYER
            )
            session.day = 1
            result = GuardProtectAction().execute(session, guardProtect, result)
            assertTrue(result.protectedPlayers.contains(target2))

            // Death resolution
            val deathResolution = RoleActionInstance(
                actor = 0L,
                actionDefinitionId = "DEATH_RESOLUTION",
                targets = emptyList(),
                submittedBy = ActionSubmissionSource.JUDGE
            )
            result = DeathResolutionAction().execute(session, deathResolution, result)

            // Both should survive (one from save, one from protection)
            result.deaths.forEach { (_, deaths) ->
                assertFalse(deaths.contains(target1))
                assertFalse(deaths.contains(target2))
            }
        }
    }
}
