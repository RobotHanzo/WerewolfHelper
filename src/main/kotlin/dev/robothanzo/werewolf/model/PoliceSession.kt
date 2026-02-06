package dev.robothanzo.werewolf.model

import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import net.dv8tion.jda.api.entities.Message

class PoliceSession(
    guildId: Long,
    channelId: Long,
    session: Session,
    var state: State = State.NONE,
    var stageEndTime: Long = 0,
    message: Message? = null,
    finishedCallback: (() -> Unit)? = null
) : AbstractPoll(guildId, channelId, session, message, finishedCallback) {
    enum class State {
        NONE, ENROLLMENT, SPEECH, UNENROLLMENT, VOTING, FINISHED;

        fun canEnroll(): Boolean {
            return this == ENROLLMENT
        }

        fun canQuit(): Boolean {
            return this == ENROLLMENT || this == UNENROLLMENT
        }
    }

    override fun isEligibleVoter(player: Player): Boolean {
        // Only players who never enrolled (i.e., not present in candidates map) are eligible
        return player.alive && !candidates.containsKey(player.id)
    }
}
