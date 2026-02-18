# Game Steps — Agent Reference

## Directory Layout

```
steps/
├── SetupStep.kt, NightStep.kt, DayStep.kt, DeathAnnouncementStep.kt
├── SheriffElectionStep.kt, SpeechStep.kt, VotingStep.kt, JudgeDecisionStep.kt
└── tasks/
    ├── MagicianTasks.kt, NightmareTasks.kt, WolfYoungerBrotherTasks.kt
    ├── WerewolfVotingTasks.kt, RoleActionsTasks.kt
```

---

## Design Architecture — Three Levels

```
Step  (macro game state, one active per guild)
 └── Phase  (NightStep sub-state only, tracked via stateData.phaseType)
      └── Task  (atomic unit: Start → Wait → Cleanup)
```

Only `NightStep` uses the Phase/Task sub-system. All other steps manage their own timing.

---

## Level 1: Steps

### GameStep Base Class

Every step is a Spring `@Component` extending `GameStep`. Key contract:

| Method                        | Notes                                                                                                  |
|-------------------------------|--------------------------------------------------------------------------------------------------------|
| `id: String`                  | Unique key (e.g. `"NIGHT_STEP"`). Must match `startStep()` argument.                                   |
| `name: String`                | Display name (Chinese).                                                                                |
| `onStart(session, service)`   | Always call `super.onStart(...)` first — it sets `stepStartTime`.                                      |
| `onEnd(session, service)`     | Clean up timers, services, coroutines.                                                                 |
| `handleInput(session, input)` | Returns `Map<String, Any>` with at least `"success"`. Auto-saved by `GameStateServiceImpl` on success. |
| `getEndTime(session): Long`   | Frontend timer estimate. Default `0L` (indefinite).                                                    |

### Advancing Steps

- **`service.nextStep(session)`** — follows the `gameFlow` sequence with conditional skips and game-end checks. Use this
  normally.
- **`service.startStep(session, stepId)`** — jumps to a specific step. Use for non-linear transitions (`JUDGE_DECISION`,
  `SETUP`).

### `gameFlow` Sequence & Conditional Skips

```
NIGHT_STEP → DAY_STEP → SHERIFF_ELECTION* → DEATH_ANNOUNCEMENT
    ↑                                                  │
    └──────── VOTING_STEP ← SPEECH_STEP ←────────────┘
```

- `SHERIFF_ELECTION` skipped when `session.day != 0` (only runs Night 1 / Day 0).
- `SPEECH_STEP` + `VOTING_STEP` skipped when `detonatedThisDay == true` (jumps to `NIGHT_STEP`).
- If `session.hasEnded()` is non-`NOT_ENDED` (except heading into `DEATH_ANNOUNCEMENT`), flow redirects to
  `JUDGE_DECISION`; intended next step saved in `stateData.pendingNextStep`.
- `session.day` increments when entering `DEATH_ANNOUNCEMENT`. Night 1 runs with `day == 0`.

---

## Level 2: Phases (NightPhase)

```kotlin
enum class NightPhase(val defaultDurationMs: Long) {
    NIGHTMARE_ACTION           (60_000L),
    MAGICIAN_ACTION            (60_000L),
    WOLF_YOUNGER_BROTHER_ACTION(60_000L),
    WEREWOLF_VOTING            (90_000L),
    ROLE_ACTIONS               (60_000L)
}
```

Active phase tracked in `stateData.phaseType` (`null` between phases). `phaseStartTime`/`phaseEndTime` set by `Start`
task, cleared by `Cleanup` task.

**To add a new phase:** add entry to `NightPhase` → create `XxxTasks.kt` → add three objects to `NightSequence.TASKS`.

---

## Level 3: Tasks (NightTask)

### Interface

```kotlin
internal interface NightTask {
    val phase: NightPhase
    val isSkippable: Boolean get() = true
    suspend fun execute(step: NightStep, guildId: Long): Boolean
    // true  = phase continues (keep remaining tasks)
    // false = phase done/skipped (drop remaining skippable tasks for this phase)
    fun shouldExecute(session: Session): Boolean = true  // evaluated ONCE at queue build
}
```

### Start / Wait / Cleanup Pattern

| Task         | `isSkippable` | Returns                                           | Purpose                                               |
|--------------|---------------|---------------------------------------------------|-------------------------------------------------------|
| `XxxStart`   | `true`        | `true` if work exists, `false` to skip phase      | Set `phaseType`, prompt players via `ActionUIService` |
| `XxxWait`    | `true`        | `!finishedEarly` (true=timeout, false=done early) | `step.waitForCondition(...)`                          |
| `XxxCleanup` | **`false`**   | `false` always                                    | Clear `phaseType`, clean UI prompts, log end          |

