package dev.robothanzo.werewolf.controller

import dev.robothanzo.werewolf.controller.dto.RoleDto
import dev.robothanzo.werewolf.controller.dto.RolesResponse
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import dev.robothanzo.werewolf.i18n.Msg
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@RestController
@RequestMapping("/api/roles")
@Tag(name = "Roles", description = "The canonical identity registry (drives pickers and rendering)")
class RolesController(private val roles: RoleRegistry, private val msg: Msg) {

    @Operation(summary = "List roles", description = "Every registered identity with its localized name and faction.")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "OK")])
    @GetMapping
    fun list(): ResponseEntity<RolesResponse> {
        val dtos = roles.all.map { RoleDto(it.id, msg.msg(it.nameKey), it.faction.name) }
        return ResponseEntity.ok(RolesResponse(dtos))
    }
}
