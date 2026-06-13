package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.discord.DiscordGateway
import dev.robothanzo.werewolf.discord.DiscordInteractionHandler
import dev.robothanzo.werewolf.discord.InteractionIds
import dev.robothanzo.werewolf.discord.InteractionReply
import dev.robothanzo.werewolf.discord.SeatOption
import dev.robothanzo.werewolf.domain.Faction
import dev.robothanzo.werewolf.domain.GameSession
import dev.robothanzo.werewolf.domain.LogSeverity
import dev.robothanzo.werewolf.domain.NightIntentData
import dev.robothanzo.werewolf.domain.NightState
import dev.robothanzo.werewolf.domain.Phase
import dev.robothanzo.werewolf.game.GameConstants
import dev.robothanzo.werewolf.game.flow.GameScheduler
import dev.robothanzo.werewolf.game.night.NightAbility
import dev.robothanzo.werewolf.game.night.NightDeclarationsBuilder
import dev.robothanzo.werewolf.game.night.NightPlanner
import dev.robothanzo.werewolf.game.night.NightResolver
import dev.robothanzo.werewolf.game.night.Effect
import dev.robothanzo.werewolf.game.roles.RoleIds
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import dev.robothanzo.werewolf.game.win.WinConditionChecker
import dev.robothanzo.werewolf.i18n.Msg
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Runs an automated night end-to-end: the [NightPlanner] builds the simultaneous-wave plan, each
 * ability's actor(s) are prompted in their private channel (a select menu for individual roles,
 * vote buttons for the collective wolf kill), submissions are collected into the persisted
 * [NightState], and the [NightResolver] turns them into deaths — on the deadline or once everyone
 * has acted.
 *
 * Implements [DiscordInteractionHandler] so the gateway routes button/menu clicks straight here.
 */
