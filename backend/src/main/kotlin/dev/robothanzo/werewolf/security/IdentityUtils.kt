package dev.robothanzo.werewolf.security

import org.springframework.stereotype.Component

class ActivePlayerLockoutException : RuntimeException("Active player lockout")

/**
 * The bean the method-security guards reference: `@PreAuthorize("@identityUtils.canManage(#guildId)")`.
 * Resolves the current session user and delegates to [DashboardRoleService].
 */
@Component("identityUtils")
class IdentityUtils(
    private val currentUser: CurrentUser,
    private val roleService: DashboardRoleService,
) {

    fun canManage(guildId: String): Boolean {
        val userId = currentUser.userId() ?: return false
        val gid = guildId.toLongOrNull() ?: return false
        if (roleService.isActivePlayer(gid, userId)) {
            throw ActivePlayerLockoutException()
        }
        return roleService.canManage(gid, userId)
    }

    fun canView(guildId: String): Boolean {
        val userId = currentUser.userId() ?: return false
        val gid = guildId.toLongOrNull() ?: return false
        if (roleService.isActivePlayer(gid, userId)) {
            throw ActivePlayerLockoutException()
        }
        return roleService.canView(gid, userId)
    }
}
