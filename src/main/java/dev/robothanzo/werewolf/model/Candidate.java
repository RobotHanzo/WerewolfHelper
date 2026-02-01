package dev.robothanzo.werewolf.model;

import dev.robothanzo.werewolf.database.documents.Session;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Candidate {
    private Session.Player player;
    @Builder.Default
    private boolean expelPK = false;
    @Builder.Default
    private List<Long> electors = new LinkedList<>();
    @Builder.Default
    private boolean quit = false;

    public static Comparator<Candidate> getComparator() {
        return Comparator.comparingInt(o -> o.getPlayer().getId());
    }

    public static List<Candidate> getWinner(Collection<Candidate> candidates, @Nullable Session.Player police) {
        List<Candidate> winners = new LinkedList<>();
        float winningVotes = 0;
        for (Candidate candidate : candidates) {
            float votes = candidate.getVotes(police);
            if (votes <= 0)
                continue;
            if (votes > winningVotes) {
                winningVotes = votes;
                winners.clear();
                winners.add(candidate);
            } else if (votes == winningVotes) {
                winners.add(candidate);
            }
        }
        return winners;
    }

    public List<String> getElectorsAsMention() {
        List<String> result = new LinkedList<>();
        for (Long elector : electors) {
            result.add("<@!" + elector + ">");
        }
        return result;
    }

    public float getVotes(@Nullable Session.Player police) {
        boolean hasPolice = police != null;
        if (hasPolice)
            hasPolice = electors.contains(police.getUserId());
        return (float) ((electors.size() + (hasPolice ? 0.5 : 0)) * (quit ? 0 : 1));
    }
}
