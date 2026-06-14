package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.discord.DiscordGateway
import dev.robothanzo.werewolf.discord.DiscordInteractionHandler
import dev.robothanzo.werewolf.discord.InteractionIds
import dev.robothanzo.werewolf.discord.InteractionReply
import dev.robothanzo.werewolf.discord.SeatOption
import dev.robothanzo.werewolf.discord.SoundCue
import dev.robothanzo.werewolf.domain.Faction
import dev.robothanzo.werewolf.domain.GameSession
import dev.robothanzo.werewolf.domain.LogSeverity
import dev.robothanzo.werewolf.domain.NightIntentData
import dev.robothanzo.werewolf.domain.NightState
import dev.robothanzo.werewolf.domain.Phase
import dev.robothanzo.werewolf.domain.Seat
import dev.robothanzo.werewolf.domain.WolfChatData
import dev.robothanzo.werewolf.game.GameConstants
import dev.robothanzo.werewolf.game.flow.GameScheduler
import dev.robothanzo.werewolf.game.night.NightAbility
import dev.robothanzo.werewolf.game.night.NightDeclarationsBuilder
import dev.robothanzo.werewolf.game.night.NightPlanner
import dev.robothanzo.werewolf.game.night.NightResolver
import dev.robothanzo.werewolf.game.night.Effect
import dev.robothanzo.werewolf.game.roles.RoleIds
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import dev.robothanzo.werewolf.game.roles.RoleTag
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
    private val announcer: CourtAnnouncer,
    private val router: InteractionRouter,
    private val deaths: DeathService,
) : DiscordInteractionHandler {

    private val log = LoggerFactory.getLogger(javaClass)
    private val abilitiesById = abilities.associateBy { it.id }
    private val abilitiesByRole = abilities.groupBy { it.roleId }
    private val wolfKillAbility = abilities.firstOrNull { Effect.WOLF_KILL in it.writes }

    private companion object {
        const val MECHANIC_LEARN_ID = "mechanic_wolf.learn"

        /** Cap on retained wolf-chat lines per night, so a long night can't grow the document unbounded. */
        const val MAX_WOLF_CHAT = 100
    }

    @PostConstruct
    fun register() {
        router.register(InteractionIds.NS_NIGHT, this)
        gateway.setWolfChatHandler(::onWolfChat)
    }

    /**
     * Record a relayed wolf-chat line onto the live night (the gateway has already gated on
     * night-active + wolf-chat membership). Mutating through [GameSessionService.mutate] persists and
     * broadcasts the fresh snapshot, so the judge board updates live. Oldest lines are trimmed once
     * the per-night cap is exceeded.
     */
    fun onWolfChat(guildId: Long, seat: Int, author: String, content: String) {
        sessionService.mutate(guildId) { session ->
            if (!session.nightState.active) return@mutate
            val chat = session.nightState.wolfChat
            chat.add(WolfChatData(seat, author, content, System.currentTimeMillis()))
            if (chat.size > MAX_WOLF_CHAT) chat.subList(0, chat.size - MAX_WOLF_CHAT).clear()
        }
    }

    // ---------- starting a night ----------
    fun startNight(guildId: Long) {
        sessionService.mutate(guildId) { session -> planNight(session) }
        val session = sessionService.find(guildId) ?: return
        // Court cue: night falls, everyone is silenced (the announcement itself was previously log-only).
        gateway.muteAll(guildId)
        gateway.playSound(guildId, SoundCue.NIGHT)
        announcer.announce(guildId, "night.start", session.day)
        promptActors(session)
        val endsAt = session.nightState.endsAt
        scheduler.schedule(guildId, GameScheduler.NIGHT, endsAt - System.currentTimeMillis()) { resolveNight(guildId) }
    }

    private fun planNight(session: GameSession) {
        val alive = session.aliveSeats()
        val present = alive.flatMap { it.livingCards() }.map { it.roleId }.toSet()
        // 機械狼 acts as its learned identity once it has learned (ROLES.md 機械狼 主動技).
        val learned = alive.mapNotNull { it.learnedRoleId }.toSet()
        val mechanicMayLearn = alive.any { s ->
            s.livingCards().any { it.roleId == RoleIds.MECHANIC_WOLF } && s.learnedRoleId == null
        }

        // 血月使徒 自爆 seals this one night: all 神職 abilities void + the wolves cannot knife.
        val sealed = session.bloodMoonSeal
        session.bloodMoonSeal = false

        val wolfParticipants = if (sealed) emptySet() else wolfKillParticipants(session, alive)

        val active = abilitiesByRole.values.flatten()
            .filter { it.id != wolfKillAbility?.id }
            .filter { it.roleId in present || it.roleId in learned }
            .filterNot { it.id == MECHANIC_LEARN_ID && !mechanicMayLearn } // one-shot learn
            .filterNot { it.firstNightOnly && session.day != 1 }
            .filterNot { it.id == "demon_hunter.hunt" && session.day <= 1 }
            .filterNot { sealed && roles.factionOf(it.roleId) == Faction.GOD } // 封印神職技能
            .distinctBy { it.id }
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

    /**
     * Who opens their eyes for the wolf knife. Normally the chat wolves (wolf-faction roles that
     * aren't a 互不相認 大哥). Once no chat wolf is left alive, an `INHERITS_KILL` seat (石像鬼 / 隱狼 /
     * 機械狼) inherits the knife and is armed — 隱狼 only if 隱狼繼承狼刀 is enabled (ROLES.md).
     */
    private fun wolfKillParticipants(session: GameSession, alive: List<Seat>): Set<Int> {
        fun inheritor(roleId: String) = roles.byId(roleId)?.hasTag(RoleTag.INHERITS_KILL) == true
        val chatWolves = alive.filter { seat ->
            seat.livingCards().any { roles.factionOf(it.roleId) == Faction.WOLF && !inheritor(it.roleId) }
        }
        if (chatWolves.isNotEmpty()) return chatWolves.map { it.number }.toSet()

        // No chat wolf left — the 大哥 inherits the knife.
        val inheritors = alive.filter { seat ->
            seat.livingCards().any { card ->
                inheritor(card.roleId) &&
                    (card.roleId != RoleIds.HIDDEN_WOLF || session.settings.hiddenWolfInheritsKnife)
            }
        }
        inheritors.forEach { it.knifeArmed = true }
        return inheritors.map { it.number }.toSet()
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
            // A 機械狼 that has learned a role also acts as that role (besides the real card-holders).
            val actors = session.aliveSeats().filter { seat ->
                seat.livingCards().any { it.roleId == ability.roleId } ||
                    (ability.id != MECHANIC_LEARN_ID && seat.learnedRoleId == ability.roleId)
            }
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
    override fun handle(guildId: Long, userId: Long, channelId: Long, customId: String, values: List<String>): InteractionReply? {
        val session = sessionService.find(guildId) ?: return null
        // The action is attributed to a seat. A player drives their own prompt (memberId match); a
        // judge, who can see every seat channel, drives the prompt **on that seat's behalf** — the
        // seat is whichever one the clicked channel is bound to.
        val ownSeat = session.seats.firstOrNull { it.memberId == userId }?.number
        val channelSeat = session.seats.firstOrNull { it.channelId == channelId && it.channelId != 0L }?.number
        val seatNumber = when {
            channelSeat != null && gateway.isJudge(guildId, userId) -> channelSeat
            ownSeat != null -> ownSeat
            else -> return InteractionReply("你不是這場遊戲的玩家")
        }
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
            // Persist 狼美人 charm / 邱比特 lover bonds first so a same-night death can cascade 殉情.
            persistBonds(session)
            night.deaths.clear()
            // Each death flows through the shared applier (arms 獵人/狼王 revenge, cascades 殉情,
            // 隱狼 auto-death). The resolver's suppressRevenge flags are carried through.
            resolution.deaths.forEach { death ->
                val seat = session.seat(death.seat) ?: return@forEach
                val card = seat.cards.firstOrNull { !it.dead } ?: return@forEach
                deaths.applyDeath(session, death.seat, card, DeathCause.NIGHT, death.suppressRevenge).forEach { info ->
                    if (info.seat !in night.deaths) {
                        night.deaths.add(info.seat)
                        val padded = session.seat(info.seat)?.paddedNumber ?: ""
                        sessionService.log(guildId, LogSeverity.ALERT, "night.death", padded, roles.localizedName(info.roleId))
                    }
                }
            }
            applyLearns(session)
            announceInvestigations(session)
            deliverGravekeeper(session)

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

    /** 守墓人 — from the second night on, DM the faction (好人/狼人) of the last expelled seat
     *  (ROLES.md 守墓人: 第一晚無技能, 第二晚起). */
    private fun deliverGravekeeper(session: GameSession) {
        if (session.day < 2) return
        val expelled = session.lastExpelledSeat?.let { session.seat(it) } ?: return
        val keeper = session.aliveSeats().firstOrNull { s ->
            s.livingCards().any { it.roleId == RoleIds.GRAVEKEEPER }
        } ?: return
        val card = expelled.cards.lastOrNull { it.dead } ?: expelled.cards.firstOrNull() ?: return
        val faction = if (expelled.clone) Faction.GOD else roles.factionOf(card.roleId)
        val verdict = if (faction == Faction.WOLF) "狼人" else "好人"
        gateway.sendSeatMessage(session.guildId, keeper.number, msg.msg("gravekeeper.report", expelled.paddedNumber, verdict))
    }

    /** Persist 狼美人 charm + 邱比特 lover bonds onto the seats so a daytime death can cascade 殉情
     *  (the in-resolver night 殉情 is unchanged; this only feeds the day-phase logic). */
    private fun persistBonds(session: GameSession) {
        session.nightState.intents.filter { !it.skipped }.forEach { intent ->
            when (intent.abilityId) {
                "wolf_beauty.charm" -> {
                    val beauty = intent.actorSeats.firstOrNull() ?: return@forEach
                    val victim = intent.targets.firstOrNull() ?: return@forEach
                    session.seat(beauty)?.charmedSeat = victim
                }
                "cupid.bond" -> {
                    if (intent.targets.size < 2) return@forEach
                    val (a, b) = intent.targets[0] to intent.targets[1]
                    session.seat(a)?.loverSeat = b
                    session.seat(b)?.loverSeat = a
                }
            }
        }
    }

    /** 機械狼 learn — copy the chosen seat's identity into the actor's learnedRoleId (one-shot). */
    private fun applyLearns(session: GameSession) {
        session.nightState.intents
            .filter { it.abilityId == MECHANIC_LEARN_ID && !it.skipped && it.targets.isNotEmpty() }
            .forEach { intent ->
                val mechanic = intent.actorSeats.firstOrNull()?.let { session.seat(it) } ?: return@forEach
                if (mechanic.learnedRoleId != null) return@forEach
                val target = session.seat(intent.targets.first()) ?: return@forEach
                val card = target.livingCards().firstOrNull() ?: target.cards.firstOrNull() ?: return@forEach
                // Kept off the public log — the learned identity is secret, surfaced only on the dashboard.
                mechanic.learnedRoleId = card.roleId
            }
    }

    /** DM each investigator their result: 預言家 reports faction (隱狼 reads 好人); 通靈師/石像鬼 report
     *  the exact identity (a 機械狼 reads as its learned identity). */
    private fun announceInvestigations(session: GameSession) {
        session.nightState.intents
            .filter { it.abilityId.endsWith("investigate") && !it.skipped && it.targets.isNotEmpty() }
            .forEach { intent ->
                val actor = intent.actorSeats.firstOrNull() ?: return@forEach
                val target = session.seat(intent.targets.first()) ?: return@forEach
                val card = target.livingCards().firstOrNull() ?: target.cards.firstOrNull() ?: return@forEach
                val verdict = if (intent.roleId == RoleIds.SEER) {
                    val readsGood = roles.byId(card.roleId)?.hasTag(RoleTag.INVESTIGATED_AS_GOOD) == true
                    val faction = if (target.clone) Faction.GOD else roles.factionOf(card.roleId)
                    if (faction == Faction.WOLF && !readsGood) "狼人" else "好人"
                } else {
                    // 通靈師 / 石像鬼 — exact identity, following a 機械狼's learned id.
                    roles.localizedName(target.learnedRoleId ?: card.roleId)
                }
                gateway.sendSeatMessage(session.guildId, actor, "查驗 玩家${target.paddedNumber} → $verdict")
            }
    }
}
