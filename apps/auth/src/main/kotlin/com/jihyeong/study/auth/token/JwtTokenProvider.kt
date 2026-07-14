package com.jihyeong.study.auth.token

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.jihyeong.study.auth.support.AuthStudyLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.Date

class InvalidJwtException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

@Component
class JwtTokenProvider(
	@Value("\${auth.jwt.secret}") secret: String,
) {

	private val algorithm = Algorithm.HMAC256(secret)
	private val verifier = JWT.require(algorithm).build()

	fun createAccessToken(userId: Long, role: UserRole): String {
		return createToken(userId, role, TokenType.ACCESS, ACCESS_TOKEN_TTL)
	}

	fun createRefreshToken(userId: Long, role: UserRole, tokenId: String): String {
		return createToken(userId, role, TokenType.REFRESH, REFRESH_TOKEN_TTL, tokenId)
	}

	fun verifyAccessToken(token: String): JwtClaims = verify(token, TokenType.ACCESS)

	fun verifyRefreshToken(token: String): JwtClaims = verify(token, TokenType.REFRESH)

	fun refreshTokenTtl(): Duration = REFRESH_TOKEN_TTL

	private fun createToken(
		userId: Long,
		role: UserRole,
		tokenType: TokenType,
		ttl: Duration,
		tokenId: String = java.util.UUID.randomUUID().toString(),
	): String {
		val now = Instant.now()
		return JWT.create()
			.withSubject(userId.toString())
			.withJWTId(tokenId)
			.withClaim("role", role.name)
			.withClaim("tokenType", tokenType.name)
			.withIssuedAt(Date.from(now))
			.withExpiresAt(Date.from(now.plus(ttl)))
			.sign(algorithm)
	}

	private fun verify(token: String, expectedType: TokenType): JwtClaims {
		try {
			val decoded = verifier.verify(token)
			val claims = JwtClaims(
				userId = decoded.subject.toLong(),
				role = UserRole.valueOf(decoded.getClaim("role").asString()),
				tokenId = requireNotNull(decoded.id) { "JWT ID가 없습니다." },
				tokenType = TokenType.valueOf(decoded.getClaim("tokenType").asString()),
			)
			check(claims.tokenType == expectedType) { "${expectedType.name} 토큰이 아닙니다." }
			AuthStudyLogger.state("JWT 검증 성공: userId={}, role={}, tokenType={}, tokenId={}", claims.userId, claims.role, claims.tokenType, claims.tokenId)
			return claims
		} catch (exception: JWTVerificationException) {
			throw InvalidJwtException("JWT 서명 또는 만료 검증에 실패했습니다.", exception)
		} catch (exception: IllegalArgumentException) {
			throw InvalidJwtException("JWT claim 형식이 올바르지 않습니다.", exception)
		} catch (exception: IllegalStateException) {
			throw InvalidJwtException(exception.message ?: "JWT token type이 올바르지 않습니다.", exception)
		}
	}

	companion object {
		private val ACCESS_TOKEN_TTL: Duration = Duration.ofMinutes(15)
		private val REFRESH_TOKEN_TTL: Duration = Duration.ofDays(14)
	}
}
