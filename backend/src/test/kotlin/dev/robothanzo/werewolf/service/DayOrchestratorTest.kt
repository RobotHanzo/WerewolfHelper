package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.discord.InteractionIds
import dev.robothanzo.werewolf.discord.NicknameService
import dev.robothanzo.werewolf.discord.NoOpDiscordGateway
import dev.robothanzo.werewolf.domain.GameSession
import dev.robothanzo.werewolf.game.flow.GameScheduler
import dev.robothanzo.werewolf.game.speech.SpeechService
import dev.robothanzo.werewolf.game.vote.PollEngine
import dev.robothanzo.werewolf.game.vote.PollKind
import dev.robothanzo.werewolf.game.vote.PollStage
import dev.robothanzo.werewolf.game.win.WinConditionChecker
import dev.robothanzo.werewolf.support.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Drives the day-side flows (police election, expel vote, speech interrupt) end-to-end through the
 * orchestrator against the [NoOpDiscordGateway], verifying the persisted [GameSession.speech] /
 * [GameSession.poll] state transitions. [GameSessionService] is mocked so `mutate`/`find` operate on
 * an in-memory session — no Spring context or Mongo needed.
 */
class DayOrchestratorTest {

    private val msg = TestFixtures.msg()
    private val roles = TestFixtures.registry()
    private val gateway = NoOpDiscordGateway()
    private val scheduler = GameScheduler()

    private lateinit var session: GameSession
    private lateinit var sessionService: GameSessionService
    private lateinit var day: DayOrchestrator

    private val gid = 1L

    @BeforeEach
    fun setup() {
        // 1 wolf + 2 seers + 2 villagers: the game stays in progress after a single expel.
        session = TestFixtures.session(
            playerCount = 5,
            seats = listOf(
                TestFixtures.seat(1, "wolf" to false),
                TestFixtures.seat(2, "seer" to false),
                TestFixtures.seat(3, "seer" to false),
                TestFixtures.seat(4, "villager" to false),
                TestFixtures.seat(5, "villager" to false),
            ),
        ) { assigned = true }

        sessionService = mock()
        whenever(sessionService.find(any())).thenAnswer { session }
        whenever(sessionService.mutate(any(), any<(GameSession) -> Any?>())).thenAnswer { inv ->
            inv.getArgument<(GameSession) -> Any?>(1).invoke(session)
        }
        whenever(sessionService.log(any(), any(), any())).thenAnswer { null }

        val announcer = CourtAnnouncer(gateway, msg)
        val router = InteractionRouter(gateway)
        day = DayOrchestrator(
            sessionService = sessionService,
            speeches = SpeechService(),
            polls = PollEngine(),
            win = WinConditionChecker(roles),
            roles = roles,
            nicknames = NicknameService(msg),
            gateway = gateway,
            scheduler = scheduler,
            announcer = announcer,
            router = router,
            msg = msg,
            deaths = DeathService(roles, NicknameService(msg), gateway),
            duel = dev.robothanzo.werewolf.game.day.DuelResolver(roles),
        )
    }

    @Test
    fun `police election elects the most-voted candidate`() {
        day.startPoliceElection(gid)
        assertEquals(PollKind.POLICE, session.poll!!.kind)
        assertEquals(PollStage.ENROLL, session.poll!!.stage)

        // seats 1 and 2 enroll
        day.handle(gid, 1, 0L, InteractionIds.POLICE_ENROLL, emptyList())
        day.handle(gid, 2, 0L, InteractionIds.POLICE_ENROLL, emptyList())
        assertEquals(setOf(1, 2), session.poll!!.candidates)

        day.resolvePollStage(gid) // ENROLL -> CAMPAIGN (campaign speeches start)
        assertEquals(PollStage.CAMPAIGN, session.poll!!.stage)
        assertNotNull(session.speech)

        day.stopSpeech(gid) // campaign speeches end -> CAMPAIGN -> WITHDRAW
        assertEquals(PollStage.WITHDRAW, session.poll!!.stage)

        day.resolvePollStage(gid) // WITHDRAW -> VOTING
        assertEquals(PollStage.VOTING, session.poll!!.stage)

        // non-candidate seats vote: 3 & 4 -> candidate 1, 5 -> candidate 2. The third (final) vote
        // completes the poll and auto-resolves.
        day.handle(gid, 3, 0L, "${InteractionIds.POLICE_VOTE}:1", emptyList())
        day.handle(gid, 4, 0L, "${InteractionIds.POLICE_VOTE}:1", emptyList())
        day.handle(gid, 5, 0L, "${InteractionIds.POLICE_VOTE}:2", emptyList())

        assertEquals(1, session.policeSeat)
        assertTrue(session.seat(1)!!.police)
        assertNull(session.poll)
    }

