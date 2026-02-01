package dev.robothanzo.werewolf.database.documents;

import lombok.Getter;

@Getter
public enum UserRole {
    JUDGE("法官"),
    SPECTATOR("觀眾"),
    PENDING("待定"),
    BLOCKED("已封鎖");

    private final String description;

    UserRole(String description) {
        this.description = description;
    }

    public boolean isPrivileged() {
        return this == JUDGE || this == SPECTATOR;
    }

    public static UserRole fromString(String role) {
        if (role == null)
            return PENDING;
        try {
            return UserRole.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PENDING;
        }
    }
}
