package dev.robothanzo.werewolf.game.listeners

import dev.robothanzo.werewolf.database.SessionRepository
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionTiming
import dev.robothanzo.werewolf.game.model.RoleEventType
import dev.robothanzo.werewolf.game.roles.PredefinedRoles
import org.springframework.stereotype.Component

/**
 * Generic listener for death trigger actions.
 * When a player dies, this listener checks if any of their roles have DEATH_TRIGGER actions
 * and marks those actions as available for execution.
 */
@Component
class DeathTriggerListener(
    private val sessionRepository: SessionRepository
) : RoleEventListener {

    override fun onEvent(session: Session, eventType: RoleEventType, metadata: Map<String, Any>) {
        if (eventType != RoleEventType.ON_DEATH) {
            return
        }

        val userId = (metadata["userId"] as? Number)?.toLong() ?: return
        val player = session.players.values.find { it.userId == userId }

        if (player == null || player.roles.isNullOrEmpty()) {
            return
        }

        // Check each role for death trigger actions
        var hasDeathTrigger = false
        for (roleName in player.roles!!) {
            val roleActions = PredefinedRoles.getRoleActions(roleName)
            val deathTriggerActions = roleActions.filter { it.timing == ActionTiming.DEATH_TRIGGER }

            if (deathTriggerActions.isNotEmpty()) {
                // Mark death trigger actions as available for this role
                for (action in deathTriggerActions) {
                    val stateKey = "${action.actionId}Available"
                    session.stateData[stateKey] = userId
                }
                hasDeathTrigger = true
            }
        }

        if (hasDeathTrigger) {
            sessionRepository.save(session)
        }
    }

    override fun getInterestedEvents(): List<RoleEventType> {
        return listOf(RoleEventType.ON_DEATH)
    }
}