    @Test
    fun `expel vote kills the elected seat and opens last words`() {
        day.startExpelVote(gid)
        assertEquals(PollKind.EXPEL, session.poll!!.kind)
        assertEquals(PollStage.VOTING, session.poll!!.stage)

        // everyone votes seat 4; the final vote completes and resolves the poll
        (1..5).forEach { day.handle(gid, it.toLong(), 0L, "${InteractionIds.EXPEL_VOTE}:4", emptyList()) }

        assertFalse(session.seat(4)!!.alive)
        assertNull(session.poll)
        assertNotNull(session.speech)        // last-words flow
        assertTrue(session.speech!!.lastWords)
        assertEquals(listOf(4), session.speech!!.order)
    }

    @Test
    fun `expel records the last expelled seat for the gravekeeper`() {
        day.startExpelVote(gid)
        (1..5).forEach { day.handle(gid, it.toLong(), 0L, "${InteractionIds.EXPEL_VOTE}:4", emptyList()) }
        assertEquals(4, session.lastExpelledSeat)
    }

    @Test
    fun `an expelled 白癡 flips its card and survives`() {
        session = TestFixtures.session(
            playerCount = 5,
            seats = listOf(
                TestFixtures.seat(1, "wolf" to false),
                TestFixtures.seat(2, "seer" to false),
                TestFixtures.seat(3, "villager" to false),
                TestFixtures.seat(4, "idiot" to false) { idiot = true },
                TestFixtures.seat(5, "villager" to false),
            ),
        ) { assigned = true }

        day.startExpelVote(gid)
        (1..5).forEach { day.handle(gid, it.toLong(), 0L, "${InteractionIds.EXPEL_VOTE}:4", emptyList()) }

        assertTrue(session.seat(4)!!.alive)            // survives the expel
        assertTrue(session.seat(4)!!.idiotRevealed)    // but is flipped (loses its vote)
        assertNull(session.lastExpelledSeat)           // no real expel happened
    }

    @Test
    fun `first-night deaths open last words at dawn`() {
        session.phase = dev.robothanzo.werewolf.domain.Phase.DAWN
        session.day = 1
        session.nightState.deaths = mutableListOf(4)

        day.enterDawn(gid)

        assertNotNull(session.speech)
        assertTrue(session.speech!!.lastWords)
        assertEquals(listOf(4), session.speech!!.order)
    }

    @Test
    fun `night deaths from the second night on get no last words`() {
        session.phase = dev.robothanzo.werewolf.domain.Phase.DAWN
        session.day = 2
        session.nightState.deaths = mutableListOf(4)

        day.enterDawn(gid)

        assertNull(session.speech) // 第二晚起夜間死亡無遺言
    }

    @Test
    fun `knight duel on a wolf kills the wolf and enters night`() {
        // two wolves so killing one leaves the game in progress (the duel then forces night).
        session = TestFixtures.session(
            playerCount = 6,
            seats = listOf(
                TestFixtures.seat(1, "knight" to false),
                TestFixtures.seat(2, "wolf" to false),
                TestFixtures.seat(3, "wolf" to false),
                TestFixtures.seat(4, "seer" to false),
                TestFixtures.seat(5, "villager" to false),
                TestFixtures.seat(6, "villager" to false),
            ),
        ) { assigned = true; phase = dev.robothanzo.werewolf.domain.Phase.SPEECHES }

        val forceNight = day.knightDuel(gid, 1, 2)
        assertTrue(forceNight)
        assertFalse(session.seat(2)!!.alive)
        assertEquals(dev.robothanzo.werewolf.domain.Phase.NIGHT, session.phase)
    }

