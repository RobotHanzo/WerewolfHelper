package dev.robothanzo.werewolf.listeners;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import dev.robothanzo.werewolf.database.documents.Session;
import dev.robothanzo.werewolf.utils.CmdUtils;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
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
                webhook = Objects.requireNonNull(channel.createWebhook("Werewolf Impersonator").complete());
            }
            webhookCache.put(channel.getIdLong(), WebhookClient.withUrl(webhook.getUrl()));
        }
        return webhookCache.get(channel.getIdLong());
    }

    private boolean isCharacterAlive(Session session, String character) {
        for (Session.Player player : session.fetchAlivePlayers().values()) {
            if (player.getRoles() != null && player.getRoles().contains(character)) {
                // Check if this specific role is NOT dead
                if (player.getDeadRoles() == null || !player.getDeadRoles().contains(character)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean shouldSend(Session.Player player, Session session) {
        assert player.getRoles() != null;
        // Check first role (primary role for speech?) or any active role?
        // Logic seems to assume specific roles.
        // Assuming primary role is relevant, but with soft kill, roles order matters.
        // We should probably check if the role enabling speech is ALIVE.
        // But original code uses getRoles().getFirst().
        // If first role is dead in soft kill (but player alive), should he speak?
        // Probably not as that role.
        // But let's keep getFirst() for now, or check deadRoles.
        // Ideally we check if ANY of the enabling roles are alive.
        // But complicating this might break assumption that first role = current identity.
        // For now, let's just stick to replacement of getPlayers -> fetchAlivePlayers.
        return player.getRoles().getFirst().contains("狼人") ||
                player.getRoles().contains("狼兄") ||
                player.getRoles().getFirst().contains("狼王") ||
                player.getRoles().getFirst().contains("狼美人") ||
                player.getRoles().getFirst().contains("血月使者") ||
                player.getRoles().getFirst().contains("惡靈騎士") ||
                player.getRoles().getFirst().contains("夢魘") ||
                (player.getRoles().contains("狼弟") && !isCharacterAlive(session, "狼兄"));
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        Session session = CmdUtils.getSession(event.getGuild());
        if (session == null) return;
        for (Session.Player player : session.fetchAlivePlayers().values()) {
            if (player.getRoles() != null && !player.getRoles().isEmpty()) {
                if (shouldSend(player, session) && player.getChannelId() == event.getChannel().getIdLong()
                        || event.getChannel().getIdLong() == session.getJudgeTextChannelId()) {
                    WebhookMessage message = new WebhookMessageBuilder()
                            .setContent(event.getMessage().getContentRaw())
                            .setUsername((((event.getChannel().getIdLong() == session.getJudgeTextChannelId()) ? "法官頻道" : player.getNickname())) +
                                    " (" + event.getAuthor().getName() + ")")
                            .setAvatarUrl(event.getAuthor().getAvatarUrl())
                            .build();
                    for (Session.Player p : session.fetchAlivePlayers().values()) {
                        if (shouldSend(p, session) && event.getChannel().getIdLong() != p.getChannelId()) {
                            getWebhookClientOrCreate(Objects.requireNonNull(event.getGuild().getTextChannelById(p.getChannelId()))).send(message);
                        }
                    }
                    if (event.getChannel().getIdLong() != session.getJudgeTextChannelId())
                        getWebhookClientOrCreate(Objects.requireNonNull(event.getGuild().getTextChannelById(session.getJudgeTextChannelId()))).send(message);
                    break;
                }
                if ((player.isJinBaoBao()
                        && player.getChannelId() == event.getChannel().getIdLong())) {
                    WebhookMessage message = new WebhookMessageBuilder()
                            .setContent(event.getMessage().getContentRaw())
                            .setUsername(player.getNickname() + " (" + event.getAuthor().getName() + ")")
                            .setAvatarUrl(event.getAuthor().getAvatarUrl())
                            .build();
                    for (Session.Player p : session.fetchAlivePlayers().values()) {
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
