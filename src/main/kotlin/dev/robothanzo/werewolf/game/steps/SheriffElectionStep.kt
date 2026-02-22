package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.service.GameStateService
import dev.robothanzo.werewolf.service.PoliceService
import dev.robothanzo.werewolf.service.SpeechService
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
class SheriffElectionStep(
    @param:Lazy
    private val policeService: PoliceService,
    @param:Lazy
    private val speechService: SpeechService
) : GameStep() {
    override val id = "SHERIFF_ELECTION"
    override val name = "警長參選"

    override fun onStart(session: Session, service: GameStateService) {
        super.onStart(session, service)
        WerewolfApplication.jda.getGuildById(session.guildId) ?: return
        val channel = session.courtTextChannel ?: return
        policeService.startEnrollment(session, channel, null) {
            service.nextStep(session)
        }
    }

    override fun getEndTime(session: Session): Long {
        val now = System.currentTimeMillis()
        val effectiveNow = if (session.stateData.paused) (session.stateData.pauseStartTime ?: now) else now
        val policeSession = policeService.sessions[session.guildId] ?: return effectiveNow

        return when (policeSession.state) {
            dev.robothanzo.werewolf.model.PoliceSession.State.ENROLLMENT -> {
                val base = policeSession.stageEndTime
                val candidates = policeSession.candidates.size
                if (candidates >= 2) {
                    // Speech (c * 180) + Unenroll (20) + Vote (30)
                    base + (candidates * 180 * 1000L) + 50000L
                } else {
                    base
                }
            }

            dev.robothanzo.werewolf.model.PoliceSession.State.SPEECH -> {
                val speechSession = speechService.getSpeechSession(session.guildId) ?: return effectiveNow
                // Current speaker end time + remaining speakers
                val currentEnd = speechSession.currentSpeechEndTime
                // remaining: speechSession.order
                var remainingTime = 0
                for (p in speechSession.order) {
                    remainingTime += if (p.police) 210 else 180
                }
                currentEnd + (remainingTime * 1000L) + 50000L // + Unenroll (20) + Vote (30)
            }

            dev.robothanzo.werewolf.model.PoliceSession.State.UNENROLLMENT -> {
                // If candidates > 1, add voting. Logic was: if remaining == 1, no voting.
                // But quitEnrollment updates endTime.
                // Here we calculate fresh.
                val activeCandidates = policeSession.candidates.values.count { !it.quit }
                if (activeCandidates > 1) {
                    policeSession.stageEndTime + 30000L
                } else {
                    policeSession.stageEndTime
                }
            }

            dev.robothanzo.werewolf.model.PoliceSession.State.VOTING -> {
                policeSession.stageEndTime
            }

            else -> effectiveNow
        }
    }

    override fun onEnd(session: Session, service: GameStateService) {
        if (policeService.sessions.containsKey(session.guildId)) {
            policeService.interrupt(session.guildId)
        }
    }

    override fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any> {
        val action = input["action"] as? String ?: return mapOf(
            "success" to false,
            "message" to "Missing action"
        )

        return when (action) {
            "force_start_voting" -> {
                policeService.forceStartVoting(session.guildId)
                mapOf("success" to true)
            }

            "interrupt" -> {
                policeService.interrupt(session.guildId)
                mapOf("success" to true)
            }

            "quit" -> {
                val playerId = (input["playerId"] as? Number)?.toInt()
                if (playerId == null) {
                    mapOf("success" to false, "message" to "Missing playerId")
                } else {
                    val result = policeService.quitEnrollment(session.guildId, playerId)
                    mapOf("success" to result)
                }
            }

            "start" -> {
                WerewolfApplication.jda.getGuildById(session.guildId) ?: return mapOf(
                    "success" to false,
                    "message" to "Guild not found"
                )
                val channel = session.courtTextChannel ?: return mapOf(
                    "success" to false,
                    "message" to "Channel not found"
                )
                policeService.startEnrollment(session, channel, null)
                mapOf("success" to true)
            }

            else -> mapOf("success" to false, "message" to "Unknown action")
        }
    }
}
