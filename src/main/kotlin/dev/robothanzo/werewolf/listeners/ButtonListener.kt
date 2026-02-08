package dev.robothanzo.werewolf.listeners

import dev.robothanzo.jda.interactions.annotations.Button
import dev.robothanzo.jda.interactions.annotations.select.StringSelectMenu
import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionStatus
import dev.robothanzo.werewolf.game.model.SKIP_TARGET_ID
import dev.robothanzo.werewolf.game.model.updateActionStatus
import dev.robothanzo.werewolf.game.model.validateAndSubmitAction
import dev.robothanzo.werewolf.model.Candidate
import dev.robothanzo.werewolf.utils.CmdUtils
import dev.robothanzo.werewolf.utils.isAdmin
import dev.robothanzo.werewolf.utils.player
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.springframework.stereotype.Component
import java.util.*
import dev.robothanzo.werewolf.database.documents.Player as DatabasePlayer

@Component
class ButtonListener : ListenerAdapter() {
    companion object {
        fun getVerifiedPlayerAndIsJudge(
            event: ButtonInteractionEvent,
            session: Session
        ): Pair<DatabasePlayer?, Boolean> {
            val isJudge = event.member?.isAdmin() == true
            val player = session.getPlayerByChannel(event.channel.idLong)
            if (player == null) {
                event.hook.editOriginal(":x: 找不到玩家").queue()
                return null to isJudge
            }
            val interactingPlayer = event.member?.player()
            if (player.id != interactingPlayer?.id && !isJudge) {
                event.hook.editOriginal(":x: 這不是你的按鈕").queue()
                return null to false
            }
            return player to isJudge
        }
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val customId = event.button.customId ?: return
        val id = customId.split(":".toRegex()).toTypedArray()

        when (id[0]) {
            "confirmNewPolice" -> {
                WerewolfApplication.policeService.confirmNewPolice(event)
                return
            }

            "destroyPolice" -> {
                WerewolfApplication.policeService.destroyPolice(event)
                return
            }

            "selectAction" -> {
                event.deferReply().queue()
                val actionId = if (id.size > 1) id[1] else return

                // Use withLockedSession to ensure we are working with the latest session state
                WerewolfApplication.gameSessionService.withLockedSession(event.guild!!.idLong) { session ->
                    val (player, isJudge) = getVerifiedPlayerAndIsJudge(event, session)
                    if (player == null) return@withLockedSession
                    if (player.actionSubmitted) {
                        event.hook.sendMessage(":x: 你已提交行動，無法再次選擇").queue()
                        return@withLockedSession
                    }
                    // Get the action instance to check if there's already a pending selection, if so, delete
                    val actionInstance = WerewolfApplication.actionUIService.getActionData(session, player.id)
                    if (actionInstance != null && actionInstance.actionDefinitionId.isNotEmpty() && actionInstance.targets.isEmpty()) {
                        actionInstance.targetPromptId?.let { event.messageChannel.deleteMessageById(it).queue() }
                    }

                    // Get the action definition
                    val actionExecutor = WerewolfApplication.roleActionExecutor
                    val action = actionExecutor.getActionById(actionId)

                    if (action != null && action.targetCount > 0) {
                        // Update selection in a persistent state
                        WerewolfApplication.actionUIService.updateActionSelection(
                            event.guild!!.idLong,
                            player.id,
                            actionId,
                            session
                        )

                        // Filter eligible targets using the action's logic
                        val allAlivePlayerIds = session.alivePlayers().values.map { it.id }
                        val pid = player.id
                        val eligibleTargetIds = action.eligibleTargets(session, pid, allAlivePlayerIds)

                        val eligiblePlayers = session.players.values.filter {
                            it.id in eligibleTargetIds
                        }

                        if (eligiblePlayers.isEmpty()) {
                            event.hook.sendMessage(":x: 沒有可選的目標").queue()
                            WerewolfApplication.actionUIService.clearPrompt(session, player.id)
                            return@withLockedSession
                        }

                        val targetMessage = buildString {
                            appendLine("🎯 **選擇目標**")
                            appendLine()
                            appendLine("請選擇 **${action.actionName}** 目標：")
                        }

                        val targetButtons = eligiblePlayers.map { p ->
                            net.dv8tion.jda.api.components.buttons.Button.secondary(
                                "selectTarget:${p.id}",
                                p.nickname
                            )
                        }.toMutableList()

                        // Add Skip button
                        targetButtons.add(
                            net.dv8tion.jda.api.components.buttons.Button.danger(
                                "selectTarget:$SKIP_TARGET_ID",
                                "跳過"
                            )
                        )
                        val message =
                            event.hook.sendMessage(targetMessage)
                                .setComponents(
                                    dev.robothanzo.werewolf.utils.MsgUtils.spreadButtonsAcrossActionRows(
                                        targetButtons
                                    )
                                )
                                .complete()

                        WerewolfApplication.actionUIService.updateTargetPromptId(session, player.id, message.idLong)

                        // Update status manually using extension
                        session.updateActionStatus(
                            player.id,
                            ActionStatus.ACTING,
                            actionId
                        )
                    } else {
                        // No targets required - submit immediately
                        WerewolfApplication.actionUIService.clearPrompt(session, player.id)

                        session.validateAndSubmitAction(
                            actionId,
                            player.id,
                            arrayListOf(),
                            if (isJudge) "JUDGE" else "PLAYER",
                            WerewolfApplication.roleRegistry,
                            actionExecutor
                        )
                        event.hook.editOriginal(":white_check_mark: 已執行行動").queue()
                    }
                }
                return
            }

            "skipAction" -> {
                event.deferReply(true).queue()
                WerewolfApplication.gameSessionService.withLockedSession(event.guild!!.idLong) { session ->
                    val (player, _) = getVerifiedPlayerAndIsJudge(event, session)
                    if (player == null) return@withLockedSession
                    // Mark action as submitted but without actual action
                    player.actionSubmitted = true
                    // Update UI status to SKIPPED so the night can resolve early
                    // Update UI status to SKIPPED so the night can resolve early
                    session.updateActionStatus(
                        player.id,
                        ActionStatus.SKIPPED,
                        targetPlayerIds = listOf(SKIP_TARGET_ID)
                    )
                    // Clear the action prompt to cancel reminder
                    WerewolfApplication.actionUIService.clearPrompt(session, player.id)
                    event.hook.editOriginal(":white_check_mark: 已跳過").queue()
                }
                return
            }

            "selectTarget" -> {
                event.deferReply(true).queue()
                if (id.size < 2) {
                    event.hook.editOriginal(":x: 無效的目標選擇").queue()
                    return
                }
                val targetId = id[1]
                val guildId = event.guild!!.idLong

                WerewolfApplication.gameSessionService.withLockedSession(guildId) { session ->
                    val (player, isJudge) = getVerifiedPlayerAndIsJudge(event, session)
                    if (player == null) return@withLockedSession

                    val actionInstance = WerewolfApplication.actionUIService.getActionData(
                        session,
                        player.id
                    )
                    val actionId = actionInstance?.actionDefinitionId?.takeIf { it.isNotEmpty() }
                    if (actionId == null) {
                        event.hook.editOriginal(":x: 沒有待選的行動").queue()
                        return@withLockedSession
                    }

                    if (targetId == SKIP_TARGET_ID.toString()) {
                        // Handle Skip
                        // Handle Skip
                        session.validateAndSubmitAction(
                            actionId,
                            player.id,
                            arrayListOf(SKIP_TARGET_ID),
                            if (isJudge) "JUDGE" else "PLAYER",
                            WerewolfApplication.roleRegistry,
                            WerewolfApplication.roleActionExecutor
                        )

                        WerewolfApplication.actionUIService.clearPrompt(session, player.id)
                        player.actionSubmitted = true
                        event.hook.editOriginal(":white_check_mark: 已選擇 **跳過** 本回合行動").queue()
                        return@withLockedSession
                    }

                    val target = session.getPlayer(targetId)

                    if (target == null) {
                        event.hook.editOriginal(":x: 找不到目標").queue()
                        return@withLockedSession
                    }
                    if (player.actionSubmitted) {
                        event.hook.editOriginal(":x: 你已提交行動，無法再次選擇").queue()
                        return@withLockedSession
                    }

                    // Submit action with target
                    WerewolfApplication.actionUIService.submitTargetSelection(
                        guildId,
                        player.id,
                        target.id,
                        session
                    )

                    val result = session.validateAndSubmitAction(
                        actionId,
                        player.id,
                        arrayListOf(target.id),
                        if (isJudge) "JUDGE" else "PLAYER",
                        WerewolfApplication.roleRegistry,
                        WerewolfApplication.roleActionExecutor
                    )

                    if (result["success"] == true) {
                        // Clear the prompt after submission
                        WerewolfApplication.actionUIService.clearPrompt(
                            session,
                            player.id
                        )
                        player.actionSubmitted = true
                        event.hook.editOriginal(":white_check_mark: 已選擇 **${target.nickname}** 為目標").queue()
                    } else {
                        event.hook.editOriginal(":x: ${result["error"]}").queue()
                    }
                }
                return
            }
        }

        if (!customId.startsWith("vote")) return

        event.deferReply(true).queue()

        val guildId = event.guild!!.idLong
        WerewolfApplication.gameSessionService.withLockedSession(guildId) { session ->
            var player: DatabasePlayer? = null
            var check = false

            for (p in session.alivePlayers().values) {
                if (p.user?.idLong != null && p.user?.idLong == event.user.idLong) {
                    check = true
                    player = p
                    break
                }
            }

            if (!check || player == null) {
                event.hook.editOriginal(":x: 只有玩家能投票").queue()
                return@withLockedSession
            }
            if (player.idiot && (player.roles == null || player.roles!!.isEmpty())) {
                event.hook.editOriginal(":x: 死掉的白癡不得投票").queue()
                return@withLockedSession
            }

            if (customId.startsWith("votePolice")) {
                if (WerewolfApplication.policeService.sessions.containsKey(guildId)) {
                    val policeSession = WerewolfApplication.policeService.sessions[guildId]!!
                    val candidates = policeSession.candidates

                    if (!policeSession.isEligibleVoter(player)) {
                        event.hook.editOriginal(":x: 你曾經參選過或正在參選，不得投票").queue()
                        return@withLockedSession
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
                val poll = WerewolfApplication.expelService.getPoll(guildId)
                if (poll != null) {
                    // Check voter eligibility
                    if (!poll.isEligibleVoter(player)) {
                        event.hook.editOriginal(":x: 你不得投票").queue()
                        return@withLockedSession
                    }

                    val candidates = WerewolfApplication.expelService.getPollCandidates(guildId)!!
                    val votingCandidate = candidates[player.id]

                    if (votingCandidate != null && votingCandidate.expelPK) {
                        event.hook.editOriginal(":x: 你正在和別人進行放逐辯論，不得投票").queue()
                        return@withLockedSession
                    }

                    val candidateId = customId.replace("voteExpel", "").toIntOrNull()
                    val electedCandidate = if (candidateId != null) candidates[candidateId] else null

                    if (electedCandidate != null) {
                        handleVote(
                            event,
                            candidates,
                            electedCandidate
                        ) // Fixed: was passing candidates, electedCandidate
                        // Broadcast update immediately
                        WerewolfApplication.gameSessionService.broadcastSessionUpdate(session)
                    }
                } else {
                    event.hook.editOriginal(":x: 投票已過期").queue()
                }
            }
        }
    }

    override fun onEntitySelectInteraction(event: EntitySelectInteractionEvent) {
        if ("selectNewPolice" == event.componentId) {
            WerewolfApplication.policeService.selectNewPolice(event)
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
                if (candidate.player.user?.idLong == electedCandidate.player.user?.idLong) {
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

    @Button
    fun enrollPolice(event: ButtonInteractionEvent) {
        WerewolfApplication.policeService.enrollPolice(event)
    }

    @Button
    fun changeRoleOrder(event: ButtonInteractionEvent) {
        if (event.guild == null) return
        event.deferReply().queue()
        val session = CmdUtils.getSession(event) ?: return
        val player = session.getPlayer(event.user.idLong)
        if (player == null) {
            event.hook.editOriginal(":x: 你不是玩家").queue()
            return
        }
        try {
            WerewolfApplication.playerService.switchRoleOrder(player)
            event.hook.editOriginal(":white_check_mark: 交換成功").queue()
        } catch (e: Exception) {
            event.hook.editOriginal(":x: " + e.message).queue()
        }
    }

    @StringSelectMenu
    fun selectOrder(event: StringSelectInteractionEvent) {
        WerewolfApplication.speechService.handleOrderSelection(event)
    }

    @Button
    fun confirmOrder(event: ButtonInteractionEvent) {
        WerewolfApplication.speechService.confirmOrder(event)
    }

    @Button
    fun skipSpeech(event: ButtonInteractionEvent) {
        WerewolfApplication.speechService.skipSpeech(event)
    }

    @Button
    fun interruptSpeech(event: ButtonInteractionEvent) {
        WerewolfApplication.speechService.interruptSpeech(event)
    }

    @Button
    fun terminateTimer(event: ButtonInteractionEvent) {
        event.deferReply(true).queue()
        if (CmdUtils.isAdmin(event)) {
            try {
                WerewolfApplication.speechService.stopTimer(event.channel.idLong)
                event.hook.editOriginal(":white_check_mark:").queue()
            } catch (_: Exception) {
                event.hook.editOriginal(":x:").queue()
            }
        } else {
            event.hook.editOriginal(":x:").queue()
        }
    }
}