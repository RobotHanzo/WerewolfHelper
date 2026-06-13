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
        )
    }

    @Test
    fun `police election elects the most-voted candidate`() {
        day.startPoliceElection(gid)
        assertEquals(PollKind.POLICE, session.poll!!.kind)
        assertEquals(PollStage.ENROLL, session.poll!!.stage)

        // seats 1 and 2 enroll
        day.handle(gid, 1, InteractionIds.POLICE_ENROLL, emptyList())
        day.handle(gid, 2, InteractionIds.POLICE_ENROLL, emptyList())
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
        day.handle(gid, 3, "${InteractionIds.POLICE_VOTE}:1", emptyList())
        day.handle(gid, 4, "${InteractionIds.POLICE_VOTE}:1", emptyList())
        day.handle(gid, 5, "${InteractionIds.POLICE_VOTE}:2", emptyList())

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
        (1..5).forEach { day.handle(gid, it.toLong(), "${InteractionIds.EXPEL_VOTE}:4", emptyList()) }

        assertFalse(session.seat(4)!!.alive)
        assertNull(session.poll)
        assertNotNull(session.speech)        // last-words flow
        assertTrue(session.speech!!.lastWords)
        assertEquals(listOf(4), session.speech!!.order)
    }

    @Test
    fun `speech interrupt majority advances to the next speaker`() {
        day.startSpeeches(gid) // no police -> random direction, flow active immediately
        val speaker = session.speech!!.order[session.speech!!.index]

        // a strict majority of the 5 alive players (3) vote the speaker off
        (1..5).filter { it != speaker }.take(3)
            .forEach { day.handle(gid, it.toLong(), InteractionIds.SPEECH_INTERRUPT, emptyList()) }

        // either advanced to the next speaker or the flow ended (single remaining speaker)
        assertTrue(session.speech == null || session.speech!!.index >= 1)
    }
}
