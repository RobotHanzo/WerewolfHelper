package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.audio.Audio
import dev.robothanzo.werewolf.audio.Audio.play
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.GameStateService
import dev.robothanzo.werewolf.service.SpeechService
import kotlinx.coroutines.*
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component


@Component
class DayStep(
    private val speechService: SpeechService,
    @param:Lazy
    private val gameSessionService: GameSessionService
) : GameStep() {
    override val id = "DAY_STEP"
    override val name = "天亮了"

    override fun getEndTime(session: Session): Long {
        val now = System.currentTimeMillis()
        val effectiveNow = if (session.stateData.paused) (session.stateData.pauseStartTime ?: now) else now
        return effectiveNow + (maxOf(0L, session.stateData.stepStartTime + 10_000L - effectiveNow))
    }

    override fun onStart(session: Session, service: GameStateService) {
        super.onStart(session, service)
        // Unmute everyone? Or keep muted for police election?
        // Usually day start = announcement, then specific phases handle muting
        speechService.setAllMute(session.guildId, true) // Ensure silence for announcement
        session.courtTextChannel?.sendMessage("# **:sunny: 天亮了**")?.queue()
        session.courtVoiceChannel?.play(Audio.Resource.MORNING) {
            // Audio is ~7s, total delay ~10s. We use a coroutine to wait while not paused.
            CoroutineScope(Dispatchers.Default).launch {
                var waitedMs = 7000L // Assume 7s for audio
                while (waitedMs < 10000L) {
                    delay(500)
                    val s = gameSessionService.getSession(session.guildId).orElse(null) ?: break
                    if (s.currentState != id) break
                    if (!s.stateData.paused) {
                        waitedMs += 500
                    }
                }

                gameSessionService.withLockedSession(session.guildId) { lockedSession ->
                    if (lockedSession.currentState == id && !lockedSession.stateData.paused) {
                        service.nextStep(lockedSession)
                    }
                }
            }
        }
    }

    override fun onEnd(session: Session, service: GameStateService) {

    }

    override fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any> {
        return mapOf("success" to true)
    }
}
