package dev.robothanzo.werewolf.game.listeners

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.RoleEventType
import dev.robothanzo.werewolf.security.SessionRepository
import org.springframework.stereotype.Component

/**
 * Listener for hunter death - allows hunter to take revenge on another player
 */
@Component
class HunterDeathListener(
    private val sessionRepository: SessionRepository
) : RoleEventListener {

    override fun onEvent(session: Session, eventType: RoleEventType, metadata: Map<String, Any>) {
        if (eventType != RoleEventType.ON_DEATH) {
            return
        }

        val userId = (metadata["userId"] as? Number)?.toLong() ?: return
        val player = session.players.values.find { it.userId == userId }

        // Check if this player has hunter role
        if (player == null || player.roles?.contains("獵人") != true) {
            return
        }

        // Mark that this hunter can now execute revenge
        session.stateData["hunterRevengeAvailable"] = userId
        sessionRepository.save(session)
    }

    override fun getInterestedEvents(): List<RoleEventType> {
        return listOf(RoleEventType.ON_DEATH)
    }
}
