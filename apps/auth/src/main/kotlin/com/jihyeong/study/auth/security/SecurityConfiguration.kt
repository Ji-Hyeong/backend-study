package com.jihyeong.study.auth.security

import com.jihyeong.study.auth.support.AuthStudyLogger
import com.jihyeong.study.auth.token.AuthenticatedUser
import com.jihyeong.study.auth.token.InvalidJwtException
import com.jihyeong.study.auth.token.JwtTokenProvider
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfiguration(
	private val jwtAuthenticationFilter: JwtAuthenticationFilter,
) {

	@Bean
	fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
		http
			.csrf { it.disable() }
			.httpBasic { it.disable() }
			.formLogin { it.disable() }
			.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
			.authorizeHttpRequests {
				it.requestMatchers("/api/public/**").permitAll()
					.requestMatchers("/api/admin/**").hasRole("ADMIN")
					.anyRequest().authenticated()
			}
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
		return http.build()
	}
}

@Component
class JwtAuthenticationFilter(
	private val jwtTokenProvider: JwtTokenProvider,
) : OncePerRequestFilter() {

	override fun doFilterInternal(
		request: HttpServletRequest,
		response: HttpServletResponse,
		filterChain: FilterChain,
	) {
		val authorization = request.getHeader(HttpHeaders.AUTHORIZATION)
		if (authorization == null) {
			filterChain.doFilter(request, response)
			return
		}
		if (!authorization.startsWith(BEARER_PREFIX)) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Bearer 토큰 형식이 아닙니다.")
			return
		}

		try {
			val claims = jwtTokenProvider.verifyAccessToken(authorization.removePrefix(BEARER_PREFIX))
			val principal = AuthenticatedUser(claims.userId, claims.role)
			val authentication = UsernamePasswordAuthenticationToken(
				principal,
				null,
				listOf(SimpleGrantedAuthority("ROLE_${claims.role.name}")),
			)
			SecurityContextHolder.getContext().authentication = authentication
			AuthStudyLogger.state("SecurityContext 인증 설정: userId={}, role={}", principal.userId, principal.role)
			filterChain.doFilter(request, response)
		} catch (exception: InvalidJwtException) {
			SecurityContextHolder.clearContext()
			AuthStudyLogger.state("JWT 인증 실패: message={}", exception.message ?: "unknown")
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED, exception.message)
		}
	}

	companion object {
		private const val BEARER_PREFIX = "Bearer "
	}
}
