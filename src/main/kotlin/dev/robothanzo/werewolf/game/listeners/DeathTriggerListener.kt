package dev.robothanzo.werewolf.game.listeners

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionTiming
import dev.robothanzo.werewolf.game.model.RoleEventType
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import org.springframework.stereotype.Component
import dev.robothanzo.werewolf.game.model.Role as GameRole

/**
 * Generic listener for death trigger actions.
 * When a player dies, this listener checks if any of their roles have DEATH_TRIGGER actions
 * and marks those actions as available for execution.
 */
@Component
class DeathTriggerListener(
    private val roleRegistry: RoleRegistry
) : RoleEventListener {

    override fun onEvent(session: Session, eventType: RoleEventType, metadata: Map<String, Any>) {
        if (eventType != RoleEventType.ON_DEATH) {
            return
        }

        val userId = (metadata["userId"] as? Number)?.toLong() ?: return
        val player = session.getPlayer(userId)

        if (player == null || player.roles.isNullOrEmpty()) {
            return
        }

        // Check each role for death trigger actions
        var hasDeathTrigger = false
        for (roleName in player.roles!!) {
            val roleObj: GameRole? = session.hydratedRoles[roleName] ?: roleRegistry.getRole(roleName)
            if (roleObj != null) {
                val deathTriggerActions = roleObj.getActions().filter { it.timing == ActionTiming.DEATH_TRIGGER }

                if (deathTriggerActions.isNotEmpty()) {
                    // Mark death trigger actions as available for this role
                    for (action in deathTriggerActions) {
                        val stateKey = "${action.actionId}Available"
                        session.stateData.roleFlags[stateKey] = userId
                    }
                    hasDeathTrigger = true
                }
            }
        }

        if (hasDeathTrigger) {
            dev.robothanzo.werewolf.WerewolfApplication.gameSessionService.saveSession(session)
        }
    }

    override fun getInterestedEvents(): List<RoleEventType> {
        return listOf(RoleEventType.ON_DEATH)
    }
}