    @Test
    fun `knight duel on a good player kills the knight and stays in the day`() {
        session = TestFixtures.session(
            playerCount = 5,
            seats = listOf(
                TestFixtures.seat(1, "knight" to false),
                TestFixtures.seat(2, "wolf" to false),
                TestFixtures.seat(3, "seer" to false),
                TestFixtures.seat(4, "villager" to false),
                TestFixtures.seat(5, "villager" to false),
            ),
        ) { assigned = true; phase = dev.robothanzo.werewolf.domain.Phase.SPEECHES }

        val forceNight = day.knightDuel(gid, 1, 3)
        assertFalse(forceNight)
        assertFalse(session.seat(1)!!.alive)
        assertTrue(session.seat(3)!!.alive)
        assertEquals(dev.robothanzo.werewolf.domain.Phase.SPEECHES, session.phase)
    }

    @Test
    fun `白狼王 self-destruct arms its revenge and seals nothing`() {
        session = TestFixtures.session(
            playerCount = 5,
            seats = listOf(
                TestFixtures.seat(1, "white_wolf_king" to false),
                TestFixtures.seat(2, "wolf" to false),
                TestFixtures.seat(3, "seer" to false),
                TestFixtures.seat(4, "villager" to false),
                TestFixtures.seat(5, "villager" to false),
            ),
        ) { assigned = true; phase = dev.robothanzo.werewolf.domain.Phase.SPEECHES }

        val forceNight = day.selfDestruct(gid, 1)
        assertTrue(forceNight)
        assertFalse(session.seat(1)!!.alive)
        assertTrue(session.seat(1)!!.revengePending)
        assertEquals(dev.robothanzo.werewolf.domain.Phase.NIGHT, session.phase)
    }

    @Test
    fun `血月使徒 self-destruct seals the coming night`() {
        session = TestFixtures.session(
            playerCount = 5,
            seats = listOf(
                TestFixtures.seat(1, "blood_moon" to false),
                TestFixtures.seat(2, "wolf" to false),
                TestFixtures.seat(3, "seer" to false),
                TestFixtures.seat(4, "villager" to false),
                TestFixtures.seat(5, "villager" to false),
            ),
        ) { assigned = true; phase = dev.robothanzo.werewolf.domain.Phase.SPEECHES }

        day.selfDestruct(gid, 1)
        assertTrue(session.bloodMoonSeal)
    }

    @Test
    fun `detonate command self-destructs a wolf and forces night`() {
        session = TestFixtures.session(
            playerCount = 5,
            seats = listOf(
                TestFixtures.seat(1, "wolf" to false),
                TestFixtures.seat(2, "wolf" to false),
                TestFixtures.seat(3, "seer" to false),
                TestFixtures.seat(4, "villager" to false),
                TestFixtures.seat(5, "villager" to false),
            ),
        ) { assigned = true; phase = dev.robothanzo.werewolf.domain.Phase.SPEECHES }

        val reply = day.detonate(gid, 1) // seat 1's member id is 1
        assertEquals(msg.msg("cmd.detonate.ok", "01"), reply)
        assertFalse(session.seat(1)!!.alive)
        assertEquals(dev.robothanzo.werewolf.domain.Phase.NIGHT, session.phase)
    }

    @Test
    fun `detonate is rejected when the caller's current identity is not a wolf`() {
        session = TestFixtures.session(
            playerCount = 5,
            seats = listOf(
                TestFixtures.seat(1, "wolf" to false),
                TestFixtures.seat(2, "seer" to false),
                TestFixtures.seat(3, "seer" to false),
                TestFixtures.seat(4, "villager" to false),
                TestFixtures.seat(5, "villager" to false),
            ),
        ) { assigned = true; phase = dev.robothanzo.werewolf.domain.Phase.SPEECHES }

        val reply = day.detonate(gid, 2) // seat 2 is a 預言家
        assertEquals(msg.msg("cmd.detonate.not_wolf"), reply)
        assertTrue(session.seat(2)!!.alive)
        assertEquals(dev.robothanzo.werewolf.domain.Phase.SPEECHES, session.phase)
    }

