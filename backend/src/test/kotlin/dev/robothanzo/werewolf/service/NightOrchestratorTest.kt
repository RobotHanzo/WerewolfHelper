package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.discord.DiscordBot
import dev.robothanzo.werewolf.discord.InteractionIds
import dev.robothanzo.werewolf.discord.NicknameService
import dev.robothanzo.werewolf.domain.GameSession
import dev.robothanzo.werewolf.domain.repo.GameSessionRepository
import dev.robothanzo.werewolf.game.flow.GameScheduler
import dev.robothanzo.werewolf.game.night.NightDeclarationsBuilder
import dev.robothanzo.werewolf.game.night.NightPlanner
import dev.robothanzo.werewolf.game.night.NightResolver
import dev.robothanzo.werewolf.game.roles.RoleIds
import dev.robothanzo.werewolf.game.win.WinConditionChecker
import dev.robothanzo.werewolf.support.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Exercises the 機械狼 learn flow through [NightOrchestrator] with a null [net.dv8tion.jda.api.JDA]
 * (Discord calls no-op), and [GameSessionService] mocked onto an in-memory session (no Spring / Mongo).
 */
class NightOrchestratorTest {

    private val msg = TestFixtures.msg()
    private val roles = TestFixtures.registry()
    private val discord = DiscordBot(null, mock<GameSessionRepository>(), roles, msg)
    private val scheduler = GameScheduler()

    private lateinit var session: GameSession
    private lateinit var sessionService: GameSessionService
    private lateinit var night: NightOrchestrator

    private val gid = 1L

    private fun wire() {
        sessionService = mock()
        whenever(sessionService.find(any())).thenAnswer { session }
        whenever(sessionService.mutate(any(), any<(GameSession) -> Any?>())).thenAnswer { inv ->
            inv.getArgument<(GameSession) -> Any?>(1).invoke(session)
        }
        whenever(sessionService.log(any(), any(), any(), anyOrNull())).thenAnswer { null }

        night = NightOrchestrator(
            abilities = TestFixtures.allAbilities(),
            planner = NightPlanner(),
            resolver = NightResolver(roles),
            declarations = NightDeclarationsBuilder(),
            sessionService = sessionService,
            win = WinConditionChecker(roles),
            roles = roles,
            jda = null,
            discord = discord,
            scheduler = scheduler,
            msg = msg,
            announcer = CourtAnnouncer(null, mock<GameSessionRepository>(), msg),
            router = InteractionRouter(discord),
            deaths = DeathService(roles, NicknameService(msg), null),
        )
    }

    @BeforeEach
    fun setup() = wire()

    @Test
    fun `機械狼 learns the targeted identity`() {
        session = TestFixtures.session(
            playerCount = 5,
            seats = listOf(
                TestFixtures.seat(1, RoleIds.MECHANIC_WOLF to false),
                TestFixtures.seat(2, RoleIds.WITCH to false),
                TestFixtures.seat(3, RoleIds.SEER to false),
                TestFixtures.seat(4, RoleIds.WOLF to false),
                TestFixtures.seat(5, RoleIds.VILLAGER to false),
            ),
        ) { assigned = true; day = 2 }

        night.startNight(gid)
        night.handle(gid, 1L, 0L, "${InteractionIds.NIGHT_ACTION}:mechanic_wolf.learn", listOf("2"))
        night.resolveNight(gid)

        assertEquals(RoleIds.WITCH, session.seat(1)!!.learnedRoleId)
    }

    @Test
    fun `雙身分只有當前身分行動 — seer+witch acts only as seer`() {
        // A double-identity seat holding 預言家 (first) + 女巫 (second), both alive. Only the current
        // identity may act, so 預言家 is planned and 女巫 is not (no other witch is on the board).
        session = TestFixtures.session(
            doubleIdentity = true,
            playerCount = 4,
            seats = listOf(
                TestFixtures.seat(1, RoleIds.SEER to false, RoleIds.WITCH to false),
                TestFixtures.seat(2, RoleIds.WOLF to false, RoleIds.VILLAGER to false),
                TestFixtures.seat(3, RoleIds.VILLAGER to false, RoleIds.VILLAGER to false),
                TestFixtures.seat(4, RoleIds.VILLAGER to false, RoleIds.VILLAGER to false),
            ),
        ) { assigned = true; day = 2 }

        night.startNight(gid)

        val planned = session.nightState.waves.flatten()
        assertTrue("seer.investigate" in planned, "the current 預言家 identity should be planned: $planned")
        assertTrue("witch.potion" !in planned, "the dormant 女巫 identity must not act: $planned")
    }

    @Test
    fun `雙身分只有當前身分行動 — dormant seer wakes once the wolf card dies`() {
        // 狼人(first) + 預言家(second). While both live the seat acts as the wolf; once the wolf card
        // dies the 預言家 becomes the current identity and may investigate.
        session = TestFixtures.session(
            doubleIdentity = true,
            playerCount = 4,
            seats = listOf(
                TestFixtures.seat(1, RoleIds.WOLF to true, RoleIds.SEER to false),
                TestFixtures.seat(2, RoleIds.VILLAGER to false, RoleIds.VILLAGER to false),
                TestFixtures.seat(3, RoleIds.VILLAGER to false, RoleIds.VILLAGER to false),
                TestFixtures.seat(4, RoleIds.VILLAGER to false, RoleIds.VILLAGER to false),
            ),
        ) { assigned = true; day = 2 }

        night.startNight(gid)

        val planned = session.nightState.waves.flatten()
        assertTrue("seer.investigate" in planned, "the now-current 預言家 identity should be planned: $planned")
        assertTrue("wolf.kill" !in planned, "the dead 狼人 card must not knife: $planned")
    }

    @Test
    fun `機械狼 acts as its learned role on later nights`() {
        // The mechanic has learned 女巫; no real 女巫 is on the board, so the planned witch ability
        // can only come from the learned identity.
        session = TestFixtures.session(
            playerCount = 5,
            seats = listOf(
                TestFixtures.seat(1, RoleIds.MECHANIC_WOLF to false) { learnedRoleId = RoleIds.WITCH },
                TestFixtures.seat(2, RoleIds.WOLF to false),
                TestFixtures.seat(3, RoleIds.SEER to false),
                TestFixtures.seat(4, RoleIds.VILLAGER to false),
                TestFixtures.seat(5, RoleIds.VILLAGER to false),
            ),
        ) { assigned = true; day = 3 }

        night.startNight(gid)

        val planned = session.nightState.waves.flatten()
        assertTrue("witch.potion" in planned, "expected the learned 女巫 ability to be planned: $planned")
        assertTrue("mechanic_wolf.learn" !in planned, "the one-shot learn should be spent")
    }
}
