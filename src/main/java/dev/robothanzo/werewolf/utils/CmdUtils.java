package dev.robothanzo.werewolf.utils;

import dev.robothanzo.werewolf.WerewolfHelper;
import dev.robothanzo.werewolf.database.documents.Session;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import static com.mongodb.client.model.Filters.eq;

public class CmdUtils {
    public static final Timer timer = new Timer();

    public static boolean isAdmin(ButtonInteractionEvent event) {
        if (event.getGuild() == null || !Objects.requireNonNull(event.getMember()).getPermissions().contains(Permission.ADMINISTRATOR)) {
            event.getHook().editOriginal(":x: 你沒有管理員").queue();
            return false;
        }
        return true;
    }

    public static boolean isAdmin(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null || !Objects.requireNonNull(event.getMember()).getPermissions().contains(Permission.ADMINISTRATOR)) {
            event.getHook().editOriginal(":x: 你沒有管理員").queue();
            return false;
        }
        return true;
    }

    public static boolean isAuthor(SlashCommandInteractionEvent event) {
        if (event.getUser().getIdLong() != WerewolfHelper.AUTHOR) {
            event.getHook().editOriginal(":x:").queue();
            return false;
        }
        return true;
    }

    @Nullable
    public static Session getSession(@Nullable Guild guild) {
        if (guild == null) return null;
        return Session.fetchCollection().find(eq("guildId", guild.getIdLong())).first();
    }

    @Nullable
    public static Session getSession(ButtonInteractionEvent event) {
        return getSession(Objects.requireNonNull(event.getGuild()));
    }

    @Nullable
    public static Session getSession(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.getHook().editOriginal(":x: 不在伺服器內").queue();
        }
        Session session = getSession(event.getGuild());
        if (session == null) {
            event.getHook().editOriginal(":x: 不在狼人殺伺服器內").queue();
            return null;
        }
        return session;
    }

    public static void schedule(Runnable runnable, long delay) {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                runnable.run();
            }
        }, delay);
    }
}