`shouldExecute` is a **pre-filter** evaluated once when building the queue (e.g. "character alive?"). For runtime
conditions, return `false` from `Start.execute()` instead.

### Canonical Task Order (`NightSequence.TASKS`)

```
NightmareStart/Wait/Cleanup
MagicianStart/Wait/Cleanup
WolfYoungerBrotherStart/Wait/Cleanup
WerewolfVotingStart/Wait/Warning/FinalWait/Cleanup
RoleActionsStart/Wait/Cleanup
```

### Task File Skeleton

```kotlin
internal interface XxxTask : NightTask {
    override val phase get() = NightPhase.XXX_ACTION
    override fun shouldExecute(session: Session) = session.isCharacterAlive("角色名")
}

object XxxStart : XxxTask {
    override suspend fun execute(step: NightStep, guildId: Long): Boolean {
        return step.gameSessionService.withLockedSession(guildId) { s ->
            // set phaseType, phaseStartTime, phaseEndTime, prompt players
            // return true if work exists, false to skip
        }
    }
}

object XxxWait : XxxTask {
    override suspend fun execute(step: NightStep, guildId: Long): Boolean {
        val finishedEarly = step.waitForCondition(guildId, durationSeconds) { /* condition */ }
        return !finishedEarly
    }
}

object XxxCleanup : XxxTask {
    override val isSkippable = false
    override suspend fun execute(step: NightStep, guildId: Long): Boolean {
        step.gameSessionService.withLockedSession(guildId) { s ->
            step.actionUIService.cleanupExpiredPrompts(s)
            s.addLog(LogType.SYSTEM, "Xxx行動階段結束")
            s.stateData.phaseType = null
        }
        return false
    }
}
```

### `waitForCondition`

Signal-driven + 1 s poll loop. Wakes immediately on `ACTION_PROCESSED` event (via `phaseSignals`). Returns `true` if
condition met before timeout, `false` on timeout.

---

## Step Reference (Quick)

| Step ID              | Key Behaviour                                            | `getEndTime`                                                |
|----------------------|----------------------------------------------------------|-------------------------------------------------------------|
| `SETUP`              | Resets services; `start_game` → `NIGHT_STEP`             | —                                                           |
| `NIGHT_STEP`         | Runs task queue in coroutine; calls `nextStep` when done | Current phase remaining + future phases `defaultDurationMs` |
| `DAY_STEP`           | Plays morning audio; auto-advances after ~10 s           | `stepStartTime + 10_000`                                    |
| `DEATH_ANNOUNCEMENT` | Resolves night actions, processes cascading deaths       | `stepStartTime + (10 + triggers×30)×1000`                   |
| `SHERIFF_ELECTION`   | Delegates to `PoliceService`; dynamic stages             | Computed from `PoliceSession.State`                         |
| `SPEECH_STEP`        | Delegates to `SpeechService.startAutoSpeechFlow`         | Current speaker + queued speakers (180/210 s each)          |
| `VOTING_STEP`        | Delegates to `ExpelService`; handles PK speech           | PK speech time + 30 s, or `expelSession.endTime`            |
| `JUDGE_DECISION`     | Judge picks End or Continue; non-linear exit             | —                                                           |

---

## Key Gotchas

- **`@param:Lazy`** on `ActionUIService`, `GameSessionService`, `PoliceService` — breaks circular deps. Do not remove.
- **`withLockedSession`** — always use for mutations. Never hold the lock across a `suspend` that re-acquires it.
- **`nightScope`** — `SupervisorJob + Dispatchers.Default`. Cancelled in `onEnd` via
  `orchestrationJobs.remove(guildId)?.cancel()`.
- **`activeQueues`** — `internal` so `getEndTime` can read the live queue without locking.
- **`shouldExecute`** — evaluated once at queue build, not mid-run.
- **Imports** — `NightStep.kt` uses `import dev.robothanzo.werewolf.game.steps.tasks.*`. Tests must import task objects
  directly (e.g. `import ...tasks.NightmareWait`), never via `NightSequence.TaskName`.
- **`handleInput` auto-save** — `GameStateServiceImpl` saves session automatically on `"success" to true`. Steps don't
  need to save inside `handleInput`.
- **`votingEnded` flag** — returning `"votingEnded" to true` from `handleInput` triggers `nextStep` automatically (only
  `VotingStep` uses this).
