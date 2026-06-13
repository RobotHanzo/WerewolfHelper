package dev.robothanzo.werewolf.game.roles.impl

import dev.robothanzo.werewolf.domain.Faction
import dev.robothanzo.werewolf.game.roles.BaseRole
import dev.robothanzo.werewolf.game.roles.RoleIds
import org.springframework.stereotype.Component

/** 平民 — the only villager-faction identity. */
@Component
class VillagerRole : BaseRole(RoleIds.VILLAGER, "role.villager", Faction.VILLAGER)
