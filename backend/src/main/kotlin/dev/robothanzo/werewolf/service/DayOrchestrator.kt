package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.discord.ButtonStyle
import dev.robothanzo.werewolf.discord.CourtButton
import dev.robothanzo.werewolf.discord.DiscordGateway
import dev.robothanzo.werewolf.discord.DiscordInteractionHandler
import dev.robothanzo.werewolf.discord.InteractionIds
import dev.robothanzo.werewolf.discord.InteractionReply
import dev.robothanzo.werewolf.discord.NicknameService
import dev.robothanzo.werewolf.discord.SoundCue
import dev.robothanzo.werewolf.domain.Faction
import dev.robothanzo.werewolf.domain.GameSession
import dev.robothanzo.werewolf.domain.LogSeverity
import dev.robothanzo.werewolf.domain.Phase
import dev.robothanzo.werewolf.domain.Seat
import dev.robothanzo.werewolf.game.GameConstants
import dev.robothanzo.werewolf.game.day.DuelResolver
import dev.robothanzo.werewolf.game.flow.GameScheduler
import dev.robothanzo.werewolf.game.roles.RoleIds
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import dev.robothanzo.werewolf.game.speech.SpeechDirection
import dev.robothanzo.werewolf.game.speech.SpeechFlow
import dev.robothanzo.werewolf.game.speech.SpeechService
import dev.robothanzo.werewolf.game.vote.Poll
import dev.robothanzo.werewolf.game.vote.PollEngine
import dev.robothanzo.werewolf.game.vote.PollKind
import dev.robothanzo.werewolf.game.vote.PollOutcome
import dev.robothanzo.werewolf.game.vote.PollStage
import dev.robothanzo.werewolf.game.win.WinConditionChecker
import dev.robothanzo.werewolf.i18n.Msg
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import kotlin.random.Random

/**
 * Runs the day-side loop on Discord, the mirror image of [NightOrchestrator]: it reacts to each
 * day-phase entry, posts court announcements / audio cues / interactive prompts, persists the flow
 * on [GameSession.speech] / [GameSession.poll], and routes the resulting button clicks back in. All
 * sequencing/tally rules live in the pure [SpeechService] / [PollEngine]; this class is wiring +
 * persistence only.
 *
 * State changes happen in single `mutate` blocks; the internal `*Internal` helpers operate on the
 * already-loaded session (so they never nest a second `mutate`) and own the Discord/scheduler side
 * effects. Scheduler deadlines and dashboard control endpoints call back into the public methods,
 * each of which wraps exactly one `mutate`.
 */
