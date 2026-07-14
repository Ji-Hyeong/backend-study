package com.jihyeong.study.auth.security

import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class AuthStudyController(
	private val currentUserResolver: CurrentUserResolver,
) {

	@GetMapping("/public/ping")
	fun ping(): Map<String, String> = mapOf("result" to "public")

	@GetMapping("/me")
	fun me(authentication: Authentication): Map<String, Any?> {
		val user = currentUserResolver.resolve(authentication)
		return mapOf("subject" to user.subject, "userId" to user.userId, "roles" to user.roles.sorted())
	}

	@GetMapping("/admin/reports")
	@PreAuthorize("hasRole('ADMIN')")
	fun adminReports(): Map<String, String> = mapOf("result" to "admin report")

	@GetMapping("/orders/{ownerId}")
	fun order(
		@PathVariable ownerId: Long,
		authentication: Authentication,
	): Map<String, Any> {
		val user = currentUserResolver.resolve(authentication)
		if (!user.hasRole("ADMIN") && user.userId != ownerId) {
			throw OrderOwnerAccessDeniedException(ownerId, user.userId)
		}
		return mapOf("ownerId" to ownerId, "requestedBy" to user.subject)
	}
}

@ResponseStatus(HttpStatus.FORBIDDEN)
class OrderOwnerAccessDeniedException(ownerId: Long, requesterId: Long?) : AccessDeniedException(
	"주문 소유자가 아닙니다. ownerId=$ownerId, requesterId=$requesterId",
)
