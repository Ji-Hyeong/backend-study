package com.jihyeong.study.auth.security

import com.jihyeong.study.auth.refresh.AuthService
import com.jihyeong.study.auth.support.AuthStudyLogger
import com.jihyeong.study.auth.token.UserRole
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(properties = ["study.auth.refresh-token-store=memory"])
@AutoConfigureMockMvc
class AuthAuthorizationTests {

	@Autowired
	private lateinit var authService: AuthService

	@Autowired
	private lateinit var mockMvc: MockMvc

	@Test
	fun `USER access token은 내 정보에는 접근하지만 admin 경로에는 접근하지 못한다`() {
		val userToken = authService.login(201L, UserRole.USER).accessToken

		AuthStudyLogger.scenario("RBAC: USER와 ADMIN 경로를 분리한다")
		AuthStudyLogger.step(1, "USER access token으로 내 정보 경로를 호출한다.")
		mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer $userToken"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.userId").value(201))
			.andExpect(jsonPath("$.role").value("USER"))

		AuthStudyLogger.step(2, "같은 token으로 ADMIN 경로를 호출하면 403을 반환한다.")
		mockMvc.perform(get("/api/admin/reports").header(HttpHeaders.AUTHORIZATION, "Bearer $userToken"))
			.andExpect(status().isForbidden)
	}

	@Test
	fun `주문 조회는 소유자 또는 ADMIN만 허용한다`() {
		val userToken = authService.login(202L, UserRole.USER).accessToken
		val adminToken = authService.login(203L, UserRole.ADMIN).accessToken

		AuthStudyLogger.scenario("Resource Owner: USER는 자신의 주문만, ADMIN은 모든 주문을 조회한다")
		AuthStudyLogger.step(1, "USER가 자신의 주문을 조회한다.")
		mockMvc.perform(get("/api/orders/202").header(HttpHeaders.AUTHORIZATION, "Bearer $userToken"))
			.andExpect(status().isOk)

		AuthStudyLogger.step(2, "USER가 다른 사용자의 주문을 조회하면 403을 반환한다.")
		mockMvc.perform(get("/api/orders/204").header(HttpHeaders.AUTHORIZATION, "Bearer $userToken"))
			.andExpect(status().isForbidden)

		AuthStudyLogger.step(3, "ADMIN은 다른 사용자의 주문도 조회할 수 있다.")
		mockMvc.perform(get("/api/orders/204").header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.ownerId").value(204))
	}

	@Test
	fun `access token 대신 refresh token을 API에 보내면 401을 반환한다`() {
		val tokenPair = authService.login(205L, UserRole.USER)

		AuthStudyLogger.scenario("Token Type: refresh token은 API 인증에 사용할 수 없다")
		mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenPair.refreshToken}"))
			.andExpect(status().isUnauthorized)
	}
}
