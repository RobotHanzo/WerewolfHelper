package dev.robothanzo.werewolf.game.roles

import dev.robothanzo.werewolf.game.model.ActionDefinitionId
import dev.robothanzo.werewolf.game.model.Role
import dev.robothanzo.werewolf.game.roles.actions.RoleAction
import org.springframework.stereotype.Component

@Component
class RoleRegistry(
    roles: List<Role>,
    actions: List<RoleAction>
) {
    private val roleMap = roles.associateBy { it.roleName }
    private val actionMap = actions.associateBy { it.actionId }

    fun getRole(roleName: String): Role? = roleMap[roleName]

    fun getAction(actionId: ActionDefinitionId?): RoleAction? = actionMap[actionId]

    fun getAllRoles(): Collection<Role> = roleMap.values

    fun getAllActions(): Collection<RoleAction> = actionMap.values
}
