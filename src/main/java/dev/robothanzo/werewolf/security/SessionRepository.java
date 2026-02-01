package dev.robothanzo.werewolf.security;

import dev.robothanzo.werewolf.database.documents.Session;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SessionRepository extends MongoRepository<Session, String> {
    Optional<Session> findByGuildId(long guildId);

    void deleteByGuildId(long guildId);
}
