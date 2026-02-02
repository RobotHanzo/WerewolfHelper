package dev.robothanzo.werewolf.utils;

import dev.robothanzo.werewolf.database.documents.AuthSession;
import dev.robothanzo.werewolf.database.documents.UserRole;
import dev.robothanzo.werewolf.service.DiscordService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class IdentityUtils {

    private final DiscordService discordService;
    
    // Simple cache to reduce Discord API calls - entries expire after they're created
    private static class MembershipCacheEntry {
        final boolean isMember;
        final long timestamp;
        
        MembershipCacheEntry(boolean isMember) {
            this.isMember = isMember;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            // Cache for 5 minutes
            return System.currentTimeMillis() - timestamp > 300_000;
        }
    }
    
    private final Map<String, MembershipCacheEntry> membershipCache = new ConcurrentHashMap<>();

    public Optional<AuthSession> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthSession) {
            return Optional.of((AuthSession) auth.getPrincipal());
        }
        return Optional.empty();
    }

    public boolean hasAccess(long guildId) {
        return getCurrentUser()
                .map(user -> {
                    // First check if the guild ID matches what's stored in the session
                    if (!String.valueOf(guildId).equals(user.getGuildId())) {
                        return false;
                    }
                    
                    // Check cache first
                    String cacheKey = guildId + ":" + user.getUserId();
                    MembershipCacheEntry cached = membershipCache.get(cacheKey);
                    if (cached != null && !cached.isExpired()) {
                        return cached.isMember;
                    }
                    
                    // Cache miss or expired - verify the user is still actually a member of the guild
                    boolean isMember = discordService.getMember(guildId, user.getUserId()) != null;
                    membershipCache.put(cacheKey, new MembershipCacheEntry(isMember));
                    
                    // Clean up expired entries periodically (simple approach)
                    if (membershipCache.size() > 1000) {
                        membershipCache.entrySet().removeIf(e -> e.getValue().isExpired());
                    }
                    
                    return isMember;
                })
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
