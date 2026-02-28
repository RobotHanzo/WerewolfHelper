package dev.robothanzo.werewolf.game.roles

import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.*
import dev.robothanzo.werewolf.game.roles.actions.ActionExecutionResult
import dev.robothanzo.werewolf.game.roles.actions.PsychicCheckAction
import dev.robothanzo.werewolf.game.roles.actions.RoleActionExecutor
import dev.robothanzo.werewolf.game.roles.actions.WolfMechanicLearnAction
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class PsychicTests {

    private lateinit var session: Session
    private lateinit var executor: RoleActionExecutor
    private lateinit var roleRegistry: RoleRegistry

    // Actions
    private lateinit var psychicCheckAction: PsychicCheckAction
    private lateinit var wolfMechanicLearnAction: WolfMechanicLearnAction

    @BeforeEach
    fun setup() {
        session = Session()
        session.stateData = GameStateData()

        roleRegistry = mock()
        psychicCheckAction = PsychicCheckAction()
        wolfMechanicLearnAction = WolfMechanicLearnAction(roleRegistry)

        val players = mutableMapOf<String, Player>()
        for (i in 1..4) {
            val player = Player(id = i)
            player.session = session
            players[i.toString()] = player
        }

        // Psychic
        players["1"]?.roles?.add("通靈師")

        // Villager
        players["2"]?.roles?.add("平民")

        // Witch
        players["3"]?.roles?.add("女巫")

        // Wolf Mechanic
        players["4"]?.roles?.add("機械狼")
        players["4"]?.roles?.add("狼人")

        session.players = players
    }

    private fun createAction(
        actor: Int,
        actionId: ActionDefinitionId,
        targets: List<Int> = emptyList()
    ): RoleActionInstance {
        return RoleActionInstance(
            actor = actor,
            actionDefinitionId = actionId,
            targets = targets.toMutableList(),
            actorRole = "",
            submittedBy = ActionSubmissionSource.PLAYER,
            status = ActionStatus.PENDING
        )
    }

    private fun mockChannel(playerId: Int): TextChannel {
        val mockChannel = mock<TextChannel>()
        val mockAction = mock<MessageCreateAction>()
        whenever(mockChannel.sendMessage(any<CharSequence>())).thenReturn(mockAction)

        val player = session.getPlayer(playerId)
        player!!.channelId = playerId.toLong() * 100 // dummy

        val spiedPlayer = spy(player)
        doReturn(mockChannel).whenever(spiedPlayer).channel

        session.players!![playerId.toString()] = spiedPlayer
        return mockChannel
    }

    @Test
    @DisplayName("Psychic checking a normal player")
    fun testPsychicNormalCheck() {
        val psychicId = 1
        val targetId = 3 // Witch
        val mockChannel = mockChannel(psychicId)

        val action = createAction(psychicId, ActionDefinitionId.PSYCHIC_CHECK, listOf(targetId))
        psychicCheckAction.execute(session, action, ActionExecutionResult())

        verify(mockChannel).sendMessage(argThat<CharSequence> { contains("查驗結果") && contains("女巫") })
    }

    @Test
    @DisplayName("Psychic checking a Wolf Mechanic before they learn")
    fun testPsychicWolfMechanicUnlearned() {
        val psychicId = 1
        val targetId = 4 // Mechanic
        val mockChannel = mockChannel(psychicId)

        val action = createAction(psychicId, ActionDefinitionId.PSYCHIC_CHECK, listOf(targetId))
        psychicCheckAction.execute(session, action, ActionExecutionResult())

        verify(mockChannel).sendMessage(argThat<CharSequence> { contains("查驗結果") && contains("機械狼") })
    }

    @Test
    @DisplayName("Psychic checking a Wolf Mechanic after they learn")
    fun testPsychicWolfMechanicLearned() {
        val psychicId = 1
        val mechanicId = 4

        // Mechanic learned "女巫" (Witch)
        val learnAction = createAction(mechanicId, ActionDefinitionId.WOLF_MECHANIC_LEARN, listOf(3))
        learnAction.status = ActionStatus.PROCESSED
        session.stateData.executedActions[1] = mutableListOf(learnAction)

        val mockChannel = mockChannel(psychicId)

        val action = createAction(psychicId, ActionDefinitionId.PSYCHIC_CHECK, listOf(mechanicId))
        psychicCheckAction.execute(session, action, ActionExecutionResult())

        verify(mockChannel).sendMessage(argThat<CharSequence> { contains("查驗結果") && contains("女巫") })
    }
}
