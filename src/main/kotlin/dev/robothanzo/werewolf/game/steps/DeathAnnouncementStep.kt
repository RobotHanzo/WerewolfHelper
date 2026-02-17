package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.game.listeners.RoleEventListener
import dev.robothanzo.werewolf.game.model.ActionStatus
import dev.robothanzo.werewolf.game.model.ActionTiming
import dev.robothanzo.werewolf.game.model.RoleEventType
import dev.robothanzo.werewolf.game.model.resolveNightActions
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import dev.robothanzo.werewolf.game.roles.actions.RoleActionExecutor
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.GameStateService
import dev.robothanzo.werewolf.utils.CmdUtils
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
class DeathAnnouncementStep(
    private val roleRegistry: RoleRegistry,
    private val roleActionExecutor: RoleActionExecutor,
    @param:Lazy
    private val gameSessionService: GameSessionService
) : GameStep, RoleEventListener {
    override val id = "DEATH_ANNOUNCEMENT"
    override val name = "宣布死訊"
    private val log = LoggerFactory.getLogger(DeathAnnouncementStep::class.java)

    override fun getInterestedEvents(): List<RoleEventType> = listOf(RoleEventType.ACTION_PROCESSED)

    override fun onEvent(session: Session, eventType: RoleEventType, metadata: Map<String, Any>) {
        if (eventType == RoleEventType.ACTION_PROCESSED && session.currentState == id) {
            log.info("Action processed event received in DeathAnnouncementStep for guild ${session.guildId}")
            gameSessionService.withLockedSession(session.guildId) { lockedSession ->
                checkAdvance(lockedSession, WerewolfApplication.gameStateService)
            }
        }
    }

    override fun onStart(session: Session, service: GameStateService) {
        val guildId = session.guildId
        val deadPlayerIds = mutableListOf<Int>()

        gameSessionService.withLockedSession(guildId) { lockedSession ->
            // Resolve all pending night actions and get deaths
            val resolutionResult = lockedSession.resolveNightActions(roleActionExecutor, roleRegistry)

            // Process deaths - mark players as dead based on resolution result
            val allDeaths = mutableSetOf<Int>()
            val causes = ArrayList(resolutionResult.deaths.keys)
            causes.shuffle() // Shuffle causes to randomize death order for fairness in announcements

            for (cause in causes) {
                for (userId in resolutionResult.deaths[cause] ?: emptyList()) {
                    val player = lockedSession.getPlayer(userId)
                    if (player != null) {
                        player.markDead(cause)
                        // Capture ID for async event processing
                        deadPlayerIds.add(userId)
                        allDeaths.add(userId)
                    }
                }
            }

            lockedSession.stateData.deadPlayers = allDeaths.toList()

            // Create public death announcement (without revealing causes)
            if (allDeaths.isNotEmpty()) {
                val deathList = allDeaths.joinToString("、") { userId ->
                    val player = lockedSession.getPlayer(userId)
                    val nickname = player?.nickname ?: "玩家 $userId"
                    if (!lockedSession.settings.hiddenRoleOnDeath) {
                        val role = player?.deadRoles?.lastOrNull() ?: "未知角色"
                        "$nickname ($role)"
                    } else {
                        nickname
                    }
                }

                lockedSession.addLog(LogType.SYSTEM, "昨晚 $deathList 死亡")
            } else {
                lockedSession.courtTextChannel?.sendMessage("# **:angel: 昨晚是平安夜**")?.queue()
            }

            // Set initial end time based on triggers (can be updated by triggers later)
            // We set a base time, but the async process will control the flow mostly.
            val duration = getDurationSeconds(lockedSession)
            lockedSession.currentStepEndTime = System.currentTimeMillis() + (duration * 1000L)

            log.info("Death announcement started for guild $guildId. Deaths: $allDeaths")
            gameSessionService.broadcastSessionUpdate(lockedSession)
        }

        // Launch async death processing
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            while (true) {
                // Fetch fresh session to check for new deaths
                val currentSession = gameSessionService.getSession(guildId).orElse(null) ?: break

                // Find players who are dead but haven't been processed yet
                val toProcess = currentSession.players.values
                    .filter { !it.alive && !currentSession.stateData.processedDeathPlayerIds.contains(it.id) }
                    .sortedBy { it.id } // Consistent order

                if (toProcess.isEmpty()) {
                    // Check if there are any pending death triggers (someone still choosing their shot)
                    val hasPendingTriggers = currentSession.stateData.submittedActions.any { instance ->
                        val isUnprocessed =
                            instance.status != ActionStatus.PROCESSED && instance.status != ActionStatus.SKIPPED
                        val action = instance.actionDefinitionId?.let { roleRegistry.getAction(it) }
                        action?.timing == ActionTiming.DEATH_TRIGGER && isUnprocessed
                    }

                    if (!hasPendingTriggers) break // No more deaths and no more pending shots

                    kotlinx.coroutines.delay(1000) // Wait for user to take action
                    continue
                }

                val player = toProcess.first()

                // Only allow last words for first day
                val allowLastWords = currentSession.day == 1
                try {
                    player.runDeathEvents(allowLastWords)

                    // Mark as processed after everything (last words, triggers, etc) is done
                    gameSessionService.withLockedSession(guildId) { lockedSession ->
                        if (!lockedSession.stateData.processedDeathPlayerIds.contains(player.id)) {
                            lockedSession.stateData.processedDeathPlayerIds.add(player.id)

                            // Also ensure they are in the announced deadPlayers list for UI
                            if (!lockedSession.stateData.deadPlayers.contains(player.id)) {
                                val currentDead = lockedSession.stateData.deadPlayers.toMutableList()
                                currentDead.add(player.id)
                                lockedSession.stateData.deadPlayers = currentDead
                            }
                        }
                    }
                } catch (e: Exception) {
                    log.error("Error running death events for player ${player.id}", e)
                    // Still mark as processed to avoid infinite loop on error
                    gameSessionService.withLockedSession(guildId) { it.stateData.processedDeathPlayerIds.add(player.id) }
                }
            }

            // Once all events are done, check if we can advance
            gameSessionService.withLockedSession(guildId) { lockedSession ->
                checkAdvance(lockedSession, service)
            }
        }

        // Schedule tasks for potential advancement (one-shot fallbacks in case async hangs or something)
        // Task 1: Minimum display time (10s as requested)
        CmdUtils.schedule({
            gameSessionService.withLockedSession(guildId) { currentSession ->
                checkAdvance(currentSession, service)
            }
        }, 10000)

        // Task 2: Maximum duration (backup timeout)
        val duration = getDurationSeconds(session)
        if (duration > 10) {
            CmdUtils.schedule({
                gameSessionService.withLockedSession(guildId) { currentSession ->
                    checkAdvance(currentSession, service)
                }
            }, duration * 1000L + 5000) // Small buffer
        }
    }

    fun checkAdvance(session: Session, service: GameStateService) {
        if (session.currentState != id) return

        val now = System.currentTimeMillis()
        val minTime = session.stateData.stepStartTime + 10000L
        val maxTime = session.currentStepEndTime

        // Check for any active death triggers (ACTING, PENDING, or SUBMITTED but not processed)
        val hasActiveTriggers = session.stateData.submittedActions.any { instance ->
            val isUnprocessed = instance.status != ActionStatus.PROCESSED && instance.status != ActionStatus.SKIPPED

            if (instance.actionDefinitionId != null) {
                val action = roleRegistry.getAction(instance.actionDefinitionId!!)
                action?.timing == ActionTiming.DEATH_TRIGGER && isUnprocessed
            } else {
                // If action not chosen yet, check if it's a dead player acting in the announcement phase
                val isDeadPlayer = session.getPlayer(instance.actor)?.alive == false
                isDeadPlayer && isUnprocessed
            }
        }

        // Check for players who are dead but death events haven't finished
        val hasUnprocessedDeaths =
            session.players.values.any { !it.alive && !session.stateData.processedDeathPlayerIds.contains(it.id) }

        // Check for active speeches (last words) or police transfers
        val speechStatus = WerewolfApplication.speechService.getSpeechStatus(session.guildId)
        val policeStatus = WerewolfApplication.policeService.getPoliceStatus(session.guildId)
        val hasOngoingEvents = speechStatus != null || policeStatus != null

        var shouldAdvance = false
        if (now >= maxTime) {
            log.info("DeathAnnouncementStep timeout for guild ${session.guildId}. Advancing.")
            shouldAdvance = true
        } else if (now >= minTime) {
            if (!hasActiveTriggers && !hasUnprocessedDeaths && !hasOngoingEvents) {
                log.info("DeathAnnouncementStep early exit for guild ${session.guildId} (no active triggers/events). Advancing.")
                shouldAdvance = true
            }
        }

        if (shouldAdvance) {
            service.nextStep(session)
        } else {
            log.debug("DeathAnnouncementStep checkAdvance for guild ${session.guildId}: should not advance yet. Has triggers: $hasActiveTriggers, Unprocessed deaths: $hasUnprocessedDeaths, Ongoing events: $hasOngoingEvents, Time until min: ${minTime - now}ms")
        }
    }

    override fun onEnd(session: Session, service: GameStateService) {
    }

    override fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any> {
        return mapOf("success" to true)
    }

    override fun getDurationSeconds(session: Session): Int {
        // Use a persistent way to count active triggers
        val triggerCount = session.stateData.submittedActions.count { instance ->
            val isUnprocessed = instance.status != ActionStatus.PROCESSED && instance.status != ActionStatus.SKIPPED

            if (instance.actionDefinitionId != null) {
                val action = roleRegistry.getAction(instance.actionDefinitionId!!)
                action?.timing == ActionTiming.DEATH_TRIGGER && isUnprocessed
            } else {
                val isDeadPlayer = session.getPlayer(instance.actor)?.alive == false
                isDeadPlayer && isUnprocessed
            }
        }

        return 10 + (triggerCount * 30)
    }
}
