package com.jihyeong.study.auth.security

import com.jihyeong.study.auth.support.AuthStudyLogger
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt

class KeycloakJwtAuthenticationConverterTests {

	private val converter = KeycloakJwtAuthenticationConverter()

	@Test
	fun `Keycloak realm role과 client role을 Spring authority로 합친다`() {
		val jwt = Jwt.withTokenValue("keycloak-access-token")
			.header("alg", "none")
			.claim("sub", "keycloak-user-uuid")
			.claim("preferred_username", "study-user")
			.claim("realm_access", mapOf("roles" to listOf("USER", "AUDITOR")))
			.claim("resource_access", mapOf("backend-study-api" to mapOf("roles" to listOf("PROJECT_EDITOR"))))
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(600))
			.build()

		AuthStudyLogger.scenario("Keycloak claim: realm role과 client role의 위치가 다르다")
		AuthStudyLogger.step(1, "converter가 두 claim을 ROLE_ 접두사의 authority로 정규화한다.")
		val authentication = requireNotNull(converter.convert(jwt))

		assertEquals("study-user", authentication.name)
		assertTrue(authentication.authorities.any { it.authority == "ROLE_USER" })
		assertTrue(authentication.authorities.any { it.authority == "ROLE_AUDITOR" })
		assertTrue(authentication.authorities.any { it.authority == "ROLE_PROJECT_EDITOR" })
	}
}
