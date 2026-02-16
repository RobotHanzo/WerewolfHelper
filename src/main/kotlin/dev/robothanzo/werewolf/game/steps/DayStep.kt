package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.audio.Audio
import dev.robothanzo.werewolf.audio.Audio.play
import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.GameStateService
import dev.robothanzo.werewolf.service.SpeechService
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
class DayStep(
    private val speechService: SpeechService,
    @param:Lazy
    private val gameSessionService: GameSessionService
) : GameStep {
    override val id = "DAY_PHASE"
    override val name = "天亮了"

    override fun onStart(session: Session, service: GameStateService) {
        // Unmute everyone? Or keep muted for police election?
        // Usually day start = announcement, then specific phases handle muting
        speechService.setAllMute(session.guildId, true) // Ensure silence for announcement
        session.addLog(LogType.SYSTEM, "天亮了")
        session.courtTextChannel?.sendMessage("# **:sunny: 天亮了**")?.queue()
        session.courtVoiceChannel?.play(Audio.Resource.MORNING) {
            gameSessionService.withLockedSession(session.guildId) { lockedSession ->
                if (lockedSession.currentState == id) {
                    service.nextStep(lockedSession)
                }
            }
        }
    }

    override fun onEnd(session: Session, service: GameStateService) {

    }

    override fun getDurationSeconds(session: Session): Int {
        return 41 // 40s for discussion + 1s buffer before next phase starts
    }

    override fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any> {
        return mapOf("success" to true)
    }
}
