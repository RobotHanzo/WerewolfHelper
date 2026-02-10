package dev.robothanzo.werewolf.game.listeners

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionTiming
import dev.robothanzo.werewolf.game.model.DeathCause
import dev.robothanzo.werewolf.game.model.RoleEventType
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import org.springframework.stereotype.Component

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
        val deathCause = metadata["deathCause"] as? DeathCause ?: DeathCause.UNKNOWN

        val player = session.getPlayer(userId) ?: return

        if (player.roles.isEmpty()) {
            return
        }

        // Check each role for death trigger actions
        val deathTriggerActions = player.roles.flatMap { roleName ->
            (session.hydratedRoles[roleName] ?: roleRegistry.getRole(roleName))?.getActions()
                ?.filter { it.timing == ActionTiming.DEATH_TRIGGER } ?: emptyList()
        }

        if (deathTriggerActions.isEmpty()) {
            return
        }

        // Call onDeath for each death trigger action
        for (action in deathTriggerActions) {
            action.onDeath(session, player.id, deathCause)
        }

        WerewolfApplication.gameSessionService.saveSession(session)
    }

    override fun getInterestedEvents(): List<RoleEventType> {
        return listOf(RoleEventType.ON_DEATH)
    }
}
