package dev.robothanzo.werewolf.model

import dev.robothanzo.werewolf.database.documents.Player
import java.util.*

data class Candidate(
    val player: Player,
    var expelPK: Boolean = false,
    val electors: MutableList<Long> = LinkedList(),
    var quit: Boolean = false
) {
    fun getElectorsAsMention(): List<String> {
        return electors.map { "<@!$it>" }
    }

    fun getVotes(police: Player?): Float {
        var hasPolice = police != null
        if (hasPolice) {
            hasPolice = electors.contains(police!!.user?.idLong)
        }
        return ((electors.size + (if (hasPolice) 0.5 else 0.0)) * (if (quit) 0 else 1)).toFloat()
    }

    companion object {
        fun getComparator(): Comparator<Candidate> {
            return Comparator.comparingInt { o: Candidate -> o.player.id }
        }

        fun getWinner(candidates: Collection<Candidate>, police: Player?): List<Candidate> {
            val winners = LinkedList<Candidate>()
            var winningVotes = 0f
            for (candidate in candidates) {
                val votes = candidate.getVotes(police)
                if (votes <= 0) continue
                if (votes > winningVotes) {
                    winningVotes = votes
                    winners.clear()
                    winners.add(candidate)
                } else if (votes == winningVotes) {
                    winners.add(candidate)
                }
            }
            return winners
        }
    }
}
