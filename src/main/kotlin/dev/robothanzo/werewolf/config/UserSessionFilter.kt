package dev.robothanzo.werewolf.config

import dev.robothanzo.werewolf.database.documents.AuthSession
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException

class UserSessionFilter : OncePerRequestFilter() {
    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val session = request.getSession(false)
        if (session != null) {
            val userObj = session.getAttribute("user")
            if (userObj is AuthSession) {
                val authorities: MutableList<SimpleGrantedAuthority> = ArrayList()
                authorities.add(SimpleGrantedAuthority("ROLE_USER"))
                if (userObj.role != null) {
                    authorities.add(SimpleGrantedAuthority("ROLE_" + userObj.role!!.name))
                }
                val auth = UsernamePasswordAuthenticationToken(
                    userObj,
                    null,
                    authorities
                )
                SecurityContextHolder.getContext().authentication = auth
            }
        }
        filterChain.doFilter(request, response)
    }
}
