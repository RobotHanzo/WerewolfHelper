package dev.robothanzo.werewolf.model

import dev.robothanzo.werewolf.database.documents.Session
import java.util.*

data class SpeechSession(
    val guildId: Long,
    val channelId: Long,
    val session: Session,
    val interruptVotes: MutableList<Long> = LinkedList(),
    val order: MutableList<Session.Player> = LinkedList(),
    var speakingThread: Thread? = null,
    var lastSpeaker: Long? = null,
    var finishedCallback: (() -> Unit)? = null,
    var currentSpeechEndTime: Long = 0,
    var totalSpeechTime: Int = 0,
    var shouldStopCurrentSpeaker: Boolean = false
)
