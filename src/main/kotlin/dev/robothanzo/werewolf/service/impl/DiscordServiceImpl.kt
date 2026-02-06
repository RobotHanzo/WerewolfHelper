package dev.robothanzo.werewolf.service.impl

import club.minnced.discord.jdave.interop.JDaveSessionFactory
import dev.robothanzo.jda.interactions.JDAInteractions
import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.service.DiscordService
import jakarta.annotation.PostConstruct
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.audio.AudioModuleConfig
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.ChunkingFilter
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.util.*

@Service
class DiscordServiceImpl(
    private val eventListeners: ObjectProvider<EventListener>
) : DiscordService {
    private val log = LoggerFactory.getLogger(DiscordServiceImpl::class.java)
    override var jda: JDA = JDABuilder.createDefault(System.getenv("TOKEN"))
        .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
        .setChunkingFilter(ChunkingFilter.ALL)
        .setMemberCachePolicy(MemberCachePolicy.ALL)
        .enableCache(EnumSet.allOf(CacheFlag::class.java))
        .disableCache(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS)
        .setAudioModuleConfig(AudioModuleConfig().withDaveSessionFactory(JDaveSessionFactory()))
        .build()

    @PostConstruct
    fun init() {
        log.info("Initializing Discord Service...")
        try {
            // Register all Spring-managed EventListener beans
            eventListeners.forEach { listener ->
                jda.addEventListener(listener)
                log.debug("Registered event listener: ${listener.javaClass.simpleName}")
            }

            // Offload blocking/heavy initialization to a background thread to speed up startup
            Thread {
                try {
                    JDAInteractions("dev.robothanzo.werewolf").registerInteractions(jda).queue()
                    jda.awaitReady()
                    jda.presence.activity = Activity.competing("狼人殺 by Hanzo")
                    log.info("JDA Fully Ready: {}", jda.selfUser.asTag)
                } catch (e: Exception) {
                    log.error("Failed to finish JDA background initialization", e)
                }
            }.start()

            WerewolfApplication.jda = jda
        } catch (e: Exception) {
            log.error("Failed to initialize JDA", e)
            throw RuntimeException(e)
        }
    }

    override fun getGuild(guildId: Long): Guild? {
        return jda.getGuildById(guildId)
    }

    override fun getMember(guildId: Long, userId: String?): Member? {
        val guild = getGuild(guildId) ?: return null
        return userId?.let { guild.getMemberById(it) }
    }

    override fun getTextChannel(guildId: Long, channelId: Long): TextChannel? {
        val guild = getGuild(guildId) ?: return null
        return guild.getTextChannelById(channelId)
    }

    override fun getVoiceChannel(guildId: Long, channelId: Long): VoiceChannel? {
        val guild = getGuild(guildId) ?: return null
        return guild.getVoiceChannelById(channelId)
    }
}
