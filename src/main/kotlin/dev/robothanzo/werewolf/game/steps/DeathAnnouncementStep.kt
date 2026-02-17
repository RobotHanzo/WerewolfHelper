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

        // Delegate death processing to the first available dead player (if any)
        // The processCascadingDeaths function will handle ALL unprocessed deaths in the session
        val currentSession = gameSessionService.getSession(guildId).orElse(null)
        val firstDead = currentSession?.players?.values?.sortedBy { it.id }?.firstOrNull {
            !it.alive && !currentSession.stateData.processedDeathPlayerIds.contains(it.id)
        }

        if (firstDead != null) {
            log.info("Starting death processing chain from player ${firstDead.id}")
            firstDead.processCascadingDeaths {
                log.info("All death events processed. Advancing step.")
                service.nextStep(currentSession)
            }
        } else {
            // No deaths to process, just wait for minimum duration then advance
            log.info("No deaths to process in DeathAnnouncementStep. Scheduling advance.")
            CmdUtils.schedule({
                gameSessionService.withLockedSession(guildId) { s ->
                    service.nextStep(s)
                }
            }, 10000)
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
            if (!hasActiveTriggers && !hasUnprocessedDeaths && !hasOngoingEvents && !session.stateData.deathProcessingInProgress) {
                log.info("DeathAnnouncementStep early exit for guild ${session.guildId} (no active triggers/events/processing). Advancing.")
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
