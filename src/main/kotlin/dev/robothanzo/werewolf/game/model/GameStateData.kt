package dev.robothanzo.werewolf.game.model

import io.swagger.v3.oas.annotations.media.Schema
import org.bson.codecs.pojo.annotations.BsonIgnore
import org.springframework.data.annotation.Transient


/**
 * Represents the sub-phases of the night.
 */
enum class NightPhase {
    NIGHTMARE_ACTION,
    WOLF_YOUNGER_BROTHER_ACTION,
    MAGICIAN_ACTION,
    WEREWOLF_VOTING,
    ROLE_ACTIONS
}

enum class ActionTiming {
    NIGHT,
    DAY,
    ANYTIME,
    DEATH_TRIGGER  // Actions triggered when a role holder dies (e.g., Hunter, Wolf King)
}

enum class ActionSubmissionSource {
    PLAYER,
    JUDGE,
    SYSTEM
}

/**
 * Status of a player's action during a phase
 */
enum class ActionStatus {
    PENDING, // 1. initial stage
    ACTING, // 2-1. when an action has been selected but not the targets
    SUBMITTED, // 3. when the action is fully submitted
    SKIPPED, // 2-2. when the action is skipped
    PROCESSED; // 4. when the action has been executed during death announcement

    /**
     * Returns true if the action is considered "executed" or "finalized" for the current phase.
     * This includes [SUBMITTED], [SKIPPED], and [PROCESSED] states.
     * Useful for checking if a player has completed their turn.
     */
    val executed: Boolean
        get() = this == SUBMITTED || this == SKIPPED || this == PROCESSED
}

const val SKIP_TARGET_ID = -1

data class RoleActionInstance(
    val actor: Int,
    val actorRole: String, // Role name of the actor at the time of action
    var actionDefinitionId: ActionDefinitionId?, // Null if not yet chosen
    val targets: MutableList<Int>,
    var submittedBy: ActionSubmissionSource,
    var status: ActionStatus, // PENDING, ACTING, SUBMITTED, SKIPPED
    var actionPromptId: Long? = null, // Discord message ID for action selection prompt
    var targetPromptId: Long? = null // Discord message ID for target selection prompt
)

/**
 * Group action state for coordinated actions like wolf kills
 */
data class WolvesActionState(
    val actionId: String,
    val electorates: List<Int>, // Player IDs of players (wolves) voting
    val votes: MutableList<WolfVote> = mutableListOf(),
    val promptMessageIds: MutableMap<Int, Long> = mutableMapOf(), // Player ID -> Discord message ID
    val finished: Boolean = false
)

data class WolfVote(
    val voterId: Int,
    val targetId: Int?
)

