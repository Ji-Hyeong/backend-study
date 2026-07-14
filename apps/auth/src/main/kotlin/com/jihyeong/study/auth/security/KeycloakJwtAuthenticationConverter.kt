package com.jihyeong.study.auth.security

import com.jihyeong.study.auth.support.AuthStudyLogger
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

/**
 * Keycloak은 realm role과 client role을 서로 다른 claim에 넣는다.
 * API는 두 claim을 ROLE_ authority로 정규화해서 Spring Security의 hasRole 규칙을 그대로 사용한다.
 */
@Component
class KeycloakJwtAuthenticationConverter : Converter<Jwt, AbstractAuthenticationToken> {

	override fun convert(jwt: Jwt): AbstractAuthenticationToken {
		val roles = realmRoles(jwt) + clientRoles(jwt)
		val authorities = roles
			.map { SimpleGrantedAuthority("ROLE_$it") }
			.distinct()

		AuthStudyLogger.state(
			"Keycloak claim을 authority로 변환: subject={}, roles={}",
			jwt.subject,
			roles.sorted(),
		)
		return JwtAuthenticationToken(jwt, authorities, jwt.getClaimAsString("preferred_username") ?: jwt.subject)
	}

	@Suppress("UNCHECKED_CAST")
	private fun realmRoles(jwt: Jwt): Set<String> {
		val realmAccess = jwt.claims["realm_access"] as? Map<String, Any> ?: return emptySet()
		return (realmAccess["roles"] as? Collection<*>)
			?.filterIsInstance<String>()
			?.toSet()
			.orEmpty()
	}

	@Suppress("UNCHECKED_CAST")
	private fun clientRoles(jwt: Jwt): Set<String> {
		val resourceAccess = jwt.claims["resource_access"] as? Map<String, Any> ?: return emptySet()
		return resourceAccess.values
			.filterIsInstance<Map<String, Any>>()
			.flatMap { clientAccess -> (clientAccess["roles"] as? Collection<*>)?.filterIsInstance<String>().orEmpty() }
			.toSet()
	}
}
