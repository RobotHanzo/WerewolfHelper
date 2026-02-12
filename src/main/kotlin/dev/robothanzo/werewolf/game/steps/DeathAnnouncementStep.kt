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
import dev.robothanzo.werewolf.service.GameActionService
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.GameStateService
import dev.robothanzo.werewolf.utils.CmdUtils
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
class DeathAnnouncementStep(
    private val roleRegistry: dev.robothanzo.werewolf.game.roles.RoleRegistry,
    private val gameActionService: GameActionService,
    private val roleActionExecutor: dev.robothanzo.werewolf.game.roles.actions.RoleActionExecutor,
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

        gameSessionService.withLockedSession(guildId) { lockedSession ->
            // Resolve all pending night actions and get deaths
            val resolutionResult = lockedSession.resolveNightActions(roleActionExecutor, roleRegistry)

            // Process deaths - mark players as dead based on resolution result
            val allDeaths = mutableSetOf<Int>()

            for ((deathCause, deaths) in resolutionResult.deaths) {
                for (userId in deaths) {
                    allDeaths.add(userId)
                }
            }

            // Mark players as dead
            for (userId in allDeaths) {
                // For day 1, allow last words so player can speak during death announcement
                gameActionService.markPlayerDead(lockedSession, userId, lockedSession.day == 1)
            }

            // Store tonight's deaths in stateData for frontend display
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
                lockedSession.courtTextChannel?.sendMessage("**:angel: 昨晚是平安夜**")?.queue()
            }

            // Announce good morning on day 2+
            if (lockedSession.day > 1) {
                lockedSession.addLog(LogType.SYSTEM, "早上好，各位玩家")
            }

            // Set initial end time based on triggers
            val duration = getDurationSeconds(lockedSession)
            lockedSession.currentStepEndTime = System.currentTimeMillis() + (duration * 1000L)

            log.info("Death announcement started for guild $guildId. Deaths: $allDeaths, Duration: ${duration}s")
            gameSessionService.broadcastSessionUpdate(lockedSession)
        }

        // Schedule tasks for potential advancement (one-shot fallbacks)
        // Task 1: Minimum display time (10s as requested)
        CmdUtils.schedule({
            gameSessionService.withLockedSession(guildId) { currentSession ->
                checkAdvance(currentSession, service)
            }
        }, 10000)

        // Task 2: Maximum duration
        val duration = getDurationSeconds(session)
        if (duration > 10) {
            CmdUtils.schedule({
                gameSessionService.withLockedSession(guildId) { currentSession ->
                    checkAdvance(currentSession, service)
                }
            }, duration * 1000L + 500) // Small buffer
        }
    }

    fun checkAdvance(session: Session, service: GameStateService) {
        if (session.currentState != id) return

        val now = System.currentTimeMillis()
        val minTime = session.stateData.stepStartTime + 10000L
        val maxTime = session.currentStepEndTime

        // Check for any active death triggers (ACTING, PENDING, or SUBMITTED but not processed)
        val hasActiveTriggers = session.stateData.submittedActions.any { instance ->
            val isDeadPlayer = session.stateData.deadPlayers.contains(instance.actor)
            val isUnprocessed = instance.status != ActionStatus.PROCESSED && instance.status != ActionStatus.SKIPPED

            if (instance.actionDefinitionId != null) {
                val action = roleRegistry.getAction(instance.actionDefinitionId!!)
                action?.timing == ActionTiming.DEATH_TRIGGER && isUnprocessed
            } else {
                // If action not chosen yet, check if it's a dead player acting in the announcement phase
                isDeadPlayer && isUnprocessed
            }
        }

        var shouldAdvance = false
        if (now >= maxTime) {
            log.info("DeathAnnouncementStep timeout for guild ${session.guildId}. Advancing.")
            shouldAdvance = true
        } else if (now >= minTime) {
            if (!hasActiveTriggers) {
                log.info("DeathAnnouncementStep early exit for guild ${session.guildId} (no active triggers). Advancing.")
                shouldAdvance = true
            }
        }

        if (shouldAdvance) {
            service.nextStep(session)
        } else {
            log.debug("DeathAnnouncementStep checkAdvance for guild ${session.guildId}: should not advance yet. Has triggers: $hasActiveTriggers, Time until min: ${minTime - now}ms")
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
            val isDeadPlayer = session.stateData.deadPlayers.contains(instance.actor)
            val isUnprocessed = instance.status != ActionStatus.PROCESSED && instance.status != ActionStatus.SKIPPED

            if (instance.actionDefinitionId != null) {
                val action = roleRegistry.getAction(instance.actionDefinitionId!!)
                action?.timing == ActionTiming.DEATH_TRIGGER && isUnprocessed
            } else {
                isDeadPlayer && isUnprocessed
            }
        }

        return 10 + (triggerCount * 30)
    }
}
