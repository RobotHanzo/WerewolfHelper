package dev.robothanzo.werewolf.dto

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.controller.dto.SessionResponse
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.service.GameStateService
import net.dv8tion.jda.api.JDA
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class JacksonSerializationTest {

    @BeforeEach
    fun setUp() {
        WerewolfApplication.gameStateService = mock<GameStateService>()
        WerewolfApplication.jda = mock<JDA>()
    }

    @Test
    fun testSessionSerialization() {
        // Create a dummy session
        val session = Session(
            guildId = 123456789L
        )

        // Wrap in response
        val response = SessionResponse(session)

        // Serialize
        val mapper = jacksonObjectMapper()
        val json = mapper.writeValueAsString(response)

        // Verify output
        assertNotNull(json)
        println(json)

        // Ensure no JDA fields are present
        assert(!json.contains("\"guild\""))
        assert(!json.contains("\"courtTextChannel\""))
    }
}
