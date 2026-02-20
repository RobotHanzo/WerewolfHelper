package dev.robothanzo.werewolf.listeners

import dev.robothanzo.jda.interactions.annotations.Button
import dev.robothanzo.jda.interactions.annotations.select.StringSelectMenu
import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.*
import dev.robothanzo.werewolf.model.Candidate
import dev.robothanzo.werewolf.utils.CmdUtils
import dev.robothanzo.werewolf.utils.MsgUtils
import dev.robothanzo.werewolf.utils.isAdmin
import dev.robothanzo.werewolf.utils.player
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.ButtonStyle
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
            val interactingPlayer = event.member?.player(false)
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
                val actionId = if (id.size > 1) ActionDefinitionId.fromString(id[1]) ?: return else return

                // Use withLockedSession to ensure we are working with the latest session state
                WerewolfApplication.gameSessionService.withLockedSession(event.guild!!.idLong) { session ->
                    val (player, isJudge) = getVerifiedPlayerAndIsJudge(event, session)
                    if (player == null) return@withLockedSession

                    // Get the action definition
                    val actionExecutor = WerewolfApplication.roleActionExecutor
                    val action = actionExecutor.getAction(actionId)

                    // Get the action instance to check if there's already a pending selection, if so, delete
                    val actionInstance = WerewolfApplication.actionUIService.getActionData(session, player.id)

                    // Verify prompt message ID to prevent clicking old prompts from previous nights
                    if (actionInstance?.actionPromptId != event.messageIdLong) {
                        event.hook.sendMessage(":x: 這是舊的按鈕，請使用最新的行動提示").setEphemeral(true).queue()
                        return@withLockedSession
                    }

                    if (player.actionSubmitted && action?.allowMultiplePerPhase != true) {
                        event.hook.sendMessage(":x: 你已提交行動，無法再次選擇").queue()
                        return@withLockedSession
                    }
                    if (actionInstance.actionDefinitionId != null && actionInstance.targets.isEmpty()) {
                        actionInstance.targetPromptId?.let { event.messageChannel.deleteMessageById(it).queue() }
                        actionInstance.targetPromptId = null
                    }

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
                            if (action.targetCount > 1) {
                                appendLine("此行動需要選擇 ${action.targetCount} 名目標。點擊按鈕來選擇/取消選擇，選好後請按確認。")
                            }
                        }

                        if (action.targetCount > 1) {
                            // TargetCount > 1: Toggle mode
                            val targetButtons = eligiblePlayers.map { p ->
                                net.dv8tion.jda.api.components.buttons.Button.secondary(
                                    "selectTarget:${p.id}",
                                    p.nickname
                                )
                            }.toMutableList()

                            // Confirm Button (Initially Disabled)
                            targetButtons.add(
                                net.dv8tion.jda.api.components.buttons.Button.primary(
                                    "confirmTargets",
                                    "確認 (0/${action.targetCount})"
                                )
                                    .withDisabled(true)
                            )

                            val message =
                                event.hook.sendMessage(targetMessage)
                                    .setComponents(
                                        MsgUtils.spreadButtonsAcrossActionRows(
                                            targetButtons
                                        )
                                    )
                                    .complete()
                            actionInstance.targetPromptId = message.idLong
                            return@withLockedSession
                        } else {
                            // TargetCount <= 1: Single selection mode (Standard)
                            val targetButtons = eligiblePlayers.map { p ->
                                net.dv8tion.jda.api.components.buttons.Button.secondary(
                                    "selectTarget:${p.id}",
                                    p.nickname
                                )
                            }.toMutableList()

                            // Add Skip button if optional
                            if (action.isOptional) {
                                targetButtons.add(
                                    net.dv8tion.jda.api.components.buttons.Button.danger(
                                        "selectTarget:$SKIP_TARGET_ID",
                                        "跳過"
                                    )
                                )
                            }
                            val message =
                                event.hook.sendMessage(targetMessage)
                                    .setComponents(
                                        MsgUtils.spreadButtonsAcrossActionRows(
                                            targetButtons
                                        )
                                    )
                                    .complete()
                            actionInstance.targetPromptId = message.idLong
                        }
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

            "confirmTargets" -> {
                event.deferReply(true).queue()
                WerewolfApplication.gameSessionService.withLockedSession(event.guild!!.idLong) { session ->
                    val (player, isJudge) = getVerifiedPlayerAndIsJudge(event, session)
                    if (player == null) return@withLockedSession

                    val actionInstance = WerewolfApplication.actionUIService.getActionData(session, player.id)
                    // Verify prompt message ID
                    if (actionInstance?.targetPromptId != event.messageIdLong) {
                        event.hook.editOriginal(":x: 這是舊的按鈕").queue()
                        return@withLockedSession
                    }

                    val actionId = actionInstance.actionDefinitionId ?: return@withLockedSession
                    val actionDef = WerewolfApplication.roleActionExecutor.getAction(actionId)

                    val targets = ArrayList(actionInstance.targets) // Copy targets
                    val result = session.validateAndSubmitAction(
                        actionId,
                        player.id,
                        targets,
                        if (isJudge) "JUDGE" else "PLAYER",
                        WerewolfApplication.roleRegistry,
                        WerewolfApplication.roleActionExecutor
                    )

                    if (result["success"] == true) {
                        WerewolfApplication.actionUIService.clearPrompt(session, player.id)
                        if (actionDef?.allowMultiplePerPhase != true) {
                            player.actionSubmitted = true
                        }

                        val targetNames = targets.mapNotNull { session.getPlayer(it)?.nickname }.joinToString(", ")
                        event.hook.editOriginal(":white_check_mark: 已確認選擇目標：**$targetNames**").queue()

                        // Disable buttons on the original message and keep the selection colors
                        val updatedButtons = mutableListOf<net.dv8tion.jda.api.components.buttons.Button>()
                        for (component in event.message.components) {
                            if (component is ActionRow) {
                                for (button in component.buttons) {
                                    val buttonId = button.customId ?: ""
                                    if (buttonId.startsWith("selectTarget:")) {
                                        val tid = buttonId.split(":")[1].toIntOrNull()
                                        val isSelected = tid != null && targets.contains(tid)
                                        val style =
                                            if (isSelected) ButtonStyle.SUCCESS else ButtonStyle.SECONDARY
                                        updatedButtons.add(button.withStyle(style).asDisabled())
                                    } else {
                                        updatedButtons.add(button.asDisabled())
                                    }
                                }
                            }
                        }
                        event.message.editMessageComponents(
                            MsgUtils.spreadButtonsAcrossActionRows(
                                updatedButtons
                            )
                        ).queue()
                    } else {
                        event.hook.editOriginal(":x: ${result["error"]}").queue()
                    }
                }
                return
            }

            "skipAction" -> {
                event.deferReply(true).queue()
                WerewolfApplication.gameSessionService.withLockedSession(event.guild!!.idLong) { session ->
                    val (player, _) = getVerifiedPlayerAndIsJudge(event, session)
                    if (player == null) return@withLockedSession

                    val actionInstance = session.stateData.submittedActions.find {
                        it.actor == player.id && it.status != ActionStatus.SUBMITTED
                    }

                    // Verify prompt message ID to prevent clicking old prompts from previous nights
                    if (actionInstance?.actionPromptId != event.messageIdLong) {
                        event.hook.editOriginal(":x: 這是舊的按鈕，請使用最新的行動提示").queue()
                        return@withLockedSession
                    }

                    // For death triggers, we need to finalize the actor's death status
                    if (!player.alive) {
                        val roleRegistry = WerewolfApplication.roleRegistry
                        player.roles.forEach { roleName ->
                            val roleObj = session.hydratedRoles[roleName] ?: roleRegistry.getRole(roleName)
                            roleObj?.getActions()?.filter { it.timing == ActionTiming.DEATH_TRIGGER }
                                ?.forEach { action ->
                                    session.stateData.playerOwnedActions[player.id]?.remove(action.actionId.toString())
                                }
                        }
                        player.discordDeath()
                    }

                    player.actionSubmitted = true
                    actionInstance.status = ActionStatus.SKIPPED
                    actionInstance.targets.clear()
                    actionInstance.targets.add(SKIP_TARGET_ID)
                    // Clear the action prompt to cancel reminder
                    WerewolfApplication.actionUIService.clearPrompt(session, player.id)

                    // Trigger completion check for potential early step advancement
                    if (session.currentState == "DEATH_ANNOUNCEMENT") {
                        (WerewolfApplication.gameStateService.getCurrentStep(session) as? dev.robothanzo.werewolf.game.steps.DeathAnnouncementStep)?.checkAdvance(
                            session,
                            WerewolfApplication.gameStateService
                        )
                    }

                    event.hook.editOriginal(":white_check_mark: 已跳過").queue()
                }
                return
            }

            "selectTarget" -> {
                // For toggle buttons, we use deferEdit to update the message in place
                event.deferEdit().queue()

                if (id.size < 2) return

                val targetIdStr = id[1]
                val guildId = event.guild!!.idLong

                WerewolfApplication.gameSessionService.withLockedSession(guildId) { session ->
                    val (player, _) = getVerifiedPlayerAndIsJudge(event, session)
                    if (player == null) return@withLockedSession

                    val actionInstance = WerewolfApplication.actionUIService.getActionData(session, player.id)

                    // Verify prompt message ID
                    if (actionInstance?.targetPromptId != event.messageIdLong) {
                        return@withLockedSession
                    }

                    val actionId = actionInstance.actionDefinitionId ?: return@withLockedSession
                    val actionDef =
                        WerewolfApplication.roleActionExecutor.getAction(actionId) ?: return@withLockedSession

                    val targetId = targetIdStr.toIntOrNull() ?: return@withLockedSession

                    if (actionDef.targetCount > 1) {
                        // Multi-target Toggle Logic
                        if (actionInstance.targets.contains(targetId)) {
                            actionInstance.targets.remove(targetId)
                        } else {
                            if (actionInstance.targets.size < actionDef.targetCount) {
                                actionInstance.targets.add(targetId)
                            } else {
                                // Optional: Replace oldest? Or just ignore/error?
                                // User: "reclicking it unselects it". Doesn't specify overflow behavior.
                                // Let's just ignore if full to prevent confusion, or error.
                                // To be user friendly, let's do nothing if max reached (user must unselect first).
                            }
                        }

                        // Re-render buttons
                        val allAlivePlayerIds = session.alivePlayers().values.map { it.id }
                        val eligibleTargetIds = actionDef.eligibleTargets(session, player.id, allAlivePlayerIds)
                        val eligiblePlayers = session.players.values.filter { it.id in eligibleTargetIds }

                        val newButtons = eligiblePlayers.map { p ->
                            val isSelected = actionInstance.targets.contains(p.id)
                            if (isSelected) {
                                net.dv8tion.jda.api.components.buttons.Button.success(
                                    "selectTarget:${p.id}",
                                    p.nickname
                                )
                            } else {
                                net.dv8tion.jda.api.components.buttons.Button.secondary(
                                    "selectTarget:${p.id}",
                                    p.nickname
                                )
                            }
                        }.toMutableList()

                        val isReady = actionInstance.targets.size == actionDef.targetCount
                        newButtons.add(
                            net.dv8tion.jda.api.components.buttons.Button.primary(
                                "confirmTargets",
                                "確認 (${actionInstance.targets.size}/${actionDef.targetCount})"
                            )
                                .withDisabled(!isReady)
                        )

                        event.hook.editOriginalComponents(
                            MsgUtils.spreadButtonsAcrossActionRows(newButtons)
                        ).queue()

                    } else {
                        // Single Target Logic (Immediate Submit)
                        val isSkip = targetIdStr == SKIP_TARGET_ID.toString()
                        val targetsToSubmit = if (isSkip) arrayListOf(SKIP_TARGET_ID) else arrayListOf(targetId)
                        val targetStatus = if (isSkip) "跳過" else session.getPlayer(targetId)?.nickname ?: "Unknown"

                        // If not skip, verify target exists
                        if (!isSkip && session.getPlayer(targetId) == null) return@withLockedSession

                        val result = session.validateAndSubmitAction(
                            actionId,
                            player.id,
                            targetsToSubmit,
                            "PLAYER",
                            WerewolfApplication.roleRegistry,
                            WerewolfApplication.roleActionExecutor
                        )

                        if (result["success"] == true) {
                            WerewolfApplication.actionUIService.clearPrompt(session, player.id)
                            if (actionDef.allowMultiplePerPhase != true) player.actionSubmitted = true

                            // Update buttons: Selected green, others secondary, all disabled
                            val updatedButtons = mutableListOf<net.dv8tion.jda.api.components.buttons.Button>()
                            for (component in event.message.components) {
                                if (component is ActionRow) {
                                    for (button in component.buttons) {
                                        val buttonId = button.customId ?: ""
                                        if (buttonId.startsWith("selectTarget:")) {
                                            val tid = buttonId.split(":")[1].toIntOrNull()
                                            val isSelected = tid == (if (isSkip) SKIP_TARGET_ID else targetId)
                                            val style =
                                                if (isSelected) ButtonStyle.SUCCESS else ButtonStyle.SECONDARY
                                            updatedButtons.add(button.withStyle(style).asDisabled())
                                        } else {
                                            updatedButtons.add(button.asDisabled())
                                        }
                                    }
                                }
                            }
                            event.hook.editOriginalComponents(
                                MsgUtils.spreadButtonsAcrossActionRows(
                                    updatedButtons
                                )
                            ).queue()
                            val msg =
                                if (isSkip) ":white_check_mark: 已跳過行動" else ":white_check_mark: 已選擇 **$targetStatus**"
                            event.hook.sendMessage(msg).queue()
                        } else {
                            event.hook.sendMessage(":x: ${result["error"]}").setEphemeral(true).queue()
                        }
                    }
                }
                return
            }

            "end_game_confirm" -> {
                event.deferReply(true).queue()
                WerewolfApplication.gameSessionService.withLockedSession(event.guild!!.idLong) { session ->
                    if (!CmdUtils.isAdmin(event)) {
                        event.hook.editOriginal(":x: 只有法官可以執行此操作").queue()
                        return@withLockedSession
                    }

                    val result = WerewolfApplication.gameStateService.handleInput(
                        session,
                        mapOf("action" to "end_game_confirm")
                    )

                    if (result["success"] == true) {
                        event.hook.editOriginal(":white_check_mark: 指令已確認").queue()
                        WerewolfApplication.gameSessionService.broadcastSessionUpdate(session)
                    } else {
                        event.hook.editOriginal(":x: 操作失敗").queue()
                    }
                }
                return
            }

            "continue_game" -> {
                event.deferReply(true).queue()
                WerewolfApplication.gameSessionService.withLockedSession(event.guild!!.idLong) { session ->
                    if (!CmdUtils.isAdmin(event)) {
                        event.hook.editOriginal(":x: 只有法官可以執行此操作").queue()
                        return@withLockedSession
                    }

                    val result = WerewolfApplication.gameStateService.handleInput(
                        session,
                        mapOf("action" to "continue_game")
                    )

                    if (result["success"] == true) {
                        event.hook.editOriginal(":white_check_mark: 遊戲繼續").queue()
                        WerewolfApplication.gameSessionService.broadcastSessionUpdate(session)
                    } else {
                        event.hook.editOriginal(":x: 操作失敗").queue()
                    }
                }
                return
            }
        }

        if (!customId.startsWith("vote")) return

        event.deferReply(true).queue()

        val guildId = event.guild!!.idLong
        WerewolfApplication.gameSessionService.withLockedSession(guildId)
        { session ->
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
            if (player.idiot && player.roles.containsAll(player.deadRoles)) {
                event.hook.editOriginal(":x: 死掉的白癡不得投票").queue()
                return@withLockedSession
            }

            if (customId.startsWith("votePolice")) {
                if (WerewolfApplication.policeService.sessions.containsKey(guildId)) {
                    val policeSession = WerewolfApplication.policeService.sessions[guildId]!!
                    val candidates = policeSession.candidates

                    // Verify prompt message ID to prevent clicking old prompts
                    if (policeSession.message?.idLong != event.messageIdLong) {
                        event.hook.editOriginal(":x: 這是舊的投票按鈕，請使用最新的提示").queue()
                        return@withLockedSession
                    }

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
                    // Verify prompt message ID to prevent clicking old prompts
                    if (poll.message?.idLong != event.messageIdLong) {
                        event.hook.editOriginal(":x: 這是舊的投票按鈕，請使用最新的提示").queue()
                        return@withLockedSession
                    }

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
