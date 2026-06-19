package dev.robothanzo.werewolf.discord

import dev.robothanzo.werewolf.domain.GameSession
import dev.robothanzo.werewolf.domain.Seat
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.requests.RestAction

/**
 * Direct-JDA helpers shared by the services that talk to Discord (the old `DiscordGateway` seam was
 * removed; each service now holds a nullable `JDA?` and calls these inline). These are **stateless**
 * conveniences — guild/member/channel lookups, the messaging + voice + role mutations, and the night
 * prompt builders — so nothing here caches or owns a connection. Anything stateful (audio cues, the
 * wolf-chat webhook relay, the gateway event loop) lives in [DiscordBot]; provisioning in
 * [GuildProvisioner].
 *
 * Every mutating call is best-effort (it returns rather than throwing on a missing guild/member), so a
 * `null` JDA or an absent entity is a silent no-op — matching what the no-op gateway used to do, and
 * what the bulk engine relies on to isolate per-item faults.
 */

/** Which shared channel a message targets. */
enum class ChannelKind { COURT, JUDGE, SPECTATOR }

/** A guild member as the dashboard pickers, snapshot, and assignment need it (a plain projection of a
 *  JDA [Member] — never a JDA type leaks into the web/snapshot layer). */
data class GuildMember(
    val id: Long,
    val name: String,
    val displayName: String,
    val avatarUrl: String?,
    val bot: Boolean = false,
    val owner: Boolean = false,
    val spectator: Boolean = false,
)

// ---- lookups ----
fun JDA.guildOrNull(guildId: Long): Guild? = getGuildById(guildId)

fun Guild.memberOrNull(memberId: Long): Member? =
    getMemberById(memberId) ?: runCatching { retrieveMemberById(memberId).complete() }.getOrNull()

fun JDA.memberOrNull(guildId: Long, memberId: Long): Member? = guildOrNull(guildId)?.memberOrNull(memberId)

private fun JDA.textChannel(channelId: Long): TextChannel? =
    channelId.takeIf { it != 0L }?.let { getTextChannelById(it) }

fun JDA.channelOf(session: GameSession, kind: ChannelKind): TextChannel? {
    val ids = session.discordIds
    val channelId = when (kind) {
        ChannelKind.COURT -> ids.courtTextChannelId
        ChannelKind.JUDGE -> ids.judgeTextChannelId
        ChannelKind.SPECTATOR -> ids.spectatorTextChannelId
    }
    return textChannel(channelId)
}

fun JDA.seatChannel(session: GameSession, seatNumber: Int): TextChannel? =
    session.seat(seatNumber)?.let { textChannel(it.channelId) }

// ---- queries ----
fun JDA.isOwner(guildId: Long, memberId: Long): Boolean = guildOrNull(guildId)?.ownerIdLong == memberId

/** Whether [memberId] is a judge of this guild (the owner, the judge role, or a server admin) — judges
 *  may drive night/day prompts on a seat's behalf from that seat's channel. */
fun JDA.isJudge(session: GameSession, memberId: Long): Boolean {
    val g = guildOrNull(session.guildId) ?: return false
    if (g.ownerIdLong == memberId) return true
    val m = g.memberOrNull(memberId) ?: return false
    val judgeRoleId = session.discordIds.judgeRoleId
    return m.hasPermission(Permission.ADMINISTRATOR) || (judgeRoleId != 0L && m.roles.any { it.idLong == judgeRoleId })
}

/** Role-hierarchy preflight: can the bot actually act on this member? */
fun JDA.canManage(guildId: Long, memberId: Long): Boolean {
    val g = guildOrNull(guildId) ?: return false
    val m = g.memberOrNull(memberId) ?: return false
    return g.selfMember.canInteract(m)
}

fun JDA.guildName(guildId: Long): String? = guildOrNull(guildId)?.name
fun JDA.guildIconUrl(guildId: Long): String? = guildOrNull(guildId)?.iconUrl

fun JDA.listMembers(session: GameSession): List<GuildMember> {
    val spectatorRoleId = session.discordIds.spectatorRoleId
    return guildOrNull(session.guildId)?.members.orEmpty().map {
        GuildMember(
            it.idLong,
            it.user.name,
            it.effectiveName,
            it.user.effectiveAvatarUrl,
            it.user.isBot,
            it.isOwner,
            spectator = spectatorRoleId != 0L && it.roles.any { r -> r.idLong == spectatorRoleId },
        )
    }
}

