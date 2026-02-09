package dev.robothanzo.werewolf.websocket

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.security.GlobalWebSocketHandler

/**
 * The Wrapper that creates the {"type": "...", "data": {...}} structure.
 */
data class WebSocketEnvelope(
    @param:JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
        property = "type"
    )
    @param:JsonSubTypes(
        JsonSubTypes.Type(value = WebSocketEventData.SessionUpdate::class, name = "UPDATE"),
        JsonSubTypes.Type(value = WebSocketEventData.ProgressUpdate::class, name = "PROGRESS"),
        JsonSubTypes.Type(value = WebSocketEventData.PlayerUpdate::class, name = "PLAYER_UPDATE")
    )
    val data: WebSocketEventData
) {
    fun toJson(): String = GlobalWebSocketHandler.mapper.writeValueAsString(this)
}

/**
 * Base sealed class for the inner data.
 * Note: We removed the type info from here so it doesn't duplicate inside the "data" block.
 */
sealed class WebSocketEventData {
    data class SessionUpdate(
        val guildId: String,
        val session: Session
    ) : WebSocketEventData()

    data class ProgressUpdate(
        val guildId: String,
        val message: String? = null,
        val percent: Int? = null
    ) : WebSocketEventData()

    data class PlayerUpdate(
        val userId: String,
        val name: String,
        val avatar: String
    ) : WebSocketEventData()
}
