package com.jihyeong.study.auth.security

import com.jihyeong.study.auth.token.AuthenticatedUser
import com.jihyeong.study.auth.token.UserRole
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class AuthStudyController {

	@GetMapping("/public/ping")
	fun ping(): Map<String, String> = mapOf("result" to "public")

	@GetMapping("/me")
	fun me(@AuthenticationPrincipal user: AuthenticatedUser): Map<String, Any> {
		return mapOf("userId" to user.userId, "role" to user.role.name)
	}

	@GetMapping("/admin/reports")
	@PreAuthorize("hasRole('ADMIN')")
	fun adminReports(): Map<String, String> = mapOf("result" to "admin report")

	@GetMapping("/orders/{ownerId}")
	fun order(
		@PathVariable ownerId: Long,
		@AuthenticationPrincipal user: AuthenticatedUser,
	): Map<String, Any> {
		if (user.role != UserRole.ADMIN && user.userId != ownerId) {
			throw OrderOwnerAccessDeniedException(ownerId, user.userId)
		}
		return mapOf("ownerId" to ownerId, "requestedBy" to user.userId)
	}
}

@ResponseStatus(HttpStatus.FORBIDDEN)
class OrderOwnerAccessDeniedException(ownerId: Long, requesterId: Long) : AccessDeniedException(
	"주문 소유자가 아닙니다. ownerId=$ownerId, requesterId=$requesterId",
)
