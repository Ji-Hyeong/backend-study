package com.jihyeong.study.auth.refresh

import com.jihyeong.study.auth.support.AuthStudyLogger
import com.jihyeong.study.auth.token.JwtTokenProvider
import com.jihyeong.study.auth.token.TokenPair
import com.jihyeong.study.auth.token.UserRole
import org.springframework.stereotype.Service
import java.util.UUID

class RefreshTokenReuseException(userId: Long) : IllegalStateException("이미 사용한 refresh token이 재사용되었습니다. userId=$userId")

class RefreshTokenRevokedException(userId: Long) : IllegalStateException("폐기된 refresh token입니다. userId=$userId")

class RefreshTokenNotFoundException(userId: Long) : IllegalArgumentException("유효한 refresh token을 찾을 수 없습니다. userId=$userId")

@Service
class AuthService(
	private val jwtTokenProvider: JwtTokenProvider,
	private val refreshTokenStore: RefreshTokenStore,
) {

	fun login(userId: Long, role: UserRole): TokenPair {
		AuthStudyLogger.state("자격 증명 검증 완료 후 토큰 발급 시작: userId={}, role={}", userId, role)
		return issueTokenPair(userId, role)
	}

	fun refresh(refreshToken: String): TokenPair {
		val claims = jwtTokenProvider.verifyRefreshToken(refreshToken)
		AuthStudyLogger.state("refresh 요청 수신: userId={}, tokenId={}", claims.userId, claims.tokenId)
		val nextRefreshTokenId = UUID.randomUUID().toString()
		val nextTokenPair = TokenPair(
			accessToken = jwtTokenProvider.createAccessToken(claims.userId, claims.role),
			refreshToken = jwtTokenProvider.createRefreshToken(claims.userId, claims.role, nextRefreshTokenId),
		)

		return when (
			refreshTokenStore.rotate(
				userId = claims.userId,
				previousTokenId = claims.tokenId,
				nextTokenId = nextRefreshTokenId,
				ttl = jwtTokenProvider.refreshTokenTtl(),
			)
		) {
			RefreshTokenConsumeResult.CONSUMED -> {
				AuthStudyLogger.state("refresh token 회전 성공, 새 토큰 쌍 발급: userId={}, nextTokenId={}", claims.userId, nextRefreshTokenId)
				nextTokenPair
			}
			RefreshTokenConsumeResult.REUSED -> {
				// rotate 내부에서 새 token을 만들기 전에 해당 사용자의 모든 refresh token을 폐기했습니다.
				AuthStudyLogger.state("refresh token 재사용 감지, 사용자 세션 전체 폐기: userId={}", claims.userId)
				throw RefreshTokenReuseException(claims.userId)
			}
			RefreshTokenConsumeResult.REVOKED -> throw RefreshTokenRevokedException(claims.userId)
			RefreshTokenConsumeResult.NOT_FOUND -> throw RefreshTokenNotFoundException(claims.userId)
		}
	}

	private fun issueTokenPair(userId: Long, role: UserRole): TokenPair {
		val refreshTokenId = UUID.randomUUID().toString()
		val accessToken = jwtTokenProvider.createAccessToken(userId, role)
		val refreshToken = jwtTokenProvider.createRefreshToken(userId, role, refreshTokenId)
		refreshTokenStore.create(userId, refreshTokenId, jwtTokenProvider.refreshTokenTtl())
		AuthStudyLogger.state("access/refresh token 발급 완료: userId={}, refreshTokenId={}", userId, refreshTokenId)
		return TokenPair(accessToken, refreshToken)
	}
}
