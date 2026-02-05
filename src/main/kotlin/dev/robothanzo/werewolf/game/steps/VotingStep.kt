package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.model.Candidate
import dev.robothanzo.werewolf.service.DiscordService
import dev.robothanzo.werewolf.service.ExpelService
import dev.robothanzo.werewolf.service.GameStateService
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class VotingStep(
    private val discordService: DiscordService,
    private val expelService: ExpelService
) : GameStep {
    override val id = "VOTING_PHASE"
    override val name = "放逐投票"

    override fun onStart(session: Session, service: GameStateService) {
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

    override fun getDurationSeconds(session: Session): Int {
        return 30
    }

    private fun startExpelPoll(session: Session) {
        if (expelService.hasPoll(session.guildId)) {
            return
        }

        val guild = discordService.getGuild(session.guildId) ?: return
        val channel = session.courtTextChannel ?: return

        val candidates = session.fetchAlivePlayers().values
            .associateByTo(ConcurrentHashMap()) { it.id }
            .mapValues { Candidate(player = it.value, expelPK = true) }

        expelService.setPollCandidates(session.guildId, candidates.toMutableMap())
        expelService.startExpelPoll(session, getDurationSeconds(session))
        session.addLog(LogType.EXPEL_POLL_STARTED, "放逐投票開始", null)

        // Delegate UI creation & scheduling to ExpelService
        expelService.startExpelPollUI(session, channel, true, (getDurationSeconds(session) * 1000L))
    }

    private fun endExpelPoll(session: Session, message: String) {
        val hadPoll = expelService.hasPoll(session.guildId)
        expelService.removePoll(session.guildId)
        expelService.endExpelPoll(session.guildId)
        if (!hadPoll) return

        session.courtTextChannel?.sendMessage(message)?.queue()
    }
}
