package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.audio.Audio
import dev.robothanzo.werewolf.audio.Audio.play
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.GameStateService
import dev.robothanzo.werewolf.service.SpeechService
import dev.robothanzo.werewolf.utils.CmdUtils
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
        session.courtTextChannel?.sendMessage("# **:sunny: 天亮了**")?.queue()
        session.courtVoiceChannel?.play(Audio.Resource.MORNING) {
            // Audio is ~7s, add 3s delay to make total ~10s
            CmdUtils.schedule({
                gameSessionService.withLockedSession(session.guildId) { lockedSession ->
                    if (lockedSession.currentState == id) {
                        service.nextStep(lockedSession)
                    }
                }
            }, 3000L)
        }
    }

    override fun onEnd(session: Session, service: GameStateService) {

    }

    override fun getDurationSeconds(session: Session): Int {
        return 10
    }

    override fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any> {
        return mapOf("success" to true)
    }
}
