package dev.robothanzo.werewolf.controller

import dev.robothanzo.werewolf.controller.dto.ApiResponse
import dev.robothanzo.werewolf.game.assign.AssignmentException
import dev.robothanzo.werewolf.i18n.Msg
import dev.robothanzo.werewolf.security.ActivePlayerLockoutException
import dev.robothanzo.werewolf.service.SessionNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Maps domain exceptions to localized [ApiResponse] errors so the dashboard shows actionable zh-TW. */
@RestControllerAdvice
class GlobalExceptionHandler(private val msg: Msg) {

    @ExceptionHandler(SessionNotFoundException::class)
    fun handleNotFound(e: SessionNotFoundException): ResponseEntity<ApiResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(msg.msg("error.session_not_found")))

    @ExceptionHandler(AssignmentException::class)
    fun handleAssignment(e: AssignmentException): ResponseEntity<ApiResponse> =
        ResponseEntity.badRequest().body(ApiResponse.error(msg.msg(e.messageKey, *e.args.toTypedArray())))

    @ExceptionHandler(ActivePlayerLockoutException::class)
    fun handleLockout(e: ActivePlayerLockoutException): ResponseEntity<ApiResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(msg.msg("error.player_locked_out")))

    @ExceptionHandler(AccessDeniedException::class)
    fun handleDenied(e: AccessDeniedException): ResponseEntity<ApiResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(msg.msg("error.not_authorized")))

    @ExceptionHandler(IllegalArgumentException::class, IllegalStateException::class)
    fun handleBadRequest(e: RuntimeException): ResponseEntity<ApiResponse> =
        ResponseEntity.badRequest().body(ApiResponse.error(e.message ?: "錯誤"))
}
