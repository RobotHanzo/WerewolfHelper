package dev.robothanzo.werewolf.listeners

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.commands.Player
import dev.robothanzo.werewolf.commands.Poll
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.model.Candidate
import dev.robothanzo.werewolf.utils.CmdUtils
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import java.util.*

class ButtonListener : ListenerAdapter() {
    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val customId = event.button.customId ?: return
        val id = customId.split(":".toRegex()).toTypedArray()

        when (id[0]) {
            "confirmNewPolice" -> {
                Player.confirmNewPolice(event)
                return
            }

            "destroyPolice" -> {
                Player.destroyPolice(event)
                return
            }

            "terminateTimer" -> {
                event.deferReply(true).queue()
                if (CmdUtils.isAdmin(event)) {
                    try {
                        WerewolfApplication.speechService.stopTimer(event.channel.idLong)
                        event.hook.editOriginal(":white_check_mark:").queue()
                    } catch (e: Exception) {
                        event.hook.editOriginal(":x:").queue()
                    }
                } else {
                    event.hook.editOriginal(":x:").queue()
                }
                return
            }
        }

        if (!customId.startsWith("vote")) return

        event.deferReply(true).queue()

        val session = CmdUtils.getSession(event) ?: return
        var player: Session.Player? = null
        var check = false

        for (p in session.fetchAlivePlayers().values) {
            if (p.userId != null && p.userId == event.user.idLong) {
                check = true
                player = p
                break
            }
        }

        if (!check || player == null) {
            event.hook.editOriginal(":x: 只有玩家能投票").queue()
            return
        }
        if (player.idiot && (player.roles == null || player.roles!!.isEmpty())) {
            event.hook.editOriginal(":x: 死掉的白癡不得投票").queue()
            return
        }

        if (customId.startsWith("votePolice")) {
            val guildId = event.guild!!.idLong
            if (WerewolfApplication.policeService.sessions.containsKey(guildId)) {
                val policeSession = WerewolfApplication.policeService.sessions[guildId]!!
                val candidates = policeSession.candidates

                if (candidates.containsKey(player.id)) {
                    event.hook.editOriginal(":x: 你曾經參選過或正在參選，不得投票").queue()
                    return
                }

                val candidateId = customId.replace("votePolice", "").toIntOrNull()
                val electedCandidate = if (candidateId != null) candidates[candidateId] else null

                if (electedCandidate != null) {
                    handleVote(event, candidates, electedCandidate)
                    // Broadcast update immediately
                    WerewolfApplication.gameSessionService.broadcastSessionUpdate(session)
                } else {
                    event.hook.editOriginal(":x: 找不到候選人").queue()
                }
            } else {
                event.hook.editOriginal(":x: 投票已過期").queue()
            }
        }

        if (customId.startsWith("voteExpel")) {
            val guildId = event.guild!!.idLong
            if (Poll.expelCandidates.containsKey(guildId)) {
                val candidates = Poll.expelCandidates[guildId]!!
                val votingCandidate = candidates[player.id]

                if (votingCandidate != null && votingCandidate.expelPK) {
                    event.hook.editOriginal(":x: 你正在和別人進行放逐辯論，不得投票").queue()
                    return
                }

                val candidateId = customId.replace("voteExpel", "").toIntOrNull()
                val electedCandidate = if (candidateId != null) candidates[candidateId] else null

                if (electedCandidate != null) {
                    handleVote(event, candidates, electedCandidate) // Fixed: was passing candidates, electedCandidate
                    // Broadcast update immediately
                    WerewolfApplication.gameSessionService.broadcastSessionUpdate(session)
                }
            } else {
                event.hook.editOriginal(":x: 投票已過期").queue()
            }
        }
    }

    override fun onEntitySelectInteraction(event: EntitySelectInteractionEvent) {
        if ("selectNewPolice" == event.componentId) {
            Player.selectNewPolice(event)
        }
    }

    fun handleVote(
        event: ButtonInteractionEvent,
        candidates: Map<Int, Candidate>,
        electedCandidate: Candidate
    ) {
        var handled = false
        for (candidate in LinkedList(candidates.values)) {
            if (candidate.electors.contains(event.user.idLong)) {
                if (candidate.player.userId == electedCandidate.player.userId) {
                    electedCandidate.electors.remove(event.user.idLong)
                    event.hook.editOriginal(":white_check_mark: 已改為棄票").queue()
                } else {
                    candidates[candidate.player.id]!!.electors.remove(event.user.idLong)
                    electedCandidate.electors.add(event.user.idLong)
                    event.hook.editOriginal(
                        ":white_check_mark: 已將投給玩家${candidate.player.id}的票改成投給玩家${electedCandidate.player.id}"
                    ).queue()
                }
                handled = true
                break
            }
        }
        if (!handled) {
            electedCandidate.electors.add(event.user.idLong)
            event.hook.editOriginal(":white_check_mark: 已投給玩家${electedCandidate.player.id}").queue()
        }
    }
}
