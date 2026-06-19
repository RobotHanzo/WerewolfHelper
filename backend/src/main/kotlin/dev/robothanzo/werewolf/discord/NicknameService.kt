package dev.robothanzo.werewolf.discord

import dev.robothanzo.werewolf.domain.Seat
import dev.robothanzo.werewolf.i18n.Msg
import org.springframework.stereotype.Service

/**
 * Builds the managed server nickname — the single source of visible game state on Discord:
 * `[死人] 玩家NN [警長]` (dead prefix optional, police suffix optional). The police badge is **only**
 * a nickname suffix, never a Discord role (role-based badges broke transfers in the past).
 *
 * The grammar is pure and testable here; applying it (hierarchy preflight, owner skip, no-op skip)
 * is `JDA.syncNickname`'s job.
 */
@Service
class NicknameService(private val msg: Msg) {

    fun nicknameFor(seat: Seat): String = build(seat.paddedNumber, fullyDead = !seat.alive, police = seat.police)

    fun build(paddedNumber: String, fullyDead: Boolean, police: Boolean): String {
        val parts = buildList {
            if (fullyDead) add(msg.msg("nickname.dead_prefix"))
            add(msg.msg("seat.name", paddedNumber))
            if (police) add(msg.msg("nickname.police_suffix"))
        }
        return parts.joinToString(" ")
    }
}