@Service
class NightOrchestrator(
    abilities: List<NightAbility>,
    private val planner: NightPlanner,
    private val resolver: NightResolver,
    private val declarations: NightDeclarationsBuilder,
    private val sessionService: GameSessionService,
    private val win: WinConditionChecker,
    private val roles: RoleRegistry,
    private val gateway: DiscordGateway,
    private val scheduler: GameScheduler,
    private val msg: Msg,
) : DiscordInteractionHandler {

    private val log = LoggerFactory.getLogger(javaClass)
    private val abilitiesById = abilities.associateBy { it.id }
    private val abilitiesByRole = abilities.groupBy { it.roleId }
    private val wolfKillAbility = abilities.firstOrNull { Effect.WOLF_KILL in it.writes }

    @PostConstruct
    fun register() = gateway.setInteractionHandler(this)

    // ---------- starting a night ----------
    fun startNight(guildId: Long) {
        sessionService.mutate(guildId) { session -> planNight(session) }
        val session = sessionService.find(guildId) ?: return
        promptActors(session)
        val endsAt = session.nightState.endsAt
        scheduler.schedule(guildId, GameScheduler.NIGHT, endsAt - System.currentTimeMillis()) { resolveNight(guildId) }
    }

    private fun planNight(session: GameSession) {
        val alive = session.aliveSeats()
        val present = alive.flatMap { it.livingCards() }.map { it.roleId }.toSet()
        val wolfParticipants = alive.filter { seat ->
            seat.livingCards().any { roles.factionOf(it.roleId) == Faction.WOLF && it.roleId != RoleIds.GARGOYLE }
        }.map { it.number }.toSet()

        val active = abilitiesByRole.values.flatten()
            .filter { it.id != wolfKillAbility?.id }
            .filter { it.roleId in present }
            .filterNot { it.firstNightOnly && session.day != 1 }
            .filterNot { it.id == "demon_hunter.hunt" && session.day <= 1 }
            .toMutableList()
        if (wolfParticipants.isNotEmpty()) wolfKillAbility?.let { active.add(it) }

        val plan = planner.plan(active)
        val endsAt = System.currentTimeMillis() + GameConstants.NIGHT_SECONDS * 1000L
        session.nightState = NightState(
            active = true,
            day = session.day,
            endsAt = endsAt,
            waves = plan.waves.map { wave -> wave.abilities.map { it.id }.toMutableList() }.toMutableList(),
            wolfParticipants = wolfParticipants.toMutableSet(),
        )
        session.stepEndsAt = endsAt
        sessionService.log(session.guildId, LogSeverity.ACTION, "night.start", session.day)
    }

    private fun promptActors(session: GameSession) {
        val guildId = session.guildId
        val options = session.aliveSeats().map { SeatOption(it.number, "玩家${it.paddedNumber}") }
        session.nightState.waves.flatten().forEach { abilityId ->
            if (abilityId == wolfKillAbility?.id) {
                gateway.promptWolfVote(guildId, session.nightState.wolfParticipants.toList(), options)
                return@forEach
            }
            val ability = abilitiesById[abilityId] ?: return@forEach
            val actors = session.aliveSeats().filter { seat -> seat.livingCards().any { it.roleId == ability.roleId } }
            val roleName = roles.localizedName(ability.roleId)
            val extras = if (ability.roleId == RoleIds.WITCH) listOf(InteractionIds.WITCH_SAVE to "解救今晚刀口") else emptyList()
            actors.forEach { actor ->
                gateway.promptNightAction(
                    guildId, actor.number, "${InteractionIds.NIGHT_ACTION}:${ability.id}",
                    "🌙 $roleName：請選擇今晚的行動目標", options, extras, ability.optional, ability.targetCount,
                )
            }
        }
    }

    // ---------- handling interactions ----------
    override fun handle(guildId: Long, userId: Long, customId: String, values: List<String>): InteractionReply? {
        val session = sessionService.find(guildId) ?: return null
        val seatNumber = session.seats.firstOrNull { it.memberId == userId }?.number
            ?: return InteractionReply("你不是這場遊戲的玩家")
        if (!session.nightState.active || session.nightState.resolved) return InteractionReply("夜晚行動已結束")

        var reply = "已收到"
        sessionService.mutate(guildId) { s ->
            val night = s.nightState
            when {
                customId.startsWith(InteractionIds.WOLF_VOTE) -> {
                    if (seatNumber !in night.wolfParticipants) {
                        reply = "你不是狼人"
                        return@mutate
                    }
                    val raw = customId.substringAfterLast(":")
                    night.wolfVotes[seatNumber] = if (raw == InteractionIds.SKIP) -1 else raw.toIntOrNull() ?: -1
                    reply = if (raw == InteractionIds.SKIP) "已投票：本夜不刀" else "已投票：玩家${raw.padStart(2, '0')}"
                }

                customId.startsWith(InteractionIds.NIGHT_ACTION) -> {
                    val abilityId = customId.removePrefix("${InteractionIds.NIGHT_ACTION}:")
                    val ability = abilitiesById[abilityId] ?: return@mutate
                    val intent = NightIntentData(abilityId, ability.roleId, mutableListOf(seatNumber))
                    val first = values.firstOrNull()
                    when {
                        first == null || first == InteractionIds.SKIP -> intent.skipped = true
                        ability.roleId == RoleIds.WITCH && first == InteractionIds.WITCH_SAVE -> intent.meta["save"] = 1
                        ability.roleId == RoleIds.WITCH -> intent.meta["poison"] = first.toInt()
                        else -> intent.targets = values.filter { it != InteractionIds.SKIP }.map { it.toInt() }.toMutableList()
                    }
                    night.intents.removeAll { it.abilityId == abilityId }
                    night.intents.add(intent)
                    reply = if (intent.skipped) "已選擇不行動" else "已提交行動"
                }
            }
        }
        if (isComplete(guildId)) resolveNight(guildId)
        return InteractionReply(reply)
    }

    private fun isComplete(guildId: Long): Boolean {
        val night = sessionService.find(guildId)?.nightState ?: return false
        if (!night.active) return false
        return night.waves.flatten().all { abilityId ->
            if (abilityId == wolfKillAbility?.id) night.wolfParticipants.all { night.wolfVotes.containsKey(it) }
            else night.intents.any { it.abilityId == abilityId }
        }
    }

    // ---------- resolution ----------
    fun resolveNight(guildId: Long) {
        scheduler.cancel(guildId, GameScheduler.NIGHT)
        sessionService.mutate(guildId) { session ->
            val night = session.nightState
            if (!night.active || night.resolved) return@mutate

            val resolution = resolver.resolve(declarations.build(night), session)
            resolution.deaths.forEach { death ->
                session.seat(death.seat)?.let { seat ->
                    val card = seat.cards.firstOrNull { !it.dead }
                    if (card != null) {
                        card.dead = true
                        sessionService.log(guildId, LogSeverity.ALERT, "night.death", seat.paddedNumber, roles.localizedName(card.roleId))
                    }
                }
            }
            announceInvestigations(session)

            val summary = if (resolution.deaths.isEmpty()) msg.msg("night.peaceful")
            else resolution.deaths.joinToString("、") { msg.msg("seat.name", session.seat(it.seat)?.paddedNumber ?: "") }
            night.active = false
            night.resolved = true
            night.summary = summary
            session.stepEndsAt = null
            sessionService.log(guildId, LogSeverity.ACTION, "night.resolved", summary)

            val result = win.check(session)
            if (result.over) {
                session.phase = Phase.OVER
                sessionService.log(guildId, LogSeverity.ALERT, if (result.winner == Faction.WOLF) "game.over.wolf" else "game.over.good")
            } else {
                session.phase = Phase.DAWN
            }
        }
    }

    /** DM each investigator the faction of the seat they checked. */
    private fun announceInvestigations(session: GameSession) {
        session.nightState.intents
            .filter { it.abilityId.endsWith("investigate") && !it.skipped && it.targets.isNotEmpty() }
            .forEach { intent ->
                val actor = intent.actorSeats.firstOrNull() ?: return@forEach
                val target = session.seat(intent.targets.first()) ?: return@forEach
                val card = target.livingCards().firstOrNull() ?: target.cards.firstOrNull() ?: return@forEach
                val faction = if (target.clone) Faction.GOD else roles.factionOf(card.roleId)
                val verdict = if (faction == Faction.WOLF) "狼人" else "好人"
                gateway.sendSeatMessage(session.guildId, actor, "查驗 玩家${target.paddedNumber} → $verdict")
            }
    }
}
