package dev.robothanzo.werewolf.listeners

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.commands.Player
import dev.robothanzo.werewolf.commands.Poll
import dev.robothanzo.werewolf.model.Candidate
import dev.robothanzo.werewolf.utils.CmdUtils
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import dev.robothanzo.werewolf.database.documents.Player as DatabasePlayer

class ButtonListener : ListenerAdapter() {
    companion object {
        // Track pending action selections waiting for targets (guildId:playerId -> actionId)
        private val pendingActionSelections = ConcurrentHashMap<String, String>()

        fun getPendingAction(guildId: Long, playerId: String): String? {
            return pendingActionSelections["$guildId:$playerId"]
        }

        fun setPendingAction(guildId: Long, playerId: String, actionId: String) {
            pendingActionSelections["$guildId:$playerId"] = actionId
        }

        fun clearPendingAction(guildId: Long, playerId: String) {
            pendingActionSelections.remove("$guildId:$playerId")
        }
    }

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

            "selectAction" -> {
                event.deferReply(true).queue()
                val actionId = if (id.size > 1) id[1] else return
                val session = CmdUtils.getSession(event) ?: return
                var player: DatabasePlayer? = null
                for (p in session.fetchAlivePlayers().values) {
                    if (p.userId != null && p.userId == event.user.idLong) {
                        player = p
                        break
                    }
                }
                if (player == null) {
                    event.hook.editOriginal(":x: 找不到玩家").queue()
                    return
                }
                if (player.actionSubmitted) {
                    event.hook.editOriginal(":x: 你已提交行動，無法再次選擇").queue()
                    return
                }
                if (getPendingAction(event.guild!!.idLong, player.id.toString()) != null) {
                    event.hook.editOriginal(":x: 你已有待選的行動，請先選擇目標").queue()
                    return
                }

                // Get the action definition
                val actionExecutor = WerewolfApplication.roleActionExecutor
                val action = actionExecutor.getActionById(actionId)

                if (action != null && action.targetCount > 0) {
                    // Action requires targets - show target selection menu
                    setPendingAction(event.guild!!.idLong, player.id.toString(), actionId)

                    val alivePlayers = session.fetchAlivePlayers().values.filter { it.id != player.id }
                    if (alivePlayers.isEmpty()) {
                        event.hook.editOriginal(":x: 沒有可選的目標").queue()
                        clearPendingAction(event.guild!!.idLong, player.id.toString())
                        return
                    }

                    val targetMessage = buildString {
                        appendLine("🎯 **選擇目標**")
                        appendLine()
                        appendLine("請選擇 **${action.roleName}** 的 **${action.actionName}** 目標：")
                        for (p in alivePlayers) {
                            appendLine("- ${p.nickname}")
                        }
                    }

                    val targetButtons = alivePlayers.map { p ->
                        net.dv8tion.jda.api.components.buttons.Button.secondary(
                            "selectTarget:${player.id}:${p.id}",
                            "Player ${p.id}"
                        )
                    }

                    player.send(targetMessage, queue = false)?.setComponents(
                        net.dv8tion.jda.api.components.actionrow.ActionRow.of(targetButtons)
                    )?.queue()

                    event.hook.editOriginal(":white_check_mark: 請選擇目標").queue()
                } else {
                    // No targets required - submit immediately
                    WerewolfApplication.actionUIService.clearPrompt(player.id.toString())
                    WerewolfApplication.roleActionService.submitAction(
                        event.guild!!.idLong,
                        actionId,
                        event.user.idLong,
                        emptyList(),
                        "PLAYER"
                    )
                    event.hook.editOriginal(":white_check_mark: 已執行行動").queue()
                }
                return
            }

            "skipAction" -> {
                event.deferReply(true).queue()
                val playerId = if (id.size > 1) id[1] else return
                val session = CmdUtils.getSession(event) ?: return
                // Mark action as submitted but without actual action
                val player = session.players[playerId]
                if (player != null) {
                    player.actionSubmitted = true
                    // Clear the action prompt to cancel reminder
                    WerewolfApplication.actionUIService.clearPrompt(playerId)
                }
                event.hook.editOriginal(":white_check_mark: 已跳過").queue()
                return
            }

            "selectTarget" -> {
                event.deferReply(true).queue()
                if (id.size < 3) {
                    event.hook.editOriginal(":x: 無效的目標選擇").queue()
                    return
                }
                val playerId = id[1]
                val targetId = id[2]
                val session = CmdUtils.getSession(event) ?: return
                val guildId = event.guild!!.idLong

                val player = session.players[playerId]
                val target = session.players[targetId]

                if (player == null || target == null || player.userId != event.user.idLong) {
                    event.hook.editOriginal(":x: 找不到玩家或目標").queue()
                    return
                }
                if (player.actionSubmitted) {
                    event.hook.editOriginal(":x: 你已提交行動，無法再次選擇").queue()
                    return
                }

                val actionId = getPendingAction(guildId, playerId)
                if (actionId == null) {
                    event.hook.editOriginal(":x: 沒有待選的行動").queue()
                    return
                }

                // Clear pending selection
                clearPendingAction(guildId, playerId)
                WerewolfApplication.actionUIService.clearPrompt(playerId)

                // Submit action with target
                WerewolfApplication.roleActionService.submitAction(
                    guildId,
                    actionId,
                    event.user.idLong,
                    listOf(target.userId ?: return),
                    "PLAYER"
                )
                player.actionSubmitted = true
                event.hook.editOriginal(":white_check_mark: 已選擇 **$targetId** 為目標").queue()
                return
            }
        }

        if (!customId.startsWith("vote")) return

        event.deferReply(true).queue()

        val session = CmdUtils.getSession(event) ?: return
        var player: DatabasePlayer? = null
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
