package dev.robothanzo.werewolf.model

import dev.robothanzo.werewolf.database.documents.Session
import net.dv8tion.jda.api.entities.Message
import java.util.concurrent.ConcurrentHashMap

data class PoliceSession(
    val guildId: Long,
    val channelId: Long,
    val session: Session,
    var state: State = State.NONE,
    var stageEndTime: Long = 0,
    val candidates: MutableMap<Int, Candidate> = ConcurrentHashMap(),
    var message: Message? = null
) {
    enum class State {
        NONE, ENROLLMENT, SPEECH, UNENROLLMENT, VOTING, FINISHED;

        fun canEnroll(): Boolean {
            return this == ENROLLMENT
        }

        fun canQuit(): Boolean {
            return this == ENROLLMENT || this == UNENROLLMENT
        }
    }
}
