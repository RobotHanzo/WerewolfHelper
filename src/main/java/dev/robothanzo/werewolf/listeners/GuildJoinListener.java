package dev.robothanzo.werewolf.listeners;

import dev.robothanzo.werewolf.WerewolfApplication;
import dev.robothanzo.werewolf.commands.Server;
import dev.robothanzo.werewolf.database.documents.Session;
import dev.robothanzo.werewolf.utils.SetupHelper;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import static com.mongodb.client.model.Filters.eq;

@Slf4j
public class GuildJoinListener extends ListenerAdapter {
    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        checkAndSetup(event.getGuild());
    }

    @Override
    public void onGuildMemberRoleAdd(@NotNull GuildMemberRoleAddEvent event) {
        if (event.getUser().equals(event.getJDA().getSelfUser())) {
            checkAndSetup(event.getGuild());
        }
    }

    @Override
    public void onGuildLeave(@NotNull GuildLeaveEvent event) {
        log.info("Bot left guild: {}", event.getGuild().getId());
        Session.fetchCollection().deleteOne(eq("guildId", event.getGuild().getIdLong()));
        WerewolfApplication.speechService.interruptSession(event.getGuild().getIdLong());
    }

    private void checkAndSetup(Guild guild) {
        long ownerId = guild.getOwnerIdLong();

        // Remove from pending only if setup starts successfully or we handle it?
        // Better to check existence first.
        if (Server.pendingSetups.containsKey(ownerId)) {
            if (guild.getSelfMember().hasPermission(Permission.ADMINISTRATOR)) {
                Server.PendingSetup setupConfig = Server.pendingSetups.remove(ownerId);
                log.info("Found pending setup for guild owned by {}. Starting setup.", ownerId);
                SetupHelper.setup(guild, setupConfig);
            } else {
                // Try to warn in default channel
                if (guild.getDefaultChannel() != null && guild.getDefaultChannel() instanceof TextChannel
                        && ((TextChannel) guild.getDefaultChannel()).canTalk()) {
                    ((TextChannel) guild.getDefaultChannel()).sendMessage(
                            ":warning: 機器人需要 **管理員 (Administrator)** 權限才能設定伺服器。請授予權限後，設定將會自動開始。").queue();
                }
            }
        }
    }
}
