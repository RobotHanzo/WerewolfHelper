package dev.robothanzo.werewolf.service.impl;

import club.minnced.discord.jdave.interop.JDaveSessionFactory;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import dev.robothanzo.jda.interactions.JDAInteractions;
import dev.robothanzo.werewolf.database.Database;
import dev.robothanzo.werewolf.listeners.ButtonListener;
import dev.robothanzo.werewolf.listeners.GuildJoinListener;
import dev.robothanzo.werewolf.listeners.MemberJoinListener;
import dev.robothanzo.werewolf.listeners.MessageListener;
import dev.robothanzo.werewolf.service.DiscordService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.springframework.stereotype.Service;

import java.util.EnumSet;

import static dev.robothanzo.werewolf.WerewolfApplication.playerManager;

@Slf4j
@Service
public class DiscordServiceImpl implements DiscordService {

    private JDA jda;

    @PostConstruct
    public void init() {
        log.info("Initializing Discord Service...");
        try {
            // Ensure Database is init (legacy)
            // Database.initDatabase(); // Spring Boot likely handles DB via Spring Data,
            // but if static utils use it...
            // Ideally replace Database.initDatabase() with Spring Data usage.
            // For now, let's keep it if legacy code depends on Database.database static
            // field.
            Database.initDatabase();

            AudioSourceManagers.registerLocalSource(playerManager);

            String token = System.getenv("TOKEN");
            if (token == null || token.isEmpty()) {
                log.error("TOKEN environment variable not set!");
                return;
            }

            jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
                    .setChunkingFilter(ChunkingFilter.ALL)
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .enableCache(EnumSet.allOf(CacheFlag.class))
                    .disableCache(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS)
                    .addEventListeners(new GuildJoinListener(), new MemberJoinListener(), new MessageListener(),
                            new ButtonListener())
                    .setAudioModuleConfig(new AudioModuleConfig().withDaveSessionFactory(new JDaveSessionFactory()))
                    .build();

            new JDAInteractions("dev.robothanzo.werewolf.commands").registerInteractions(jda).queue();
            jda.awaitReady();
            jda.getPresence().setActivity(Activity.competing("狼人殺 by Hanzo"));
            log.info("JDA Initialized: {}", jda.getSelfUser().getAsTag());

            // Set legacy static instance for backward compatibility
            dev.robothanzo.werewolf.WerewolfApplication.jda = jda;

        } catch (Exception e) {
            log.error("Failed to initialize JDA", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public JDA getJDA() {
        return jda;
    }

    @Override
    public Guild getGuild(long guildId) {
        return jda.getGuildById(guildId);
    }

    @Override
    public Member getMember(long guildId, String userId) {
        Guild guild = getGuild(guildId);
        if (guild == null)
            return null;
        return guild.getMemberById(userId);
    }

    @Override
    public TextChannel getTextChannel(long guildId, long channelId) {
        Guild guild = getGuild(guildId);
        if (guild == null)
            return null;
        return guild.getTextChannelById(channelId);
    }

    @Override
    public VoiceChannel getVoiceChannel(long guildId, long channelId) {
        Guild guild = getGuild(guildId);
        if (guild == null)
            return null;
        return guild.getVoiceChannelById(channelId);
    }
}
