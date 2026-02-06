package dev.robothanzo.werewolf.game.model

enum class ActionSubmissionSource {
    PLAYER,
    JUDGE
}

data class RoleActionInstance(
    val actor: Int,
    val actionDefinitionId: String,
    val targets: List<Int>,
    val submittedBy: ActionSubmissionSource,
    val timestamp: Long = System.currentTimeMillis(),
    var processed: Boolean = false
)
