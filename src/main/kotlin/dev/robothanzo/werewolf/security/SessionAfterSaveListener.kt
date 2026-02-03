package dev.robothanzo.werewolf.security

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Session
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent
import org.springframework.stereotype.Component

@Component
class SessionAfterSaveListener : AbstractMongoEventListener<Session>() {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun onAfterSave(event: AfterSaveEvent<Session>) {
        super.onAfterSave(event)
        val session = event.source
        try {
            WerewolfApplication.gameSessionService.broadcastSessionUpdate(session)
        } catch (e: Exception) {
            log.error("Failed to broadcast session update after save", e)
        }
    }
}
