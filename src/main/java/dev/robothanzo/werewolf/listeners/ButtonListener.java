package dev.robothanzo.werewolf.listeners;

import dev.robothanzo.werewolf.commands.Poll;
import dev.robothanzo.werewolf.database.documents.Session;
import dev.robothanzo.werewolf.utils.CmdUtils;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ButtonListener extends ListenerAdapter {
    public static Lock voteLock = new ReentrantLock();

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (!Objects.requireNonNull(event.getButton().getId()).startsWith("vote")) return;
        event.deferReply(true).queue();
        if (event.getButton().getId() == null) return;
        Session session = CmdUtils.getSession(event);
        if (session == null) return;
        Session.Player player = null;
        boolean check = false;
        for (Session.Player p : session.getPlayers().values()) {
            if (p.getUserId() != null && p.getUserId() == event.getUser().getIdLong()) {
                check = true;
                player = p;
                break;
            }
        }
        if (event.getButton().getId().startsWith("vote")) {
            if (!check) {
                event.getHook().editOriginal(":x: 只有玩家能投票").queue();
                return;
            }
            if (event.getButton().getId().startsWith("votePolice")) {
                if (Poll.Police.candidates.containsKey(Objects.requireNonNull(event.getGuild()).getIdLong())) {
                    voteLock.lock();
                    Map<Integer, Poll.Candidate> candidates = Poll.Police.candidates.get(Objects.requireNonNull(event.getGuild()).getIdLong());
                    if (candidates.containsKey(player.getId())) {
                        event.getHook().editOriginal(":x: 你曾經參選過或正在參選，不得投票").queue();
                        return;
                    }
                    Poll.Candidate electedCandidate = candidates.get(Integer.parseInt(event.getButton().getId().replaceAll("votePolice", "")));
                    handleVote(event, candidates, electedCandidate);
                } else {
                    event.getHook().editOriginal(":x: 投票已過期").queue();
                }
            }
            if (event.getButton().getId().startsWith("voteExpel")) {
                if (Poll.expelCandidates.containsKey(Objects.requireNonNull(event.getGuild()).getIdLong())) {
                    voteLock.lock();
                    Map<Integer, Poll.Candidate> candidates = Poll.expelCandidates.get(Objects.requireNonNull(event.getGuild()).getIdLong());
                    Poll.Candidate electedCandidate = candidates.get(Integer.parseInt(event.getButton().getId().replaceAll("voteExpel", "")));
                    handleVote(event, candidates, electedCandidate);
                } else {
                    event.getHook().editOriginal(":x: 投票已過期").queue();
                }
            }

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
        voteLock.unlock();
    }
}
