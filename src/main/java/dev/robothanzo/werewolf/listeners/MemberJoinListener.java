package dev.robothanzo.werewolf.listeners;

import dev.robothanzo.werewolf.WerewolfApplication;
import dev.robothanzo.werewolf.database.documents.Session;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import static com.mongodb.client.model.Filters.eq;

@Slf4j
public class MemberJoinListener extends ListenerAdapter {
    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        Session session = Session.fetchCollection().find(eq("guildId", event.getGuild().getIdLong())).first();
        if (WerewolfApplication.SERVER_CREATORS.contains(event.getMember().getIdLong())) {
            if (session != null && session.getOwner() == event.getUser().getIdLong()) {
                event.getGuild().addRoleToMember(event.getMember(),
                        Objects.requireNonNull(event.getGuild().getRoleById(session.getJudgeRoleId()))).queue();
            }
        }
        if (session != null && session.isHasAssignedRoles()) {
            event.getGuild().addRoleToMember(event.getMember(),
                    Objects.requireNonNull(event.getGuild().getRoleById(session.getSpectatorRoleId()))).queue();
        }
    }
}
