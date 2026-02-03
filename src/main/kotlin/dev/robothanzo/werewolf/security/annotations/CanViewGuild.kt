package dev.robothanzo.werewolf.security.annotations

import org.springframework.security.access.prepost.PreAuthorize

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("@identityUtils.canView(#guildId)")
annotation class CanViewGuild
