package dev.robothanzo.werewolf.listeners;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import dev.robothanzo.werewolf.database.documents.Session;
import dev.robothanzo.werewolf.utils.CmdUtils;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class MessageListener extends ListenerAdapter {
    public static Map<Long, WebhookClient> webhookCache = new HashMap<>();

    @NotNull
    public static WebhookClient getWebhookClientOrCreate(TextChannel channel) {
        if (!webhookCache.containsKey(channel.getIdLong())) {
            Webhook webhook = null;
            for (Webhook w : Objects.requireNonNull(channel.retrieveWebhooks().complete())) {
                webhook = w;
            }
            if (webhook == null) {
                webhook = Objects.requireNonNull(channel.createWebhook("Discord Werewolf Impersonator").complete());
            }
            webhookCache.put(channel.getIdLong(), WebhookClient.withUrl(webhook.getUrl()));
        }
        return webhookCache.get(channel.getIdLong());
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        Session session = CmdUtils.getSession(event.getGuild());
        if (session == null) return;
        for (Session.Player player : session.getPlayers().values()) {
            if (player.getRoles() != null && player.getRoles().size() > 0) {
                if ((player.getRoles().get(0).contains("狼") || player.getRoles().contains("狼兄"))
                        && player.getChannelId() == event.getChannel().getIdLong()
                        || event.getChannel().getIdLong() == session.getJudgeTextChannelId()) {
                    WebhookMessage message = new WebhookMessageBuilder()
                            .setContent(event.getMessage().getContentRaw())
                            .setUsername("玩家" + player.getId() + " (" + event.getAuthor().getName() + "#" + event.getAuthor().getDiscriminator() + ")")
                            .setAvatarUrl(event.getAuthor().getAvatarUrl())
                            .build();
                    for (Session.Player p : session.getPlayers().values()) {
                        assert p.getRoles() != null;
                        if ((p.getRoles().get(0).contains("狼") || p.getRoles().contains("狼兄")) && event.getChannel().getIdLong() != p.getChannelId()) {
                            getWebhookClientOrCreate(Objects.requireNonNull(event.getGuild().getTextChannelById(p.getChannelId()))).send(message);
                        }
                    }
                    getWebhookClientOrCreate(Objects.requireNonNull(event.getGuild().getTextChannelById(session.getJudgeTextChannelId()))).send(message);
                    break;
                }
                if ((player.isJinBaoBao()
                        && player.getChannelId() == event.getChannel().getIdLong())) {
                    WebhookMessage message = new WebhookMessageBuilder()
                            .setContent(event.getMessage().getContentRaw())
                            .setUsername("玩家" + player.getId() + " (" + event.getAuthor().getName() + "#" + event.getAuthor().getDiscriminator() + ")")
                            .setAvatarUrl(event.getAuthor().getAvatarUrl())
                            .build();
                    for (Session.Player p : session.getPlayers().values()) {
                        if (p.isJinBaoBao() && event.getChannel().getIdLong() != p.getChannelId()) {
                            getWebhookClientOrCreate(Objects.requireNonNull(event.getGuild().getTextChannelById(p.getChannelId()))).send(message);
                        }
                    }
                    break;
                }
            }
        }
    }
}
