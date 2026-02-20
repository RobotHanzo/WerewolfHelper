package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.model.Candidate
import dev.robothanzo.werewolf.service.ExpelService
import dev.robothanzo.werewolf.service.GameStateService
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap


@Component
class VotingStep(
    private val expelService: ExpelService
) : GameStep() {
    override val id = "VOTING_STEP"
    override val name = "放逐投票"

    override fun onStart(session: Session, service: GameStateService) {
        super.onStart(session, service)
        startExpelPoll(session)
    }

    override fun onEnd(session: Session, service: GameStateService) {
        endExpelPoll(session, "放逐投票已終止（階段切換）")
    }

    override fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any> {
        val action = input["action"] as? String ?: return mapOf(
            "success" to false,
            "message" to "Missing action"
        )

        return when (action) {
            "restart" -> {
                endExpelPoll(session, "放逐投票已終止，重新開始投票")
                startExpelPoll(session)
                mapOf("success" to true)
            }

            "stop" -> {
                endExpelPoll(session, "放逐投票已終止")
                // Mark voting as ended so it transitions properly
                mapOf("success" to true, "votingEnded" to true)
            }

            else -> mapOf("success" to false, "message" to "Unknown action")
        }
    }

    override fun getEndTime(session: Session): Long {
        val guildId = session.guildId
        val speechSession = WerewolfApplication.speechService.getSpeechSession(guildId)
        if (speechSession != null) {
            val currentEnd = if (speechSession.currentSpeechEndTime > System.currentTimeMillis()) {
                speechSession.currentSpeechEndTime
            } else {
                System.currentTimeMillis()
            }

            var remainingMs = 0L
            for (player in speechSession.order) {
                remainingMs += (if (player.police) 210 else 180) * 1000L
            }

            // Estimate based on whether it's PK speech or Last Words
            val speakerId = speechSession.lastSpeaker
            val speaker = if (speakerId != null) session.getPlayer(speakerId) else null
            val isLastWords = speaker != null && !speaker.alive

            return if (isLastWords) {
                // Current speaker is the only one
                currentEnd
            } else {
                // PK flow: current + remaining + vote (30s) + expelled last words (180s)
                currentEnd + remainingMs + 30_000L + 180_000L
            }
        }

        // Otherwise check the expel session for the current voting timer
        val expelSession = expelService.getExpelSession(guildId)
        if (expelSession != null) {
            // Baseline: voting time + 180s last words
            return expelSession.endTime + 180_000L
        }

        // Default fallback: current time + vote time + last words time
        return session.stateData.stepStartTime + 30_000L + 180_000L
    }

    private fun startExpelPoll(session: Session) {
        if (expelService.hasPoll(session.guildId)) {
            return
        }

        WerewolfApplication.jda.getGuildById(session.guildId) ?: return
        val channel = session.courtTextChannel ?: return

        val candidates = session.alivePlayers().values
            .associateByTo(ConcurrentHashMap()) { it.id }
            .mapValues { Candidate(player = it.value, expelPK = true) }

        expelService.setPollCandidates(session.guildId, candidates.toMutableMap())
        expelService.startExpelPoll(session, 30)
        session.addLog(LogType.EXPEL_POLL_STARTED, "放逐投票開始", null)

        // Delegate UI creation & scheduling to ExpelService
        expelService.startExpelPollUI(session, channel, true, 30_000L) {
            WerewolfApplication.gameStateService.nextStep(session)
        }
    }

    private fun endExpelPoll(session: Session, message: String) {
        val hadPoll = expelService.hasPoll(session.guildId)
        expelService.removePoll(session.guildId)
        expelService.endExpelPoll(session.guildId)
        if (!hadPoll) return

        session.courtTextChannel?.sendMessage(message)?.queue()
    }
}
