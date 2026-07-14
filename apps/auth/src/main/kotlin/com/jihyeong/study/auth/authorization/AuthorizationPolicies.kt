package com.jihyeong.study.auth.authorization

/** ReBAC은 사용자와 리소스 사이의 관계를 권한 판단의 입력으로 사용한다. */
enum class ProjectRelation {
	OWNER,
	EDITOR,
	VIEWER,
}

class ProjectRelationshipPolicy(
	private val relations: Map<Long, Map<Long, ProjectRelation>>,
) {
	fun canRead(projectId: Long, userId: Long): Boolean = relations[projectId]?.get(userId) != null

	fun canEdit(projectId: Long, userId: Long): Boolean = when (relations[projectId]?.get(userId)) {
		ProjectRelation.OWNER,
		ProjectRelation.EDITOR,
		-> true
		else -> false
	}
}

enum class Clearance(val rank: Int) {
	INTERNAL(1),
	CONFIDENTIAL(2),
	RESTRICTED(3),
}

data class SubjectAttributes(
	val department: String,
	val clearance: Clearance,
	val active: Boolean,
)

data class DocumentAttributes(
	val department: String,
	val requiredClearance: Clearance,
	val public: Boolean,
)

data class AccessEnvironment(
	val corporateNetwork: Boolean,
)

/**
 * ABAC은 역할 대신 주체·리소스·환경 속성을 모두 평가한다.
 * 여기서는 재직 상태, 부서, 보안 등급, 사내망 여부를 독립 입력으로 둔다.
 */
class DocumentAttributePolicy {
	fun canRead(
		subject: SubjectAttributes,
		document: DocumentAttributes,
		environment: AccessEnvironment,
	): Boolean {
		if (!subject.active || !environment.corporateNetwork) return false
		if (document.public) return true
		return subject.department == document.department && subject.clearance.rank >= document.requiredClearance.rank
	}
}
