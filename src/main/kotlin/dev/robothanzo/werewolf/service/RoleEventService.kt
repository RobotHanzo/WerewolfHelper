package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.RoleEventType

interface RoleEventService {

    /**
     * Notify all listeners about an event
     */
    fun notifyListeners(session: Session, eventType: RoleEventType, metadata: Map<String, Any>)

    /**
     * Register a listener
     */
    fun registerListener(listener: Any)

    /**
     * Get all registered listeners for an event type
     */
    fun getListenersForEvent(eventType: RoleEventType): List<Any>
}
