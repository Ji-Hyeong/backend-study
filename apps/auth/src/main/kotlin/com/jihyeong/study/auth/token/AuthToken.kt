package com.jihyeong.study.auth.token

enum class UserRole {
	USER,
	ADMIN,
}

enum class TokenType {
	ACCESS,
	REFRESH,
}

data class JwtClaims(
	val userId: Long,
	val role: UserRole,
	val tokenId: String,
	val tokenType: TokenType,
)

data class TokenPair(
	val accessToken: String,
	val refreshToken: String,
)

data class AuthenticatedUser(
	val userId: Long,
	val role: UserRole,
)