    @Test
    fun `detonate is rejected outside the day phases`() {
        session = TestFixtures.session(
            playerCount = 5,
            seats = listOf(
                TestFixtures.seat(1, "wolf" to false),
                TestFixtures.seat(2, "seer" to false),
                TestFixtures.seat(3, "seer" to false),
                TestFixtures.seat(4, "villager" to false),
                TestFixtures.seat(5, "villager" to false),
            ),
        ) { assigned = true; phase = dev.robothanzo.werewolf.domain.Phase.NIGHT }

        val reply = day.detonate(gid, 1)
        assertEquals(msg.msg("cmd.detonate.not_day"), reply)
        assertTrue(session.seat(1)!!.alive)
    }

    @Test
    fun `血月使徒 survives the expel when it is the last wolf`() {
        session = TestFixtures.session(
            playerCount = 5,
            seats = listOf(
                TestFixtures.seat(1, "blood_moon" to false),
                TestFixtures.seat(2, "seer" to false),
                TestFixtures.seat(3, "seer" to false),
                TestFixtures.seat(4, "villager" to false),
                TestFixtures.seat(5, "villager" to false),
            ),
        ) { assigned = true }

        day.startExpelVote(gid)
        (1..5).forEach { day.handle(gid, it.toLong(), 0L, "${InteractionIds.EXPEL_VOTE}:1", emptyList()) }

        assertTrue(session.seat(1)!!.alive)             // survives once
        assertTrue(session.seat(1)!!.bloodMoonRevived)
        assertNull(session.lastExpelledSeat)
    }

    @Test
    fun `expelled 獵人 defers its shot until last words finish`() {
        session = TestFixtures.session(
            playerCount = 5,
            seats = listOf(
                TestFixtures.seat(1, "wolf" to false),
                TestFixtures.seat(2, "seer" to false),
                TestFixtures.seat(3, "villager" to false),
                TestFixtures.seat(4, "hunter" to false),
                TestFixtures.seat(5, "villager" to false),
            ),
        ) { assigned = true }

        day.startExpelVote(gid)
        (1..5).forEach { day.handle(gid, it.toLong(), 0L, "${InteractionIds.EXPEL_VOTE}:4", emptyList()) }

        // 遺言先行：the shot is armed but NOT yet prompted while last words run.
        assertTrue(session.seat(4)!!.revengePending)
        assertFalse(session.seat(4)!!.revengePrompted)
        assertNotNull(session.speech)
        assertTrue(session.speech!!.lastWords)

        // Last words end → the shot is now prompted and still gates the flow.
        day.stopSpeech(gid)
        assertNull(session.speech)
        assertTrue(session.seat(4)!!.revengePending)
        assertTrue(session.seat(4)!!.revengePrompted)
    }

    @Test
    fun `a 獵人 killed from the second night on is prompted immediately at dawn`() {
        session = TestFixtures.session(
            playerCount = 5,
            seats = listOf(
                TestFixtures.seat(1, "wolf" to false),
                TestFixtures.seat(2, "seer" to false),
                TestFixtures.seat(3, "villager" to false),
                TestFixtures.seat(4, "hunter" to false),
                TestFixtures.seat(5, "villager" to false),
            ),
        ) { assigned = true; phase = dev.robothanzo.werewolf.domain.Phase.DAWN; day = 2 }
        // Simulate the night having killed + armed the 獵人.
        session.seat(4)!!.cards.first().dead = true
        session.seat(4)!!.revengePending = true
        session.nightState.deaths = mutableListOf(4)

        day.enterDawn(gid)

        assertNull(session.speech) // 第二晚起夜間死亡無遺言
        assertTrue(session.seat(4)!!.revengePending)
        assertTrue(session.seat(4)!!.revengePrompted) // prompted straight away (gates the speech stage)
    }