// ---- per-member mutations ----
/** `await = true` blocks until Discord confirms, so the assignment bulk phase can apply role then
 *  nickname strictly one player at a time (FEATURES §10.2); otherwise fire-and-forget `queue()`. */
private fun RestAction<*>.dispatch(await: Boolean) = if (await) complete() else queue()

fun JDA.grantSeatRole(session: GameSession, memberId: Long, seatNumber: Int, await: Boolean = false) {
    val g = guildOrNull(session.guildId) ?: return
    val seat = session.seat(seatNumber) ?: return
    val role = g.getRoleById(seat.roleId) ?: return
    val m = g.memberOrNull(memberId) ?: return
    if (g.selfMember.canInteract(role)) g.addRoleToMember(m, role).dispatch(await)
}

/** Best-effort nickname sync (the `[死人] 玩家NN [警長]` grammar comes from `NicknameService`); owner +
 *  no-op + hierarchy guards are folded in, so callers no longer need a `canManage` preflight. */
fun JDA.syncNickname(session: GameSession, seat: Seat, nickname: String) {
    val memberId = seat.memberId ?: return
    setNickname(session, memberId, nickname)
}

fun JDA.setNickname(session: GameSession, memberId: Long, nickname: String, await: Boolean = false) {
    val g = guildOrNull(session.guildId) ?: return
    val m = g.memberOrNull(memberId) ?: return
    if (m.isOwner) return // bots can never rename the owner
    if (!g.selfMember.canInteract(m)) return
    if (m.nickname == nickname) return // skip no-op updates
    m.modifyNickname(nickname).dispatch(await)
}

fun JDA.clearNickname(session: GameSession, memberId: Long) {
    val m = memberOrNull(session.guildId, memberId) ?: return
    if (!m.isOwner) m.modifyNickname(null).complete()
}

fun JDA.removeSeatRoles(session: GameSession, memberId: Long) {
    val g = guildOrNull(session.guildId) ?: return
    val m = g.memberOrNull(memberId) ?: return
    // Strip any seat roles the member still holds (seat.roleId survives reset; only the seat→member
    // binding is cleared). Each removal blocks (complete) so it's done one at a time.
    val seatRoleIds = session.seats.mapNotNull { it.roleId.takeIf { id -> id != 0L } }.toSet()
    m.roles
        .filter { it.idLong in seatRoleIds && g.selfMember.canInteract(it) }
        .forEach { g.removeRoleFromMember(m, it).complete() }
}

fun JDA.grantSpectatorRole(session: GameSession, memberId: Long) {
    val g = guildOrNull(session.guildId) ?: return
    val role = session.discordIds.spectatorRoleId.takeIf { it != 0L }?.let { g.getRoleById(it) } ?: return
    val m = g.memberOrNull(memberId) ?: return
    if (g.selfMember.canInteract(role)) g.addRoleToMember(m, role).queue()
}

fun JDA.grantJudgeRole(session: GameSession, memberId: Long) {
    val g = guildOrNull(session.guildId) ?: return
    val role = session.discordIds.judgeRoleId.takeIf { it != 0L }?.let { g.getRoleById(it) } ?: return
    val m = g.memberOrNull(memberId) ?: return
    if (g.selfMember.canInteract(role)) g.addRoleToMember(m, role).queue()
}

fun JDA.revokeJudgeRole(session: GameSession, memberId: Long) {
    val g = guildOrNull(session.guildId) ?: return
    val role = session.discordIds.judgeRoleId.takeIf { it != 0L }?.let { g.getRoleById(it) } ?: return
    val m = g.memberOrNull(memberId) ?: return
    if (g.selfMember.canInteract(role)) g.removeRoleFromMember(m, role).queue()
}

// ---- messaging ----
fun JDA.sendChannelMessage(session: GameSession, kind: ChannelKind, text: String) {
    channelOf(session, kind)?.sendMessage(text)?.queue()
}

fun JDA.sendChannelEmbed(session: GameSession, kind: ChannelKind, embed: EmbedBuilder) {
    channelOf(session, kind)?.sendMessageEmbeds(embed.build())?.queue()
}

fun JDA.sendSeatMessage(session: GameSession, seatNumber: Int, text: String) {
    seatChannel(session, seatNumber)?.sendMessage(text)?.queue()
}

fun JDA.sendSeatEmbed(session: GameSession, seatNumber: Int, embed: EmbedBuilder, buttons: List<Button> = emptyList()) {
    val channel = seatChannel(session, seatNumber) ?: return
    val message = channel.sendMessageEmbeds(embed.build())
    if (buttons.isNotEmpty()) message.addComponents(ActionRow.of(buttons))
    message.queue()
}