data class WolfMessage(
  @get:Schema(type = "string")
    val senderUserId: Long,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ExpelCandidateDto(
    val id: Int,
    val quit: Boolean,
    val voters: List<String>
)

data class ExpelStatus(
    val voting: Boolean,
    val endTime: Long?,
    val candidates: List<ExpelCandidateDto>
)

data class HistoricalPollRecord(
    val day: Int,
    val title: String,
    val votes: Map<Int, List<Long>> // TargetPlayerID -> List of Voter User IDs
)

data class PoliceEnrollmentRecord(
    val day: Int,
    val playerId: Int,
    val type: dev.robothanzo.werewolf.database.documents.ReplayEventType,
    val stage: dev.robothanzo.werewolf.database.documents.PoliceActionStage,
    val timestamp: Long = System.currentTimeMillis()
)

data class PoliceTransferRecord(
    val day: Int,
    val fromPlayerId: Int,
    val toPlayerId: Int,
    val timestamp: Long
)

/**
 * Structured game state data to replace the generic stateData map.
 */
data class GameStateData(
    var phaseType: NightPhase? = null,
    var phaseStartTime: Long = 0,
    var phaseEndTime: Long = 0,
    // RoleDefinition shall have defined them, copy them here to track the usage and process when extra action are granted
    @Schema(example = "{\"1\": {\"WEREWOLF_KILL\": 1}}")
    var playerOwnedActions: MutableMap<Int, MutableMap<String, Int>> = mutableMapOf(), // playerId -> (actionId -> usesLeft)

    @Schema(description = "List of actions submitted in the current phase but not yet executed")
    var submittedActions: MutableList<RoleActionInstance> = mutableListOf(), // Actions yet to be processed, executed at death announcement

    @Schema(example = "{\"1\": [{\"actor\": 1, \"actionDefinitionId\": \"WEREWOLF_KILL\", \"targets\": [2]}]}")
    var executedActions: MutableMap<Int, MutableList<RoleActionInstance>> = mutableMapOf(), // day -> List of executed actions

    // Wolf features, these are rotated daily (in game day, as the final wolf kill is applied at death announcement and stored into executedActions)
    @Schema(example = "{\"WEREWOLF_KILL\": {\"actionId\": \"WEREWOLF_KILL\", \"finished\": false}}")
    var wolfStates: MutableMap<String, WolvesActionState> = mutableMapOf(), // actionId -> state

    @Schema(description = "List of messages sent by wolves")
    var werewolfMessages: MutableList<WolfMessage> = mutableListOf(),

    @Schema(description = "Player ID of the Wolf Younger Brother if he is awakened this night")
    var wolfBrotherAwakenedPlayerId: Int? = null,
    @Schema(description = "Start time of the current game step")
    var stepStartTime: Long = 0,

    @Schema(description = "History of polls in this session")
    var historicalPolls: MutableList<HistoricalPollRecord> = mutableListOf(),

    @Schema(description = "History of police enrollment/unenrollment in this session")
    var policeEnrollmentHistory: MutableList<PoliceEnrollmentRecord> = mutableListOf(),

    @Schema(description = "History of police badge transfers in this session")
    var policeTransferHistory: MutableList<PoliceTransferRecord> = mutableListOf(),

    @Schema(description = "Start time of the game")
    var gameStartTime: Long = 0,

    @Schema(description = "The next step ID pending judge approval when game end condition is met")
    var pendingNextStep: String? = null,

    @Schema(description = "The reason for game end, if detected")
    var gameEndReason: String? = null,

    @Schema(description = "Flag indicating if asynchronous death processing is currently active")
    var deathProcessingInProgress: Boolean = false
) {
    // --- Transient fields for UI state synchronization ---
    @Transient
    @get:Schema(hidden = true)
    @BsonIgnore
    var speech: SpeechStatus? = null

    @Transient
    @get:Schema(hidden = true)
    @BsonIgnore
    var police: PoliceStatus? = null

    @Transient
    @get:Schema(hidden = true)
    @BsonIgnore
    var expel: ExpelStatus? = null

    // --- Computed Properties ---

    /**
     * Finds the target ID of the last successful werewolf kill in the current phase.
     */
    val nightWolfKillTargetId: Int?
        get() =
            submittedActions.find { it.actionDefinitionId == ActionDefinitionId.WEREWOLF_KILL && it.status == ActionStatus.SUBMITTED }?.targets?.firstOrNull()

    /**
     * Finds the day the Wolf Brother died.
     */
    val wolfBrotherDiedDay: Int?
        get() {
            return executedActions.entries.find { (_, actions) ->
                actions.any { it.actionDefinitionId == ActionDefinitionId.DEATH && it.actorRole == "狼兄" }
            }?.key
        }

    // For frontend display after death announcement only, shows who died last night
    var deadPlayers: List<Int> = emptyList()

    @Schema(description = "List of player IDs whose death events (last words, triggers) have been completed")
    var processedDeathPlayerIds: MutableList<Int> = mutableListOf()

    /**
     * Whether the Ghost Rider's reflection ability has been triggered.
     * Derived from executed actions.
     */
    /**
     * Whether the Ghost Rider's reflection ability has been triggered.
     * Derived from executed actions.
     */
    val ghostRiderReflected: Boolean
        get() = executedActions.values.flatten().any { it.actionDefinitionId == ActionDefinitionId.GHOST_RIDER_REFLECT }

    /**
     * Whether a wolf has detonated this day.
     * Derived from submitted actions (self-healing/cleared at night start).
     */
    @get:BsonIgnore
    val detonatedThisDay: Boolean
        get() = submittedActions.any { it.actionDefinitionId == ActionDefinitionId.WOLF_DETONATE }

    // --- Magician Swap Logic ---

    /**
     * The swap pairing for the current night.
     */
    @get:BsonIgnore
    val nightlySwap: Map<Int, Int>
        get() {
            val swapAction =
                submittedActions.find { it.actionDefinitionId == ActionDefinitionId.MAGICIAN_SWAP && it.status.executed }
            val targets = swapAction?.targets
            if (targets == null || targets.size != 2) return emptyMap()
            return mapOf(targets[0] to targets[1], targets[1] to targets[0])
        }

    /**
     * Set of players who have been swapped by the Magician in previous nights (or tonight).
     * Used to enforce "each number can only be swapped once" rule.
     */
    @get:BsonIgnore
    val magicianSwapTargets: Set<Int>
        get() {
            return executedActions.values.flatten()
                .filter { it.actionDefinitionId == ActionDefinitionId.MAGICIAN_SWAP }
                .flatMap { it.targets }
                .toSet()
        }

    /**
     * Returns the "Real Target" after applying Magician's swap.
     * If A and B are swapped, getRealTarget(A) -> B, getRealTarget(B) -> A.
     * Otherwise returns original targetId.
     */
    fun getRealTarget(targetId: Int): Int {
        return nightlySwap[targetId] ?: targetId
    }

    /**
     * Map of Dream Weaver targets per day (Day -> TargetId).
     * Computed from executed and submitted actions.
     */
    @get:BsonIgnore
    val dreamWeaverTargets: Map<Int, Int>
        get() {
            return executedActions.mapNotNull { (day, actions) ->
                val target = actions.find { it.actionDefinitionId == ActionDefinitionId.DREAM_WEAVER_LINK }
                    ?.targets?.firstOrNull()
                if (target != null) day to target else null
            }.toMap()
        }

    /**
     * Map of Nightmare fear targets per day (Day -> TargetId).
     * Computed from executed actions.
     */
    @get:BsonIgnore
    val nightmareFearTargets: Map<Int, Int>
        get() {
            return executedActions.mapNotNull { (day, actions) ->
                val target = actions.find { it.actionDefinitionId == ActionDefinitionId.NIGHTMARE_FEAR }
                    ?.targets?.firstOrNull()
                if (target != null) day to target else null
            }.toMap()
        }
}

/**
 * Result of night action resolution
 */
data class NightResolutionResult(
    val deaths: Map<DeathCause, List<Int>>, // cause -> list of player IDs (Int)
    val saved: List<Int>, // player IDs (Int)
)

/**
 * UI-friendly status of the speech session
 */
data class SpeechStatus(
    val order: List<Int>,
    val currentSpeakerId: Int?,
    val endTime: Long,
    val totalTime: Int,
    val isPaused: Boolean = false,
    val interruptVotes: List<Int> = emptyList()
)

/**
 * UI-friendly status of the police election
 */
data class PoliceStatus(
    val state: String,
    val stageEndTime: Long?,
    val allowEnroll: Boolean,
    val allowUnEnroll: Boolean,
    val candidates: List<PoliceCandidateDto>
)

data class PoliceCandidateDto(
    val id: Int,
    val quit: Boolean,
    val voters: List<String>
)
