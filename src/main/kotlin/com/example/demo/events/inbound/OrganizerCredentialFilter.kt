package com.example.demo.events.inbound

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ServletRequestPathUtils
import org.springframework.web.util.pattern.PathPatternParser
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Component
class OrganizerCredentialFilter(
	@Value("\${organizer.api-key:}") private val configuredCredential: String,
) : OncePerRequestFilter() {

	override fun shouldNotFilter(request: HttpServletRequest): Boolean {
		// Match decoded segments and matrix parameters the same way as Spring MVC.
		val path = ServletRequestPathUtils.parseAndCache(request).pathWithinApplication()
		return !ORGANIZER_PATH.matches(path)
	}

	override fun doFilterInternal(
		request: HttpServletRequest,
		response: HttpServletResponse,
		filterChain: FilterChain,
	) {
		val suppliedCredential = bearerCredential(request.getHeader(HttpHeaders.AUTHORIZATION))
		if (configuredCredential.isBlank() || suppliedCredential.isNullOrBlank() || !matches(suppliedCredential)) {
			response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
			return
		}

		filterChain.doFilter(request, response)
	}

	private fun matches(suppliedCredential: String): Boolean = MessageDigest.isEqual(
		configuredCredential.toByteArray(StandardCharsets.UTF_8),
		suppliedCredential.toByteArray(StandardCharsets.UTF_8),
	)

	private fun bearerCredential(authorization: String?): String? {
		if (authorization == null || !authorization.regionMatches(0, BEARER_PREFIX, 0, BEARER_PREFIX.length, ignoreCase = true)) {
			return null
		}
		return authorization.substring(BEARER_PREFIX.length)
	}

	private companion object {
		val ORGANIZER_PATH = PathPatternParser.defaultInstance.parse("/api/organizer/**")
		const val BEARER_PREFIX = "Bearer "
	}
}
