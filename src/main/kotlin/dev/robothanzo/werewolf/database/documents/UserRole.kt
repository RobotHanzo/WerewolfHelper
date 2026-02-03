package dev.robothanzo.werewolf.database.documents

enum class UserRole(val description: String) {
    JUDGE("法官"),
    SPECTATOR("觀眾"),
    PENDING("待定"),
    BLOCKED("已封鎖");

    fun isPrivileged(): Boolean {
        return this == JUDGE || this == SPECTATOR
    }

    companion object {
        fun fromString(role: String?): UserRole {
            if (role == null) return PENDING
            return try {
                valueOf(role.uppercase())
            } catch (e: IllegalArgumentException) {
                PENDING
            }
        }
    }
}
