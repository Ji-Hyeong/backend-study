package com.jihyeong.study.auth.security

import com.jihyeong.study.auth.token.AuthenticatedUser
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

data class CurrentUser(
	val subject: String,
	val userId: Long?,
	val roles: Set<String>,
) {
	fun hasRole(role: String): Boolean = role in roles
}

/**
 * 자체 JWT의 숫자 userId와 OIDC의 문자열 sub를 분리한다.
 * Keycloak 연동 시 주문 소유자 검증에는 realm import가 넣는 study_user_id custom claim을 사용한다.
 */
@Component
class CurrentUserResolver {

	fun resolve(authentication: Authentication): CurrentUser = when (val principal = authentication.principal) {
		is AuthenticatedUser -> CurrentUser(
			subject = principal.userId.toString(),
			userId = principal.userId,
			roles = setOf(principal.role.name),
		)
		is Jwt -> CurrentUser(
			subject = principal.subject,
			userId = principal.getClaimAsString("study_user_id")?.toLongOrNull(),
			roles = authentication.authorities
				.mapNotNull { authority -> authority.authority.removePrefix("ROLE_").takeIf { it != authority.authority } }
				.toSet(),
		)
		else -> throw AccessDeniedException("지원하지 않는 인증 주체입니다: ${principal::class.simpleName}")
	}
}
