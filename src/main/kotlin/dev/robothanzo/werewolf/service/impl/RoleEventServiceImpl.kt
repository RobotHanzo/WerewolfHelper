package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.listeners.RoleEventListener
import dev.robothanzo.werewolf.game.model.RoleEventType
import dev.robothanzo.werewolf.service.RoleEventService
import org.springframework.stereotype.Service

@Service
class RoleEventServiceImpl(
    private val listeners: List<RoleEventListener>
) : RoleEventService {

    override fun notifyListeners(session: Session, eventType: RoleEventType, metadata: Map<String, Any>) {
        for (listener in listeners) {
            if (listener.getInterestedEvents().contains(eventType)) {
                try {
                    listener.onEvent(session, eventType, metadata)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun registerListener(listener: Any) {
        // Listeners are auto-discovered via Spring @Component
        // This method is mainly for manual registration if needed
    }

    override fun getListenersForEvent(eventType: RoleEventType): List<Any> {
        return listeners.filter { it.getInterestedEvents().contains(eventType) }.toList()
    }
}
