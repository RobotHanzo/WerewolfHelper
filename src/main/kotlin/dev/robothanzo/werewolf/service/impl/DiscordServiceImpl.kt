package dev.robothanzo.werewolf.service.impl

import club.minnced.discord.jdave.interop.JDaveSessionFactory
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import dev.robothanzo.jda.interactions.JDAInteractions
import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.Database
import dev.robothanzo.werewolf.listeners.ButtonListener
import dev.robothanzo.werewolf.listeners.GuildJoinListener
import dev.robothanzo.werewolf.listeners.MemberJoinListener
import dev.robothanzo.werewolf.listeners.MessageListener
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
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.ChunkingFilter
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

@Service
class DiscordServiceImpl : DiscordService {
    private val log = LoggerFactory.getLogger(DiscordServiceImpl::class.java)
    override var jda: JDA? = null

    @PostConstruct
    fun init() {
        log.info("Initializing Discord Service...")
        try {
            // Ensure Database is init (legacy)
            Database.initDatabase()

            AudioSourceManagers.registerLocalSource(WerewolfApplication.playerManager)

            val token = System.getenv("TOKEN")
            if (token == null || token.isEmpty()) {
                log.error("TOKEN environment variable not set!")
                return
            }

            jda = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
                .setChunkingFilter(ChunkingFilter.ALL)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .enableCache(EnumSet.allOf(CacheFlag::class.java))
                .disableCache(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS)
                .addEventListeners(
                    GuildJoinListener(), MemberJoinListener(), MessageListener(),
                    ButtonListener()
                )
                .setAudioModuleConfig(AudioModuleConfig().withDaveSessionFactory(JDaveSessionFactory()))
                .build()

            JDAInteractions("dev.robothanzo.werewolf.commands").registerInteractions(jda).queue()
            jda!!.awaitReady()
            jda!!.presence.activity = Activity.competing("狼人殺 by Hanzo")
            log.info("JDA Initialized: {}", jda!!.selfUser.asTag)

            // Set legacy static instance for backward compatibility
            WerewolfApplication.jda = jda!!
        } catch (e: Exception) {
            log.error("Failed to initialize JDA", e)
            throw RuntimeException(e)
        }
    }

    override fun getGuild(guildId: Long): Guild? {
        return jda!!.getGuildById(guildId)
    }

    override fun getMember(guildId: Long, userId: String?): Member? {
        val guild = getGuild(guildId) ?: return null
        return guild.getMemberById(userId!!)
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