/** Post a court prompt with [buttons] (chunked into rows of 5). */
fun JDA.sendCourtButtons(session: GameSession, text: String, buttons: List<Button>) {
    val channel = channelOf(session, ChannelKind.COURT) ?: return
    val rows = buttons.chunked(5).map { ActionRow.of(it) }
    channel.sendMessage(text).apply { if (rows.isNotEmpty()) addComponents(rows) }.queue()
}

/** Open every channel for @everyone to view (post-game), without lifting send restrictions. */
fun JDA.revealAllChannels(guildId: Long) {
    val g = guildOrNull(guildId) ?: return
    val pub = g.publicRole
    g.channels.filterIsInstance<IPermissionContainer>().forEach { ch ->
        runCatching { ch.upsertPermissionOverride(pub).grant(Permission.VIEW_CHANNEL).queue() }
    }
}

// ---- voice ----
fun JDA.muteAll(guildId: Long) = setMuteAll(guildId, true)
fun JDA.unmuteAll(guildId: Long) = setMuteAll(guildId, false)

private fun JDA.setMuteAll(guildId: Long, muted: Boolean) {
    val g = guildOrNull(guildId) ?: return
    g.voiceChannels.flatMap { it.members }
        .filter { !it.user.isBot && !it.isOwner && g.selfMember.canInteract(it) }
        .forEach { it.mute(muted).queue() }
}

fun JDA.muteMember(guildId: Long, memberId: Long, muted: Boolean) {
    val m = memberOrNull(guildId, memberId) ?: return
    if (!m.isOwner && m.voiceState?.channel != null) m.mute(muted).queue()
}

// ---- night prompts ----
/** Post a single-select night-action prompt in [seatNumber]'s private channel. [customId] carries the
 *  ability; [options] are the targetable seats; [extraValues] are non-seat choices; a Skip choice is
 *  added when [allowSkip]. */
fun JDA.sendSelectMenu(
    session: GameSession,
    seatNumber: Int,
    customId: String,
    prompt: String,
    options: List<SeatOption>,
    extraValues: List<Pair<String, String>> = emptyList(),
    allowSkip: Boolean = true,
    maxValues: Int = 1,
) {
    val channel = seatChannel(session, seatNumber) ?: return
    val menu = StringSelectMenu.create(customId).apply {
        extraValues.forEach { (value, label) -> addOption(label, value) }
        options.take(23).forEach { addOption(it.label, it.seat.toString()) }
        if (allowSkip) addOption("不行動", InteractionIds.SKIP)
        setRequiredRange(1, maxValues.coerceAtLeast(1))
    }.build()
    channel.sendMessage(prompt).addComponents(ActionRow.of(menu)).queue()
}

/** Post wolf-kill vote buttons (one per option, plus skip) into each wolf participant's channel. */
fun JDA.sendWolfVote(session: GameSession, voterSeats: List<Int>, options: List<SeatOption>) {
    val g = guildOrNull(session.guildId) ?: return
    val buttons = options.map { Button.danger("${InteractionIds.WOLF_VOTE}:${it.seat}", it.label) } +
        Button.secondary("${InteractionIds.WOLF_VOTE}:${InteractionIds.SKIP}", "本夜不刀")
    val rows = buttons.chunked(5).map { ActionRow.of(it) }
    voterSeats.forEach { seatNumber ->
        val seat = session.seat(seatNumber) ?: return@forEach
        seat.channelId.takeIf { it != 0L }?.let { g.getTextChannelById(it) }
            ?.sendMessage("🐺 狼人請投票決定今晚刀口：")?.addComponents(rows)?.queue()
    }
}

/** Post the witch's 解藥 / 毒藥 / skip buttons into [seatNumber]'s channel (each opens its own target
 *  menu via [sendSelectMenu]; the choice stays editable until a target is committed). */
fun JDA.sendWitchChoice(session: GameSession, seatNumber: Int, prompt: String) {
    val channel = seatChannel(session, seatNumber) ?: return
    val buttons = listOf(
        Button.success(InteractionIds.WITCH_USE_CURE, "使用解藥"),
        Button.danger(InteractionIds.WITCH_USE_POISON, "使用毒藥"),
        Button.secondary(InteractionIds.WITCH_SKIP, "不使用藥水"),
    )
    channel.sendMessage(prompt).addComponents(ActionRow.of(buttons)).queue()
}
