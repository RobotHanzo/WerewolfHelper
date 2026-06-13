package dev.robothanzo.werewolf.discord

import dev.robothanzo.werewolf.domain.GameSession
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.slf4j.LoggerFactory

/**
 * Live JDA-backed gateway. Connects with the configured token and implements the per-member
 * mutations and messaging used by the engine. The full server-provisioning sequence
 * (role/channel creation with exact permission overwrites, wolf-chat webhooks, audio playback) is
 * the remaining integration surface and is logged rather than half-applied — never silently break a
 * game with a partial provision.
 *
 * Mutating calls catch and rethrow with a readable reason so the bulk engine can isolate failures.
 */
class JdaDiscordGateway(
    properties: DiscordProperties,
    private val nicknames: NicknameService,
) : DiscordGateway {

    private val log = LoggerFactory.getLogger(javaClass)

    private val jda: JDA = JDABuilder.createLight(
        properties.token,
        GatewayIntent.GUILD_MEMBERS,
        GatewayIntent.GUILD_MESSAGES,
        GatewayIntent.GUILD_VOICE_STATES,
        GatewayIntent.MESSAGE_CONTENT,
    ).enableCache(CacheFlag.VOICE_STATE).build()

    override val available: Boolean = true

    private fun guild(guildId: Long): Guild? = jda.getGuildById(guildId)
    private fun member(guildId: Long, memberId: Long): Member? = guild(guildId)?.getMemberById(memberId)

    override fun listMembers(guildId: Long): List<GuildMember> =
        guild(guildId)?.members.orEmpty().map {
            GuildMember(
                id = it.idLong,
                name = it.user.name,
                displayName = it.effectiveName,
                avatarUrl = it.user.effectiveAvatarUrl,
                bot = it.user.isBot,
                owner = it.isOwner,
            )
        }

    override fun isOwner(guildId: Long, memberId: Long): Boolean = guild(guildId)?.ownerIdLong == memberId

    override fun canInteract(guildId: Long, memberId: Long): Boolean {
        val g = guild(guildId) ?: return false
        val m = member(guildId, memberId) ?: return false
        return g.selfMember.canInteract(m)
    }

    override suspend fun provisionGuild(session: GameSession) =
        log.warn("provisionGuild for {} requires the full JDA provisioning sequence (not yet wired).", session.guildId)

    override suspend fun resizeGuild(session: GameSession, newCount: Int) =
        log.warn("resizeGuild for {} requires the full JDA provisioning sequence (not yet wired).", session.guildId)

    override suspend fun deleteGuild(guildId: Long) {
        guild(guildId)?.leave()?.queue()
    }

    override fun grantSeatRole(guildId: Long, memberId: Long, seatNumber: Int) {
        log.warn("grantSeatRole needs the provisioned seat roles for guild {} (not yet wired).", guildId)
    }

    override fun setNickname(guildId: Long, memberId: Long, nickname: String) {
        val g = guild(guildId) ?: error("guild $guildId not found")
        val m = member(guildId, memberId) ?: error("member $memberId not found")
        if (m.isOwner) return // bots can never rename the owner
        if (!g.selfMember.canInteract(m)) error("權限不足")
        if (m.nickname == nickname) return // skip no-op updates
        m.modifyNickname(nickname).queue()
    }

    override fun grantSpectatorRole(guildId: Long, memberId: Long) {
        log.warn("grantSpectatorRole needs the provisioned spectator role for guild {} (not yet wired).", guildId)
    }

    override fun resetMember(guildId: Long, memberId: Long) {
        val m = member(guildId, memberId) ?: return
        if (!m.isOwner) m.modifyNickname(null).queue()
    }

    override fun sendChannelMessage(guildId: Long, channel: ChannelKind, text: String) {
        log.debug("sendChannelMessage {} requires the provisioned {} channel (not yet wired).", guildId, channel)
    }

    override fun sendSeatMessage(guildId: Long, seatNumber: Int, text: String) {
        log.debug("sendSeatMessage seat {} requires the provisioned seat channel (not yet wired).", seatNumber)
    }

    override fun muteAll(guildId: Long) = setMuteAll(guildId, true)
    override fun unmuteAll(guildId: Long) = setMuteAll(guildId, false)

    private fun setMuteAll(guildId: Long, muted: Boolean) {
        val g = guild(guildId) ?: return
        g.voiceChannels.flatMap { it.members }
            .filter { !it.user.isBot && !it.isOwner && g.selfMember.canInteract(it) }
            .forEach { it.mute(muted).queue() }
    }

    override fun muteMember(guildId: Long, memberId: Long, muted: Boolean) {
        val m = member(guildId, memberId) ?: return
        if (!m.isOwner) m.mute(muted).queue()
    }

    override fun playSound(guildId: Long, cue: SoundCue) =
        log.debug("playSound {} for {} requires the lavaplayer voice pipeline (not yet wired).", cue, guildId)

    override fun relayWolfChat(
        guildId: Long,
        fromSeat: Int,
        authorName: String,
        authorAvatar: String?,
        content: String,
    ) {
        log.debug("relayWolfChat for {} requires one cached webhook per channel (not yet wired).", guildId)
    }
}
