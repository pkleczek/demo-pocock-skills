package com.example.demo.events.configuration

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector
import org.springframework.security.web.SecurityFilterChain
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Configuration(proxyBeanMethods = false)
class OrganizerSecurityConfiguration {
	@Bean
	fun securityFilterChain(http: HttpSecurity, organizerTokenIntrospector: OpaqueTokenIntrospector): SecurityFilterChain {
		http
			// Credentials are sent explicitly as bearer tokens, never as browser cookies.
			.csrf { it.disable() }
			.logout { it.disable() }
			.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
			.authorizeHttpRequests {
				it.requestMatchers("/api/organizer/**").hasRole("ORGANIZER")
					.anyRequest().permitAll()
			}
			.oauth2ResourceServer { it.opaqueToken { token -> token.introspector(organizerTokenIntrospector) } }
		return http.build()
	}

	@Bean
	fun organizerTokenIntrospector(
		@Value("\${organizer.api-key:}") configuredCredential: String,
	): OpaqueTokenIntrospector = OpaqueTokenIntrospector { token ->
		if (configuredCredential.isBlank() || !MessageDigest.isEqual(
			configuredCredential.toByteArray(StandardCharsets.UTF_8),
			token.toByteArray(StandardCharsets.UTF_8),
		)) {
			throw BadOpaqueTokenException("Invalid Organizer credential")
		}
		DefaultOAuth2AuthenticatedPrincipal(
			"organizer",
			mapOf("sub" to "organizer"),
			listOf(SimpleGrantedAuthority("ROLE_ORGANIZER")),
		)
	}
}
