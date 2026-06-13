package dev.robothanzo.werewolf.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * Reads the authenticated Discord user from the Mongo-backed HTTP session. The OAuth callback stores
 * the user id (and access token) on the session after the ~500 ms commit delay (FEATURES §10.6).
 */
@Component
class CurrentUser {

    private fun request(): HttpServletRequest? =
        (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request

    fun userId(): Long? = (request()?.getSession(false)?.getAttribute(ATTR_USER_ID) as? String)?.toLongOrNull()

    fun username(): String? = request()?.getSession(false)?.getAttribute(ATTR_USERNAME) as? String

    fun avatar(): String? = request()?.getSession(false)?.getAttribute(ATTR_AVATAR) as? String

    fun login(userId: Long, username: String, avatar: String?) {
        val session = request()?.getSession(true) ?: return
        // Stored as String, not Long: the Mongo session converter's security ObjectMapper whitelists
        // String but rejects java.lang.Long for polymorphic deserialization (InvalidTypeIdException).
        session.setAttribute(ATTR_USER_ID, userId.toString())
        session.setAttribute(ATTR_USERNAME, username)
        session.setAttribute(ATTR_AVATAR, avatar)
    }

    fun logout() {
        request()?.getSession(false)?.invalidate()
    }

    companion object {
        const val ATTR_USER_ID = "wh.userId"
        const val ATTR_USERNAME = "wh.username"
        const val ATTR_AVATAR = "wh.avatar"
    }
}
