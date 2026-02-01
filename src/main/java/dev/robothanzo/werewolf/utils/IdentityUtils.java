package dev.robothanzo.werewolf.utils;

import dev.robothanzo.werewolf.database.documents.AuthSession;
import dev.robothanzo.werewolf.database.documents.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class IdentityUtils {

    public Optional<AuthSession> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthSession) {
            return Optional.of((AuthSession) auth.getPrincipal());
        }
        return Optional.empty();
    }

    public boolean hasAccess(long guildId) {
        return getCurrentUser()
                .map(user -> String.valueOf(guildId).equals(user.getGuildId()))
                .orElse(false);
    }

    public boolean hasRole(UserRole role) {
        return getCurrentUser()
                .map(user -> user.getRole() == role)
                .orElse(false);
    }

    public boolean isJudge() {
        return getCurrentUser().map(AuthSession::isJudge).orElse(false);
    }

    public boolean isSpectator() {
        return getCurrentUser().map(AuthSession::isSpectator).orElse(false);
    }

    public boolean isPrivileged() {
        return getCurrentUser().map(AuthSession::isPrivileged).orElse(false);
    }

    public boolean canManage(long guildId) {
        return hasAccess(guildId) && isJudge();
    }

    public boolean canView(long guildId) {
        return hasAccess(guildId) && isPrivileged();
    }
}
