package dev.robothanzo.werewolf.listeners;

import dev.robothanzo.werewolf.WerewolfApplication;
import dev.robothanzo.werewolf.commands.Player;
import dev.robothanzo.werewolf.commands.Poll;
import dev.robothanzo.werewolf.database.documents.Session;
import dev.robothanzo.werewolf.model.Candidate;
import dev.robothanzo.werewolf.model.PoliceSession;
import dev.robothanzo.werewolf.utils.CmdUtils;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;

public class ButtonListener extends ListenerAdapter {
    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String customId = event.getButton().getCustomId();
        if (customId == null)
            return;

        String[] id = customId.split(":");

        switch (id[0]) {
            case "confirmNewPolice" -> {
                Player.confirmNewPolice(event);
                return;
            }
            case "destroyPolice" -> {
                Player.destroyPolice(event);
                return;
            }
            case "terminateTimer" -> {
                event.deferReply(true).queue();
                if (CmdUtils.isAdmin(event)) {
                    try {
                        WerewolfApplication.speechService.stopTimer(event.getChannel().getIdLong());
                        event.getHook().editOriginal(":white_check_mark:").queue();
                    } catch (Exception e) {
                        event.getHook().editOriginal(":x:").queue();
                    }
                } else {
                    event.getHook().editOriginal(":x:").queue();
                }
                return;
            }
        }

        if (!customId.startsWith("vote"))
            return;

        event.deferReply(true).queue();

        Session session = CmdUtils.getSession(event);
        if (session == null)
            return;
        Session.Player player = null;
        boolean check = false;
        for (Session.Player p : session.fetchAlivePlayers().values()) {
            if (p.getUserId() != null && p.getUserId() == event.getUser().getIdLong()) {
                check = true;
                player = p;
                break;
            }
        }
        if (!check) {
            event.getHook().editOriginal(":x: 只有玩家能投票").queue();
            return;
        }
        if (player.isIdiot() && player.getRoles().isEmpty()) {
            event.getHook().editOriginal(":x: 死掉的白癡不得投票").queue();
            return;
        }
        if (customId.startsWith("votePolice")) {
            long guildId = Objects.requireNonNull(event.getGuild()).getIdLong();
            if (WerewolfApplication.policeService.getSessions().containsKey(guildId)) {
                PoliceSession policeSession = WerewolfApplication.policeService.getSessions().get(guildId);
                Map<Integer, Candidate> candidates = policeSession.getCandidates();

                if (candidates.containsKey(player.getId())) {
                    event.getHook().editOriginal(":x: 你曾經參選過或正在參選，不得投票").queue();
                    return;
                }
                try {
                    int candidateId = Integer.parseInt(customId.replace("votePolice", ""));
                    Candidate electedCandidate = candidates.get(candidateId);
                    if (electedCandidate != null) {
                        handleVote(event, candidates, electedCandidate);
                        // Broadcast update immediately
                        WerewolfApplication.gameSessionService.broadcastSessionUpdate(session);
                    } else {
                        event.getHook().editOriginal(":x: 找不到候選人").queue();
                    }
                } catch (NumberFormatException e) {
                    event.getHook().editOriginal(":x: 無效的投票選項").queue();
                }
            } else {
                event.getHook().editOriginal(":x: 投票已過期").queue();
            }
        }
        if (customId.startsWith("voteExpel")) {
            if (Poll.expelCandidates.containsKey(Objects.requireNonNull(event.getGuild()).getIdLong())) {
                Candidate votingCandidate = Poll.expelCandidates
                        .get(Objects.requireNonNull(event.getGuild()).getIdLong()).get(player.getId());
                if (votingCandidate != null && votingCandidate.isExpelPK()) {
                    event.getHook().editOriginal(":x: 你正在和別人進行放逐辯論，不得投票").queue();
                    return;
                }
                try {
                    Map<Integer, Candidate> candidates = Poll.expelCandidates
                            .get(Objects.requireNonNull(event.getGuild()).getIdLong());
                    int candidateId = Integer.parseInt(customId.replace("voteExpel", ""));
                    Candidate electedCandidate = candidates.get(candidateId);
                    handleVote(event, candidates, electedCandidate);
                    // Broadcast update immediately for expel (user requested realtime voting)
                    WerewolfApplication.gameSessionService.broadcastSessionUpdate(session);
                } catch (NumberFormatException e) {
                    event.getHook().editOriginal(":x: 無效的投票選項").queue();
                }
            } else {
                event.getHook().editOriginal(":x: 投票已過期").queue();
            }
        }
    }

    @Override
    public void onEntitySelectInteraction(@NotNull EntitySelectInteractionEvent event) {
        if ("selectNewPolice".equals(event.getComponentId())) {
            Player.selectNewPolice(event);
        }
    }

    public void handleVote(@NotNull ButtonInteractionEvent event, Map<Integer, Candidate> candidates,
                           Candidate electedCandidate) {
        boolean handled = false;
        for (Candidate candidate : new LinkedList<>(candidates.values())) {
            if (candidate.getElectors().contains(event.getUser().getIdLong())) {
                if (Objects.equals(candidate.getPlayer().getUserId(), electedCandidate.getPlayer().getUserId())) {
                    electedCandidate.getElectors().remove(event.getUser().getIdLong());
                    event.getHook().editOriginal(":white_check_mark: 已改為棄票").queue();
                } else {
                    candidates.get(candidate.getPlayer().getId()).getElectors().remove(event.getUser().getIdLong());
                    electedCandidate.getElectors().add(event.getUser().getIdLong());
                    event.getHook().editOriginal(":white_check_mark: 已將投給玩家" + candidate.getPlayer().getId()
                            + "的票改成投給玩家" + electedCandidate.getPlayer().getId()).queue();
                }
                handled = true;
                break;
            }
        }
        if (!handled) {
            electedCandidate.getElectors().add(event.getUser().getIdLong());
            event.getHook().editOriginal(":white_check_mark: 已投給玩家" + electedCandidate.getPlayer().getId()).queue();
        }
    }
}
