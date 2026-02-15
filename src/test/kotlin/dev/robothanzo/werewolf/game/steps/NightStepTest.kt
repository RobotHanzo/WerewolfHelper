package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.NightPhase
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import dev.robothanzo.werewolf.game.roles.actions.RoleActionExecutor
import dev.robothanzo.werewolf.service.ActionUIService
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.GameStateService
import dev.robothanzo.werewolf.service.SpeechService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class NightStepTest {

    @Mock
    private lateinit var speechService: SpeechService
    @Mock
    private lateinit var actionUIService: ActionUIService
    @Mock
    private lateinit var roleRegistry: RoleRegistry
    @Mock
    private lateinit var roleActionExecutor: RoleActionExecutor
    @Mock
    private lateinit var gameSessionService: GameSessionService
    @Mock
    private lateinit var gameStateService: GameStateService

    @Test
    fun `processNightPhases should skip skippable tasks when early completion occurs`() = runBlocking {
        // Arrange
        val step = NightStep(speechService, actionUIService, roleRegistry, roleActionExecutor, gameSessionService)
        
        val executedTasks = mutableListOf<String>()

        val task1 = object : NightTask {
            override val phase = NightPhase.NIGHTMARE_ACTION
            override val isSkippable = true
            override suspend fun execute(step: NightStep, guildId: Long): Boolean {
                executedTasks.add("Task1")
                return false // Early exit (e.g. finished early)
            }
        }

        val task2 = object : NightTask {
            override val phase = NightPhase.NIGHTMARE_ACTION
            override val isSkippable = true
            override suspend fun execute(step: NightStep, guildId: Long): Boolean {
                executedTasks.add("Task2")
                return true
            }
        }
        
        val task3 = object : NightTask {
            override val phase = NightPhase.WOLF_YOUNGER_BROTHER_ACTION
            override val isSkippable = true // explicit for clarify
            override suspend fun execute(step: NightStep, guildId: Long): Boolean {
                executedTasks.add("Task3")
                return true
            }
        }

        // Act
        step.processNightPhases(1L, gameStateService, listOf(task1, task2, task3))

        // Assert
        // Task1 returns false (stop phase), so Task2 (skippable, same phase) should be removed.
        // Task3 (different phase) should execute.
        assertEquals(listOf("Task1", "Task3"), executedTasks)
    }

    @Test
    fun `processNightPhases should NOT skip non-skippable tasks when early completion occurs`() = runBlocking {
        // Arrange
        val step = NightStep(speechService, actionUIService, roleRegistry, roleActionExecutor, gameSessionService)
        
        val executedTasks = mutableListOf<String>()

        val task1 = object : NightTask {
            override val phase = NightPhase.NIGHTMARE_ACTION
            override val isSkippable = true
            override suspend fun execute(step: NightStep, guildId: Long): Boolean {
                executedTasks.add("Task1")
                return false // Early exit
            }
        }

        val task2 = object : NightTask {
            override val phase = NightPhase.NIGHTMARE_ACTION
            override val isSkippable = false // Non-skippable (e.g. Cleanup)
            override suspend fun execute(step: NightStep, guildId: Long): Boolean {
                executedTasks.add("Task2")
                return true
            }
        }
        
        val task3 = object : NightTask {
            override val phase = NightPhase.WOLF_YOUNGER_BROTHER_ACTION
            override suspend fun execute(step: NightStep, guildId: Long): Boolean {
                executedTasks.add("Task3")
                return true
            }
        }

        // Act
        step.processNightPhases(1L, gameStateService, listOf(task1, task2, task3))

        // Assert
        // Task1 returns false. Task2 is non-skippable, so it should execute despite being same phase.
        // Task3 executes normally.
        assertEquals(listOf("Task1", "Task2", "Task3"), executedTasks)
    }

    @Test
    fun `processNightPhases should continue execution (not skip) when task returns true (Timeout)`() = runBlocking {
        // Arrange
        val step = NightStep(speechService, actionUIService, roleRegistry, roleActionExecutor, gameSessionService)
        
        val executedTasks = mutableListOf<String>()

        val task1 = object : NightTask {
            override val phase = NightPhase.NIGHTMARE_ACTION
            override val isSkippable = true
            override suspend fun execute(step: NightStep, guildId: Long): Boolean {
                executedTasks.add("Task1")
                return true // Timeout occurred, continue phase normally
            }
        }

        val task2 = object : NightTask {
            override val phase = NightPhase.NIGHTMARE_ACTION
            override val isSkippable = true
            override suspend fun execute(step: NightStep, guildId: Long): Boolean {
                executedTasks.add("Task2")
                return true
            }
        }

        // Act
        step.processNightPhases(1L, gameStateService, listOf(task1, task2))

        // Assert
        // Task1 returns true (continue), so Task2 should execute.
        assertEquals(listOf("Task1", "Task2"), executedTasks)
    }

    @Test
    fun `processNightPhases should handle exceptions gracefully and skip remaining skippable tasks`() = runBlocking {
        // Arrange
        val step = NightStep(speechService, actionUIService, roleRegistry, roleActionExecutor, gameSessionService)
        
        val executedTasks = mutableListOf<String>()

        val task1 = object : NightTask {
            override val phase = NightPhase.NIGHTMARE_ACTION
            override val isSkippable = true
            override suspend fun execute(step: NightStep, guildId: Long): Boolean {
                executedTasks.add("Task1")
                throw RuntimeException("Something went wrong!")
            }
        }

        val task2 = object : NightTask {
            override val phase = NightPhase.NIGHTMARE_ACTION
            override val isSkippable = true
            override suspend fun execute(step: NightStep, guildId: Long): Boolean {
                executedTasks.add("Task2")
                return true
            }
        }
        
        val task3 = object : NightTask {
            override val phase = NightPhase.NIGHTMARE_ACTION
            override val isSkippable = false // Cleanup
            override suspend fun execute(step: NightStep, guildId: Long): Boolean {
                executedTasks.add("Task3")
                return true
            }
        }

        // Act
        step.processNightPhases(1L, gameStateService, listOf(task1, task2, task3))

        // Assert
        // Task1 throws exception. Internally caught, returns false (stop phase).
        // Task2 is skippable -> should be skipped.
        // Task3 is non-skippable -> should run.
        assertEquals(listOf("Task1", "Task3"), executedTasks)
    }

    @Test
    fun `NightmareWait logic - Should return FALSE when finished EARLY (condition met)`() = runBlocking {
        // Arrange
        val step = mock<NightStep>()
        whenever(step.waitForCondition(anyLong(), any(), any())).thenReturn(true) // Condition met early -> wait returns true

        val task = NightSequence.NightmareWait

        // Act
        val result = task.execute(step, 1L)

        // Assert
        // Logic: return !finishedEarly
        // finishedEarly = true
        // result should be false (stop phase)
        assertFalse(result, "Should return false (stop phase) if nightmare finished early")
    }

    @Test
    fun `NightmareWait logic - Should return TRUE when TIMEOUT (condition not met)`() = runBlocking {
        // Arrange
        val step = mock<NightStep>()
        whenever(step.waitForCondition(anyLong(), any(), any())).thenReturn(false) // Timeout -> wait returns false

        val task = NightSequence.NightmareWait

        // Act
        val result = task.execute(step, 1L)

        // Assert
        // Logic: return !finishedEarly
        // finishedEarly = false
        // result should be true (continue phase -> maybe cleanup or next step)
        assertTrue(result, "Should return true (continue phase) if nightmare timed out")
    }

    @Test
    fun `WerewolfVotingWait logic - Should return FALSE when finished EARLY (condition met)`() = runBlocking {
        val step = mock<NightStep>()
        whenever(step.waitForCondition(anyLong(), any(), any())).thenReturn(true) 

        val task = NightSequence.WerewolfVotingWait
        val result = task.execute(step, 1L)

        assertFalse(result, "Should return false (stop phase) if voting finished early")
    }

    @Test
    fun `WerewolfVotingWait logic - Should return TRUE when TIMEOUT (condition not met)`() = runBlocking {
        val step = mock<NightStep>()
        whenever(step.waitForCondition(anyLong(), any(), any())).thenReturn(false)

        val task = NightSequence.WerewolfVotingWait
        val result = task.execute(step, 1L)

        assertTrue(result, "Should return true (continue phase) if voting timed out")
    }
}
