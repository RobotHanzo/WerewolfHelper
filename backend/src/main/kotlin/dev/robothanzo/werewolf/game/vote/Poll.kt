package dev.robothanzo.werewolf.game.vote

enum class PollKind { POLICE, EXPEL }

/**
 * Stages. Police elections run the full sequence; expel polls jump straight to [VOTING].
 * Transitions are always scheduled (never instant) so players get the posted buffer time.
 */
enum class PollStage { ENROLL, CAMPAIGN, WITHDRAW, VOTING, RESOLVED }

/**
 * Mutable poll state shared by police elections and expel votes. Candidates and votes are seat
 * numbers; [pkRound] marks the single allowed runoff.
 */
data class Poll(
    val kind: PollKind,
    var stage: PollStage,
    val candidates: MutableSet<Int> = sortedSetOf(),
    val withdrawn: MutableSet<Int> = sortedSetOf(),
    /** voter seat → candidate seat. */
    val votes: MutableMap<Int, Int> = linkedMapOf(),
    var stageEndsAt: Long? = null,
    var pkRound: Boolean = false,
    var pkCandidates: Set<Int> = emptySet(),
) {
    /** Candidates still standing (enrolled and not withdrawn). */
    fun activeCandidates(): Set<Int> =
        if (pkRound) pkCandidates else (candidates - withdrawn)
}

/** Outcome of resolving a poll's votes. */
enum class PollOutcome { ELECTED, EXPELLED, PK_NEEDED, NO_RESULT, BADGE_DESTROYED }

data class PollResolution(
    val outcome: PollOutcome,
    val winner: Int? = null,
    val tied: Set<Int> = emptySet(),
    val tally: Map<Int, Double> = emptyMap(),
)