    @Test
    fun `firing the armed shot kills the target and clears the gate`() {
        session = TestFixtures.session(
            playerCount = 5,
            seats = listOf(
                TestFixtures.seat(1, "wolf" to false),
                TestFixtures.seat(2, "seer" to false),
                TestFixtures.seat(3, "villager" to false),
                TestFixtures.seat(4, "hunter" to false),
                TestFixtures.seat(5, "villager" to false),
            ),
        ) { assigned = true; phase = dev.robothanzo.werewolf.domain.Phase.DAWN; day = 2 }
        session.seat(4)!!.cards.first().dead = true
        session.seat(4)!!.revengePending = true
        session.seat(4)!!.revengePrompted = true

        day.fireRevenge(gid, 4, 1)

        assertFalse(session.seat(4)!!.revengePending)
        assertFalse(session.seat(4)!!.revengePrompted)
        assertFalse(session.seat(1)!!.alive) // the 獵人 took the wolf with it
    }

    @Test
    fun `declining the armed shot clears the gate`() {
        session = TestFixtures.session(
            playerCount = 5,
            seats = listOf(
                TestFixtures.seat(1, "wolf" to false),
                TestFixtures.seat(2, "seer" to false),
                TestFixtures.seat(3, "villager" to false),
                TestFixtures.seat(4, "hunter" to false),
                TestFixtures.seat(5, "villager" to false),
            ),
        ) { assigned = true; phase = dev.robothanzo.werewolf.domain.Phase.DAWN; day = 2 }
        session.seat(4)!!.cards.first().dead = true
        session.seat(4)!!.revengePending = true
        session.seat(4)!!.revengePrompted = true

        day.skipRevenge(gid, 4)

        assertFalse(session.seat(4)!!.revengePending)
        assertFalse(session.seat(4)!!.revengePrompted)
    }

    @Test
    fun `a speaker who dies mid-round is skipped, not prompted`() {
        // Speaking order [1,2,3,4,5]; seat 2 dies (e.g. a 殉情 cascade) after the order was captured.
        session.speech = dev.robothanzo.werewolf.game.speech.SpeechFlow(
            order = listOf(1, 2, 3, 4, 5),
            direction = dev.robothanzo.werewolf.game.speech.SpeechDirection.DOWN,
            from = 1,
        )
        session.seat(2)!!.cards.first().dead = true

        day.skipCurrentSpeaker(gid) // advance off seat 1 → seat 2 is dead, so it's skipped

        assertEquals(3, session.speech!!.order[session.speech!!.index]) // landed on the next living seat
    }

    @Test
    fun `last-words flow still prompts the dead speaker`() {
        // 遺言 speakers are dead by definition — the skip-dead guard must not apply here, or the
        // dead player's last words would be silently skipped and the flow would end immediately.
        session.phase = dev.robothanzo.werewolf.domain.Phase.DAWN
        session.day = 1
        session.seat(4)!!.cards.first().dead = true
        session.nightState.deaths = mutableListOf(4)

        day.enterDawn(gid)

        assertNotNull(session.speech)
        assertTrue(session.speech!!.lastWords)
        assertEquals(4, session.speech!!.order[session.speech!!.index]) // the dead seat still speaks
    }

    @Test
    fun `abortActiveStage tears down an in-flight speech and poll`() {
        day.startSpeeches(gid)
        assertNotNull(session.speech)

        day.abortActiveStage(session)

        assertNull(session.speech)
        assertNull(session.poll)
        assertNull(session.stepEndsAt)
    }

    @Test
    fun `speech interrupt majority advances to the next speaker`() {
        day.startSpeeches(gid) // no police -> random direction, flow active immediately
        val speaker = session.speech!!.order[session.speech!!.index]

        // a strict majority of the 5 alive players (3) vote the speaker off
        (1..5).filter { it != speaker }.take(3)
            .forEach { day.handle(gid, it.toLong(), 0L, InteractionIds.SPEECH_INTERRUPT, emptyList()) }

        // either advanced to the next speaker or the flow ended (single remaining speaker)
        assertTrue(session.speech == null || session.speech!!.index >= 1)
    }
}
