package dev.robothanzo.werewolf.model;

import dev.robothanzo.werewolf.database.documents.Session;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.dv8tion.jda.api.entities.Message;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoliceSession {
    private long guildId;
    private long channelId;
    private Session session;
    @Builder.Default
    private State state = State.NONE;
    @Builder.Default
    private long stageEndTime = 0;
    @Builder.Default
    private Map<Integer, Candidate> candidates = new ConcurrentHashMap<>();
    @Builder.Default
    private Message message = null;

    public enum State {
        NONE, ENROLLMENT, SPEECH, UNENROLLMENT, VOTING, FINISHED;

        public boolean canEnroll() {
            return this == ENROLLMENT;
        }

        public boolean canQuit() {
            return this == ENROLLMENT || this == UNENROLLMENT;
        }
    }
}
