package com.jihyeong.study.auth.refresh

import com.jihyeong.study.auth.support.AuthStudyLogger
import com.jihyeong.study.auth.token.JwtTokenProvider
import com.jihyeong.study.auth.token.UserRole
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(properties = ["study.auth.refresh-token-store=memory"])
class AuthServiceTests {

	@Autowired
	private lateinit var authService: AuthService

	@Autowired
	private lateinit var jwtTokenProvider: JwtTokenProvider

	@Test
	fun `refresh token rotation은 기존 토큰을 소비하고 새 토큰 쌍을 발급한다`() {
		AuthStudyLogger.scenario("Refresh Token Rotation: 기존 refresh token은 한 번만 사용할 수 있다")
		AuthStudyLogger.step(1, "로그인으로 access token과 refresh token을 발급한다.")
		val firstPair = authService.login(101L, UserRole.USER)

		AuthStudyLogger.step(2, "refresh token을 소비하고 새로운 token pair를 발급한다.")
		val rotatedPair = authService.refresh(firstPair.refreshToken)
		val accessClaims = jwtTokenProvider.verifyAccessToken(rotatedPair.accessToken)
		val refreshClaims = jwtTokenProvider.verifyRefreshToken(rotatedPair.refreshToken)
		AuthStudyLogger.state("결과: userId={}, accessType={}, refreshType={}", accessClaims.userId, accessClaims.tokenType, refreshClaims.tokenType)

		assertThat(rotatedPair.accessToken).isNotEqualTo(firstPair.accessToken)
		assertThat(rotatedPair.refreshToken).isNotEqualTo(firstPair.refreshToken)
		assertThat(accessClaims.userId).isEqualTo(101L)
		assertThat(refreshClaims.userId).isEqualTo(101L)
	}

	@Test
	fun `이미 소비한 refresh token 재사용은 사용자 세션 전체를 폐기한다`() {
		AuthStudyLogger.scenario("Refresh Token Reuse: 탈취 가능성을 감지하면 새 token pair까지 폐기한다")
		val firstPair = authService.login(102L, UserRole.USER)
		val rotatedPair = authService.refresh(firstPair.refreshToken)

		AuthStudyLogger.step(1, "이미 소비된 이전 refresh token을 다시 사용한다.")
		assertThatThrownBy { authService.refresh(firstPair.refreshToken) }
			.isInstanceOf(RefreshTokenReuseException::class.java)
		AuthStudyLogger.step(2, "재사용 감지로 현재 활성 refresh token도 폐기되었는지 확인한다.")
		assertThatThrownBy { authService.refresh(rotatedPair.refreshToken) }
			.isInstanceOf(RefreshTokenRevokedException::class.java)
	}

	@Test
	fun `동시에 같은 refresh token을 사용하면 하나만 회전에 성공하고 재사용 감지가 세션을 폐기한다`() {
		val firstPair = authService.login(103L, UserRole.USER)
		val ready = CountDownLatch(2)
		val start = CountDownLatch(1)
		val executor = Executors.newFixedThreadPool(2)

		AuthStudyLogger.scenario("동시 Refresh: 원자적 소비로 두 요청이 함께 성공하지 않는다")
		val results = List(2) {
			executor.submit(Callable {
				ready.countDown()
				check(start.await(5, TimeUnit.SECONDS)) { "동시 refresh 시작 대기 시간이 초과되었습니다." }
				runCatching { authService.refresh(firstPair.refreshToken) }
			})
		}

		try {
			check(ready.await(5, TimeUnit.SECONDS)) { "동시 refresh 준비 대기 시간이 초과되었습니다." }
			start.countDown()
			val completed = results.map { it.get(5, TimeUnit.SECONDS) }
			val success = completed.mapNotNull { it.getOrNull() }
			val failures = completed.mapNotNull { it.exceptionOrNull() }
			AuthStudyLogger.state("결과: successCount={}, failureTypes={}", success.size, failures.map { it::class.simpleName })

			assertThat(success).hasSize(1)
			assertThat(failures).anyMatch { it is RefreshTokenReuseException }
			assertThatThrownBy { authService.refresh(success.single().refreshToken) }
				.isInstanceOf(RefreshTokenRevokedException::class.java)
		} finally {
			start.countDown()
			executor.shutdownNow()
		}
	}
}
