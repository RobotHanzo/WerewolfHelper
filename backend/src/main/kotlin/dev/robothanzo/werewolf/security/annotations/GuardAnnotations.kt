package dev.robothanzo.werewolf.security.annotations

import org.springframework.security.access.prepost.PreAuthorize

/** Method-level guard: the caller must be a JUDGE on the path's `guildId` (mirrors the auto branch). */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("@identityUtils.canManage(#guildId)")
annotation class CanManageGuild

/** Method-level guard: the caller must be at least a SPECTATOR on the path's `guildId`. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("@identityUtils.canView(#guildId)")
annotation class CanViewGuild