@Service
class DayOrchestrator(
    private val sessionService: GameSessionService,
    private val speeches: SpeechService,
    private val polls: PollEngine,
    private val win: WinConditionChecker,
    private val roles: RoleRegistry,
    private val nicknames: NicknameService,
    private val gateway: DiscordGateway,
    private val scheduler: GameScheduler,
    private val announcer: CourtAnnouncer,
    private val router: InteractionRouter,
    private val msg: Msg,
    private val deaths: DeathService,
    private val duel: DuelResolver,
) : DiscordInteractionHandler {

    /** Set by [GameFlowCoordinator] in its `@PostConstruct` (back-reference, breaks the bean cycle). */
    var coordinator: GameFlowCoordinator? = null

    @PostConstruct
    fun register() = router.register(InteractionIds.NS_DAY, this)

    // ======================= phase entry points (from GameFlowCoordinator dispatch) =======================

    /** 天亮：morning cue, unmute, announce the night's deaths (or 平安夜), then run last words. */
    fun enterDawn(guildId: Long) = mutateThenAdvance(guildId) { enterDawnInternal(it) }

    /** Start the daytime speech round; if there is a police badge the holder picks the direction. */
    fun startSpeeches(guildId: Long) = mutateThenAdvance(guildId) { startSpeechesInternal(it) }

    /** Start the day-1 police election at its enrollment stage. */
    fun startPoliceElection(guildId: Long) = mutateThenAdvance(guildId) { startPoliceElectionInternal(it) }

    /** Open the expel vote over all living seats. */
    fun startExpelVote(guildId: Long) = mutateThenAdvance(guildId) { startExpelVoteInternal(it) }

    // ======================= dashboard control endpoints (DayController) =======================

    fun skipCurrentSpeaker(guildId: Long) = mutateThenAdvance(guildId) { advanceSpeakerInternal(it) }

    fun stopSpeech(guildId: Long) = mutateThenAdvance(guildId) { endSpeechInternal(it) }

    fun chooseDirection(guildId: Long, direction: SpeechDirection) =
        mutateThenAdvance(guildId) { chooseDirectionInternal(it, direction) }

    /** Force the current poll stage to resolve / advance early (same op the scheduler deadline runs). */
    fun resolvePollStage(guildId: Long) = mutateThenAdvance(guildId) { resolvePollStageInternal(it) }

    // ======================= automatic stage progression =======================

    /**
     * Run [block] in one `mutate`, then auto-advance the phase if this stage has finished its
     * interactive work. A day phase is "done" once both its poll and its speech flow are idle —
     * which is exactly the settled state every completion path leaves behind (a poll resolving to
     * 平安夜/election/expel-last-words, a speech round ending, etc.). The next phase's entry sets up
     * fresh poll/speech, so the walk halts as soon as there is something to wait on.
     */
    private fun mutateThenAdvance(guildId: Long, block: (GameSession) -> Unit) {
        sessionService.mutate(guildId) { block(it) }
        maybeAdvance(guildId)
    }

    private fun maybeAdvance(guildId: Long) {
        val s = sessionService.find(guildId) ?: return
        if (s.phase in ADVANCEABLE_DAY_PHASES && s.poll == null && s.speech == null) {
            coordinator?.advance(guildId)
        }
    }

    // ======================= day-phase role actions (ROLES.md) =======================

    /** 騎士 決鬥. Returns true when the duel hit a wolf and the game must enter night. */
    fun knightDuel(guildId: Long, knightSeat: Int, targetSeat: Int): Boolean =
        sessionService.mutate(guildId) { knightDuelInternal(it, knightSeat, targetSeat) }

    /** 自爆 (狼人 / 白狼王 / 血月使徒). Returns true when the game must enter night (always, unless the
     *  game just ended). */
    fun selfDestruct(guildId: Long, seat: Int): Boolean =
        sessionService.mutate(guildId) { selfDestructInternal(it, seat) }

    private fun knightDuelInternal(session: GameSession, knightSeat: Int, targetSeat: Int): Boolean {
        val knight = session.seat(knightSeat) ?: return false
        val card = knight.cards.firstOrNull { !it.dead && it.roleId == RoleIds.KNIGHT } ?: return false
        if (knight.duelUsed) return false
        knight.duelUsed = true

        val result = duel.resolve(session, knightSeat, targetSeat)
        // 決鬥死亡的狼王/白狼王/狼美人技能不能發動 → the duel death suppresses revenge (KNIGHT_DUEL cause
        // also spares a 狼美人's charmed seat inside DeathService).
        val produced = deaths.killSeat(session, result.deadSeat, DeathCause.KNIGHT_DUEL, suppressRevenge = true)
        if (result.targetIsWolf) {
            announcer.announce(session.guildId, "day.duel.win", pad(knightSeat), pad(targetSeat))
            sessionService.log(session.guildId, LogSeverity.ALERT, "day.duel.win", pad(knightSeat), pad(targetSeat))
        } else {
            announcer.announce(session.guildId, "day.duel.lose", pad(knightSeat), pad(targetSeat))
            sessionService.log(session.guildId, LogSeverity.ALERT, "day.duel.lose", pad(knightSeat), pad(targetSeat))
        }
        announceDeaths(session, produced)
        checkWinInternal(session)
        if (session.phase == Phase.OVER) return false
        // A wolf hit ends the day immediately; a miss lets the speeches continue.
        if (result.targetIsWolf) { endSpeechInternal(session); enterNightInternal(session); return true }
        return false
    }

    private fun selfDestructInternal(session: GameSession, seat: Int): Boolean {
        val s = session.seat(seat) ?: return false
        val card = s.cards.firstOrNull { !it.dead } ?: return false
        // 狼美人不能自爆 (ROLES.md 狼美人).
        if (card.roleId == RoleIds.WOLF_BEAUTY) return false

        announcer.announce(session.guildId, "day.self_destruct", pad(seat))
        sessionService.log(session.guildId, LogSeverity.ALERT, "day.self_destruct", pad(seat))

        val isWhiteKing = card.roleId == RoleIds.WHITE_WOLF_KING
        // 白狼王 只有自爆可帶人 → arm via SELF_DESTRUCT; every other 自爆 suppresses the shot.
        val produced = deaths.applyDeath(session, seat, card, DeathCause.SELF_DESTRUCT, suppressRevenge = !isWhiteKing)
        announceDeaths(session, produced)

        // 血月使徒 自爆 seals the coming night (神職技能封印 + 不能指刀).
        if (card.roleId == RoleIds.BLOOD_MOON) session.bloodMoonSeal = true

        endSpeechInternal(session)
        clearPollInternal(session)
        checkWinInternal(session)
        if (session.phase == Phase.OVER) return false
        enterNightInternal(session)
        return true
    }

    /** Announce every death the shared applier produced (the primary death plus any 殉情 cascade). */
    private fun announceDeaths(session: GameSession, produced: List<DeathInfo>) {
        produced.forEach { d ->
            val padded = session.seat(d.seat)?.paddedNumber ?: pad(d.seat)
            announcer.announce(session.guildId, "death.announce", padded, roles.localizedName(d.roleId))
            sessionService.log(session.guildId, LogSeverity.ALERT, "death.announce", padded, roles.localizedName(d.roleId))
        }
    }

    /** Set the phase to night; the caller (GameController) runs `night.startNight` after the mutate. */
    private fun enterNightInternal(session: GameSession) {
        session.phase = Phase.NIGHT
    }

    // ======================= scheduler callbacks =======================

    private fun advanceSpeaker(guildId: Long) = mutateThenAdvance(guildId) { advanceSpeakerInternal(it) }

    // ======================= speech flow =======================

    private fun startSpeechesInternal(session: GameSession) {
        val alive = session.aliveSeats().map { it.number }
        if (alive.isEmpty()) return
        val police = session.policeSeat?.takeIf { session.seat(it)?.alive == true }
        if (police != null) {
            // Park on the direction choice and let the badge-holder pick (court buttons or dashboard).
            session.speech = SpeechFlow(order = emptyList(), direction = SpeechDirection.DOWN, from = police, waiting = true)
            session.stepEndsAt = null
            announcer.announce(session.guildId, "speech.start")
            gateway.sendCourtButtons(
                session.guildId, msg.msg("police.dir.prompt"),
                listOf(
                    CourtButton("${InteractionIds.POLICE_DIR}:UP", msg.msg("speech.direction.up"), ButtonStyle.PRIMARY),
                    CourtButton("${InteractionIds.POLICE_DIR}:DOWN", msg.msg("speech.direction.down"), ButtonStyle.PRIMARY),
                ),
            )
        } else {
            val from = alive.first()
            val dir = if (Random.nextBoolean()) SpeechDirection.UP else SpeechDirection.DOWN
            announcer.announce(session.guildId, "speech.start")
            startSpeechFlowInternal(session, speeches.buildOrder(alive, from, dir), from, dir)
        }
    }

    private fun chooseDirectionInternal(session: GameSession, direction: SpeechDirection) {
        val flow = session.speech ?: return
        if (!flow.waiting) return
        val alive = session.aliveSeats().map { it.number }
        startSpeechFlowInternal(session, speeches.buildOrder(alive, flow.from, direction), flow.from, direction)
    }

    private fun startSpeechFlowInternal(
        session: GameSession,
        order: List<Int>,
        from: Int,
        direction: SpeechDirection,
        lastWords: Boolean = false,
    ) {
        if (order.isEmpty()) {
            endSpeechInternal(session)
            return
        }
        session.speech = SpeechFlow(order = order, direction = direction, from = from, lastWords = lastWords)
        if (!lastWords) {
            announcer.announce(session.guildId, "speech.direction", directionLabel(direction), pad(from))
        }
        promptSpeakerInternal(session)
    }

    private fun promptSpeakerInternal(session: GameSession) {
        val flow = session.speech ?: return
        val speaker = speeches.current(flow)
        if (speaker == null) {
            endSpeechInternal(session)
            return
        }
        val seat = session.seat(speaker)
        val endsAt = System.currentTimeMillis() + GameConstants.SPEECH_SECONDS * 1000L
        flow.endsAt = endsAt
        session.stepEndsAt = endsAt

        if (session.settings.muteAfterSpeech) {
            gateway.muteAll(session.guildId)
            seat?.memberId?.let { gateway.muteMember(session.guildId, it, false) }
        }

        val text = msg.msg(if (flow.lastWords) "speech.last_words" else "speech.speaking", pad(speaker))
        val buttons = buildList {
            add(CourtButton(InteractionIds.SPEECH_SKIP, msg.msg("speech.skip.button"), ButtonStyle.SECONDARY))
            if (!flow.lastWords) add(CourtButton(InteractionIds.SPEECH_INTERRUPT, msg.msg("speech.interrupt.button"), ButtonStyle.DANGER))
        }
        gateway.sendCourtButtons(session.guildId, text, buttons)
        scheduler.schedule(session.guildId, GameScheduler.SPEECH, GameConstants.SPEECH_SECONDS * 1000L) {
            advanceSpeaker(session.guildId)
        }
    }

    private fun advanceSpeakerInternal(session: GameSession) {
        val flow = session.speech ?: return
        speeches.advance(flow)
        if (speeches.isComplete(flow)) endSpeechInternal(session) else promptSpeakerInternal(session)
    }

    private fun endSpeechInternal(session: GameSession) {
        if (session.speech == null) return
        session.speech = null
        session.stepEndsAt = null
        scheduler.cancel(session.guildId, GameScheduler.SPEECH)
        // The police campaign couples its speeches to the poll: when campaign speeches finish, the
        // poll advances out of CAMPAIGN.
        if (session.poll?.stage == PollStage.CAMPAIGN) resolvePollStageInternal(session)
    }

    // ======================= last words =======================

    private fun startLastWordsInternal(session: GameSession, seats: List<Int>) {
        val order = seats.filter { session.seat(it) != null }
        if (order.isEmpty()) return
        session.speech = SpeechFlow(order = order, direction = SpeechDirection.DOWN, from = order.first(), lastWords = true)
        promptSpeakerInternal(session)
    }

    // ======================= dawn =======================

    private fun enterDawnInternal(session: GameSession) {
        gateway.unmuteAll(session.guildId)
        gateway.playSound(session.guildId, SoundCue.MORNING)
        announcer.announce(session.guildId, "game.day.start", session.day)

        val deaths = session.nightState.deaths.toList()
        if (deaths.isEmpty()) {
            announcer.announce(session.guildId, "night.peaceful")
        } else {
            deaths.forEach { n ->
                val seat = session.seat(n)
                val roleName = seat?.cards?.lastOrNull { it.dead }?.let { roles.localizedName(it.roleId) } ?: ""
                announcer.announce(session.guildId, "death.announce", pad(n), roleName)
            }
            startLastWordsInternal(session, deaths)
        }
    }

    // ======================= polls (police election + expel) =======================

    private fun startPoliceElectionInternal(session: GameSession) {
        val poll = Poll(kind = PollKind.POLICE, stage = PollStage.ENROLL)
        session.poll = poll
        gateway.playSound(session.guildId, SoundCue.POLICE_ENROLL_START)
        gateway.sendCourtButtons(
            session.guildId, msg.msg("police.enroll.start"),
            listOf(CourtButton(InteractionIds.POLICE_ENROLL, msg.msg("police.enroll.button"), ButtonStyle.SUCCESS)),
        )
        scheduleStage(session, GameConstants.POLICE_ENROLL_SECONDS, SoundCue.ENROLL_TEN_SECONDS)
    }

    private fun startExpelVoteInternal(session: GameSession) {
        val poll = Poll(kind = PollKind.EXPEL, stage = PollStage.VOTING)
        poll.candidates.addAll(session.aliveSeats().map { it.number })
        session.poll = poll
        beginVotingInternal(session)
    }

    /** Open (or re-open, for a PK runoff) the voting stage for the current poll. */
    private fun beginVotingInternal(session: GameSession) {
        val poll = session.poll ?: return
        poll.stage = PollStage.VOTING
        val police = poll.kind == PollKind.POLICE
        if (police) {
            gateway.playSound(session.guildId, SoundCue.POLICE_VOTE_START)
        } else {
            gateway.playSound(session.guildId, SoundCue.EXPEL_POLL_START)
        }
        val prefix = if (police) InteractionIds.POLICE_VOTE else InteractionIds.EXPEL_VOTE
        val buttons = poll.activeCandidates().sorted()
            .map { CourtButton("$prefix:$it", msg.msg("seat.name", pad(it)), ButtonStyle.PRIMARY) }
        gateway.sendCourtButtons(session.guildId, msg.msg(if (police) "police.vote.start" else "expel.start"), buttons)
        val seconds = if (police) GameConstants.POLICE_VOTE_SECONDS else GameConstants.EXPEL_VOTE_SECONDS
        scheduleStage(session, seconds, SoundCue.POLL_TEN_SECONDS)
    }

    /** The poll-stage state machine — run on the stage deadline or a force-resolve. */
    private fun resolvePollStageInternal(session: GameSession) {
        val poll = session.poll ?: return
        scheduler.cancel(session.guildId, GameScheduler.POLL_STAGE)
        scheduler.cancel(session.guildId, GameScheduler.TEN_SECOND_WARNING)
        val ctx = SessionPollContext(session)
        when (poll.stage) {
            PollStage.ENROLL -> {
                val res = polls.resolveEnrollment(poll)
                when (res.outcome) {
                    PollOutcome.BADGE_DESTROYED -> { announcer.announce(session.guildId, "police.no_candidates"); clearPollInternal(session) }
                    PollOutcome.ELECTED -> electPoliceInternal(session, res.winner!!, votes = null)
                    else -> startCampaignInternal(session) // PK_NEEDED: ≥2 candidates campaign then vote
                }
            }
            PollStage.CAMPAIGN -> beginWithdrawInternal(session)
            PollStage.WITHDRAW -> {
                val active = poll.activeCandidates()
                when (active.size) {
                    0 -> { announcer.announce(session.guildId, "police.destroyed"); clearPollInternal(session) }
                    1 -> electPoliceInternal(session, active.first(), votes = null)
                    else -> beginVotingInternal(session)
                }
            }
            PollStage.VOTING -> resolveVotesInternal(session, ctx)
            PollStage.RESOLVED -> {}
        }
    }

    private fun startCampaignInternal(session: GameSession) {
        val poll = session.poll ?: return
        poll.stage = PollStage.CAMPAIGN
        val candidates = poll.activeCandidates().sorted()
        announcer.announce(session.guildId, "speech.start")
        startSpeechFlowInternal(session, speeches.buildOrder(candidates, candidates.first(), SpeechDirection.DOWN), candidates.first(), SpeechDirection.DOWN)
    }

    private fun beginWithdrawInternal(session: GameSession) {
        val poll = session.poll ?: return
        poll.stage = PollStage.WITHDRAW
        gateway.sendCourtButtons(
            session.guildId, msg.msg("police.withdraw.prompt"),
            listOf(CourtButton(InteractionIds.POLICE_WITHDRAW, msg.msg("police.withdraw.button"), ButtonStyle.DANGER)),
        )
        scheduleStage(session, GameConstants.POLICE_WITHDRAW_SECONDS, null)
    }

    private fun resolveVotesInternal(session: GameSession, ctx: SessionPollContext) {
        val poll = session.poll ?: return
        val res = polls.resolveVotes(poll, ctx)
        when (res.outcome) {
            PollOutcome.ELECTED -> electPoliceInternal(session, res.winner!!, votes = res.tally[res.winner])
            PollOutcome.EXPELLED -> expelInternal(session, res.winner!!, votes = res.tally[res.winner])
            PollOutcome.PK_NEEDED -> {
                if (poll.kind == PollKind.EXPEL) announcer.announce(session.guildId, "expel.tie")
                polls.startPkRound(poll, res.tied)
                beginVotingInternal(session)
            }
            PollOutcome.BADGE_DESTROYED -> { announcer.announce(session.guildId, "police.destroyed"); clearPollInternal(session) }
            PollOutcome.NO_RESULT -> {
                if (poll.kind == PollKind.POLICE) announcer.announce(session.guildId, "police.destroyed")
                else announcer.announce(session.guildId, "expel.no_expel")
                clearPollInternal(session)
            }
        }
    }

    private fun electPoliceInternal(session: GameSession, seat: Int, votes: Double?) {
        session.seats.forEach { it.police = false }
        session.seat(seat)?.let { it.police = true; syncNickname(session, it) }
        session.policeSeat = seat
        if (votes == null) {
            announcer.announce(session.guildId, "police.auto_elected", pad(seat))
        } else {
            announcer.announce(session.guildId, "police.elected", pad(seat), fmtVotes(votes))
        }
        sessionService.log(session.guildId, LogSeverity.ACTION, "police.elected", pad(seat), fmtVotes(votes ?: 0.0))
        clearPollInternal(session)
    }

    private fun expelInternal(session: GameSession, seat: Int, votes: Double?) {
        val seatObj = session.seat(seat)
        announcer.announce(session.guildId, "expel.result", pad(seat), fmtVotes(votes ?: 0.0))
        clearPollInternal(session)

        // 白癡 翻牌免疫放逐: an unrevealed 白癡 survives the expel but loses its future vote.
        if (seatObj != null && seatObj.idiot && !seatObj.idiotRevealed && seatObj.alive) {
            seatObj.idiotRevealed = true
            syncNickname(session, seatObj)
            announcer.announce(session.guildId, "day.idiot.revealed", pad(seat))
            sessionService.log(session.guildId, LogSeverity.INFO, "day.idiot.revealed", pad(seat))
            return
        }

        // 血月使徒 被動: the last wolf to be expelled survives the vote once and gets a night kill.
        val card0 = seatObj?.cards?.firstOrNull { !it.dead }
        if (seatObj != null && card0?.roleId == RoleIds.BLOOD_MOON && !seatObj.bloodMoonRevived && isLastWolf(session, seat)) {
            seatObj.bloodMoonRevived = true
            announcer.announce(session.guildId, "day.blood_moon.survive", pad(seat))
            sessionService.log(session.guildId, LogSeverity.ALERT, "day.blood_moon.survive", pad(seat))
            return
        }

        // A real expel: apply the death through the shared applier (arms 狼王 revenge, cascades 殉情),
        // and record the seat for the 守墓人 (ROLES.md 守墓人 第二晚起得知放逐者陣營).
        session.lastExpelledSeat = seat
        val card = seatObj?.cards?.firstOrNull { !it.dead }
        if (card != null) {
            deaths.applyDeath(session, seat, card, DeathCause.EXPEL).forEach { info ->
                val padded = session.seat(info.seat)?.paddedNumber ?: pad(info.seat)
                announcer.announce(session.guildId, "death.announce", padded, roles.localizedName(info.roleId))
                sessionService.log(session.guildId, LogSeverity.ALERT, "death.announce", padded, roles.localizedName(info.roleId))
            }
        }
        checkWinInternal(session)
        if (session.phase != Phase.OVER) startLastWordsInternal(session, listOf(seat))
    }

    private fun clearPollInternal(session: GameSession) {
        session.poll = null
        session.stepEndsAt = null
        scheduler.cancel(session.guildId, GameScheduler.POLL_STAGE)
        scheduler.cancel(session.guildId, GameScheduler.TEN_SECOND_WARNING)
    }

    private fun scheduleStage(session: GameSession, seconds: Int, warnCue: SoundCue?) {
        val poll = session.poll ?: return
        val endsAt = System.currentTimeMillis() + seconds * 1000L
        poll.stageEndsAt = endsAt
        session.stepEndsAt = endsAt
        armStageTimers(session, warnCue)
    }

    /**
     * Arm the poll-stage resolution + the "10 s left" warning from the **persisted** [Poll.stageEndsAt]
     * deadline (so it works both when the stage opens and when re-armed after a pause resume — the
     * deadline having been shifted forward by the paused duration).
     */
    private fun armStageTimers(session: GameSession, warnCue: SoundCue?) {
        val poll = session.poll ?: return
        val endsAt = poll.stageEndsAt ?: return
        val guildId = session.guildId
        val remainMs = (endsAt - System.currentTimeMillis()).coerceAtLeast(0)
        scheduler.schedule(guildId, GameScheduler.POLL_STAGE, remainMs) { resolvePollStage(guildId) }
        val warnMs = remainMs - GameConstants.TEN_SECONDS_WARNING_AT * 1000L
        if (warnCue != null && warnMs > 0) {
            scheduler.schedule(guildId, GameScheduler.TEN_SECOND_WARNING, warnMs) {
                gateway.playSound(guildId, warnCue)
            }
        }
    }

    /**
     * Re-arm whichever day countdown is live after a pause resume. Exactly one is active at a time:
     * a speaking turn ([SpeechFlow.endsAt]) or a timed poll stage ([Poll.stageEndsAt]); both deadlines
     * have already been shifted forward by the paused duration, so the jobs pick up the time that was
     * left. A parked direction choice / CAMPAIGN (speech-driven) has no timer of its own.
     */
    fun resumeDay(guildId: Long) {
        val session = sessionService.find(guildId) ?: return
        val flow = session.speech
        if (flow != null && !flow.waiting && flow.endsAt != null) {
            val remainMs = (flow.endsAt!! - System.currentTimeMillis()).coerceAtLeast(0)
            scheduler.schedule(guildId, GameScheduler.SPEECH, remainMs) { advanceSpeaker(guildId) }
        }
        val poll = session.poll
        if (poll != null && poll.stage in TIMED_POLL_STAGES && poll.stageEndsAt != null) {
            armStageTimers(session, stageWarnCue(poll))
        }
    }

    /** The "10 s left" cue for a timed poll stage (enrol/vote get one; withdraw is silent). */
    private fun stageWarnCue(poll: Poll): SoundCue? = when (poll.stage) {
        PollStage.ENROLL -> SoundCue.ENROLL_TEN_SECONDS
        PollStage.VOTING -> SoundCue.POLL_TEN_SECONDS
        else -> null
    }

    // ======================= interactions =======================

    // Day prompts live in the shared COURT channel (not a per-seat channel), so the action is always
    // attributed to the clicking member's own seat — [channelId] is unused here.
    override fun handle(guildId: Long, userId: Long, channelId: Long, customId: String, values: List<String>): InteractionReply? {
        val session = sessionService.find(guildId) ?: return null
        val seat = session.seats.firstOrNull { it.memberId == userId }?.number
            ?: return InteractionReply("你不是這場遊戲的玩家")

        var reply = "已收到"
        sessionService.mutate(guildId) { s ->
            val ctx = SessionPollContext(s)
            when {
                customId.startsWith(InteractionIds.SPEECH_SKIP) -> {
                    val flow = s.speech
                    when {
                        flow == null -> reply = "目前沒有發言流程"
                        speeches.current(flow) != seat -> reply = ":x: 你不是發言者"
                        else -> { reply = "已跳過發言"; advanceSpeakerInternal(s) }
                    }
                }

                customId.startsWith(InteractionIds.SPEECH_INTERRUPT) -> {
                    val flow = s.speech
                    when {
                        flow == null || flow.lastWords -> reply = "目前無法投票"
                        speeches.current(flow) == seat -> reply = ":x: 若要結束發言請按跳過"
                        else -> {
                            val speaker = speeches.current(flow)
                            val carried = speeches.registerInterrupt(flow, seat, s.aliveSeats().size)
                            if (carried) {
                                announcer.announce(guildId, "speech.interrupted", pad(speaker ?: seat))
                                reply = "下台投票通過"
                                advanceSpeakerInternal(s)
                            } else {
                                reply = "已投下台票"
                            }
                        }
                    }
                }

                customId.startsWith(InteractionIds.POLICE_DIR) -> {
                    val flow = s.speech
                    if (flow?.waiting == true && seat == s.policeSeat) {
                        val dir = if (customId.endsWith("UP")) SpeechDirection.UP else SpeechDirection.DOWN
                        chooseDirectionInternal(s, dir)
                        reply = "已選擇發言方向"
                    } else {
                        reply = ":x: 現在無法選擇方向"
                    }
                }

                customId.startsWith(InteractionIds.POLICE_ENROLL) -> {
                    val poll = s.poll
                    if (poll == null || poll.kind != PollKind.POLICE) {
                        reply = "目前沒有警長競選"
                    } else if (polls.enroll(poll, seat)) {
                        reply = if (seat in poll.candidates) msg.msg("police.enrolled", pad(seat)) else "已取消參選"
                    } else {
                        reply = ":x: 現在無法參選"
                    }
                }

                customId.startsWith(InteractionIds.POLICE_WITHDRAW) -> {
                    val poll = s.poll
                    reply = if (poll != null && polls.withdraw(poll, seat)) msg.msg("police.withdrew", pad(seat))
                    else ":x: 現在無法退選"
                }

                customId.startsWith(InteractionIds.POLICE_VOTE) || customId.startsWith(InteractionIds.EXPEL_VOTE) -> {
                    val poll = s.poll
                    val candidate = customId.substringAfterLast(":").toIntOrNull()
                    if (poll != null && candidate != null && polls.castVote(poll, ctx, seat, candidate)) {
                        reply = msg.msg("seat.name", pad(candidate))
                        if (votingComplete(s, ctx)) resolvePollStageInternal(s)
                    } else {
                        reply = ":x: 投票失敗"
                    }
                }
            }
        }
        // A court click can complete the stage (final vote resolves the poll; a skip ends the speech),
        // so try to auto-advance just like the dashboard/scheduler paths do.
        maybeAdvance(guildId)
        return InteractionReply(reply)
    }

    /** True once every eligible voter has cast a ballot, so the vote can resolve before the deadline. */
    private fun votingComplete(session: GameSession, ctx: SessionPollContext): Boolean {
        val poll = session.poll ?: return false
        if (poll.stage != PollStage.VOTING) return false
        val eligible = session.aliveSeats().map { it.number }.filter { polls.canVote(poll, ctx, it) }
        return eligible.isNotEmpty() && eligible.all { poll.votes.containsKey(it) }
    }

    // ======================= helpers =======================

    /** True when [seat] is the only living wolf-faction identity left on the board. */
    private fun isLastWolf(session: GameSession, seat: Int): Boolean =
        session.aliveSeats().none { s ->
            s.number != seat && s.livingCards().any { roles.factionOf(it.roleId) == Faction.WOLF }
        }

    private fun checkWinInternal(session: GameSession) {
        val result = win.check(session)
        if (result.over) {
            session.phase = Phase.OVER
            sessionService.log(
                session.guildId, LogSeverity.ALERT,
                if (result.winner == Faction.WOLF) "game.over.wolf" else "game.over.good",
            )
            announcer.announce(session.guildId, if (result.winner == Faction.WOLF) "game.over.wolf" else "game.over.good")
        }
    }

    private fun syncNickname(session: GameSession, seat: Seat) {
        val memberId = seat.memberId ?: return
        if (!gateway.canInteract(session.guildId, memberId)) return
        gateway.setNickname(session.guildId, memberId, nicknames.nicknameFor(seat))
    }

    private fun directionLabel(direction: SpeechDirection): String =
        msg.msg(if (direction == SpeechDirection.UP) "speech.direction.up" else "speech.direction.down")

    private fun pad(seat: Int): String = seat.toString().padStart(2, '0')

    /** Render a weighted tally without a trailing `.0` (3.0 → "3", 3.5 → "3.5"). */
    private fun fmtVotes(votes: Double): String =
        if (votes % 1.0 == 0.0) votes.toInt().toString() else votes.toString()

    private companion object {
        /** Day phases whose work, once their poll+speech are idle, auto-advances to the next phase. */
        val ADVANCEABLE_DAY_PHASES = setOf(Phase.DAWN, Phase.POLICE_ELECTION, Phase.SPEECHES, Phase.EXPEL_VOTE)

        /** Poll stages that run their own scheduled deadline (CAMPAIGN is driven by the speech flow). */
        val TIMED_POLL_STAGES = setOf(PollStage.ENROLL, PollStage.WITHDRAW, PollStage.VOTING)
    }
}
