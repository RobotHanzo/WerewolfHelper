package dev.robothanzo.werewolf.game.listeners

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.RoleEventType

interface RoleEventListener {
    /**
     * Handle a role event
     */
    fun onEvent(session: Session, eventType: RoleEventType, metadata: Map<String, Any>)

    /**
     * Get the event types this listener is interested in
     */
    fun getInterestedEvents(): List<RoleEventType>
}
