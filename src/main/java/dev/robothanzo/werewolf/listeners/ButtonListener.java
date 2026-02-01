package dev.robothanzo.werewolf.listeners;

import dev.robothanzo.werewolf.commands.Poll;
import dev.robothanzo.werewolf.database.documents.Session;
import dev.robothanzo.werewolf.utils.CmdUtils;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import dev.robothanzo.werewolf.commands.Player;
import dev.robothanzo.werewolf.commands.Speech;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;

public class ButtonListener extends ListenerAdapter {
    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String customId = event.getButton().getCustomId();
        if (customId == null) return;
        
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
            case "rolesList" -> {
                Poll.sendRolesList(event);
                return;
            }
            case "terminateTimer" -> {
                Speech.terminateTimer(event);
                return;
            }
        }

        if (!customId.startsWith("vote")) return;

        event.deferReply(true).queue();

        Session session = CmdUtils.getSession(event);
        if (session == null) return;
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
            if (Poll.Police.candidates.containsKey(Objects.requireNonNull(event.getGuild()).getIdLong())) {
                Map<Integer, Poll.Candidate> candidates = Poll.Police.candidates.get(Objects.requireNonNull(event.getGuild()).getIdLong());
                if (candidates.containsKey(player.getId())) {
                    event.getHook().editOriginal(":x: 你曾經參選過或正在參選，不得投票").queue();
                    return;
                }
                Poll.Candidate electedCandidate = candidates.get(Integer.parseInt(customId.replaceAll("votePolice", "")));
                handleVote(event, candidates, electedCandidate);
            } else {
                event.getHook().editOriginal(":x: 投票已過期").queue();
            }
        }
        if (customId.startsWith("voteExpel")) {
            if (Poll.expelCandidates.containsKey(Objects.requireNonNull(event.getGuild()).getIdLong())) {
                Poll.Candidate votingCandidate = Poll.expelCandidates.get(Objects.requireNonNull(event.getGuild()).getIdLong()).get(player.getId());
                if (votingCandidate != null && votingCandidate.isExpelPK()) {
                    event.getHook().editOriginal(":x: 你正在和別人進行放逐辯論，不得投票").queue();
                    return;
                }
                Map<Integer, Poll.Candidate> candidates = Poll.expelCandidates.get(Objects.requireNonNull(event.getGuild()).getIdLong());
                Poll.Candidate electedCandidate = candidates.get(Integer.parseInt(customId.replaceAll("voteExpel", "")));
                handleVote(event, candidates, electedCandidate);
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

    public void handleVote(@NotNull ButtonInteractionEvent event, Map<Integer, Poll.Candidate> candidates, Poll.Candidate electedCandidate) {
        boolean handled = false;
        for (Poll.Candidate candidate : new LinkedList<>(candidates.values())) {
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
