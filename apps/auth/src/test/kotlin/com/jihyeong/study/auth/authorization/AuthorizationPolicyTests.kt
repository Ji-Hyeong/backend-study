package com.jihyeong.study.auth.authorization

import com.jihyeong.study.auth.support.AuthStudyLogger
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class AuthorizationPolicyTests {

	@Test
	fun `ReBAC은 프로젝트와 사용자의 관계로 읽기와 수정을 구분한다`() {
		val policy = ProjectRelationshipPolicy(
			relations = mapOf(
				101L to mapOf(301L to ProjectRelation.OWNER, 302L to ProjectRelation.EDITOR, 303L to ProjectRelation.VIEWER),
			),
		)

		AuthStudyLogger.scenario("ReBAC: 역할이 아니라 프로젝트와 사용자 사이의 관계를 확인한다")
		AuthStudyLogger.step(1, "VIEWER 관계는 프로젝트를 읽을 수 있지만 수정할 수 없다.")
		assertTrue(policy.canRead(projectId = 101L, userId = 303L))
		assertFalse(policy.canEdit(projectId = 101L, userId = 303L))

		AuthStudyLogger.step(2, "EDITOR 관계는 같은 프로젝트를 수정할 수 있다.")
		assertTrue(policy.canEdit(projectId = 101L, userId = 302L))

		AuthStudyLogger.step(3, "관계가 없는 사용자는 읽기도 거부된다.")
		assertFalse(policy.canRead(projectId = 101L, userId = 304L))
	}

	@Test
	fun `ABAC은 주체 리소스 환경 속성을 함께 평가한다`() {
		val policy = DocumentAttributePolicy()
		val platformEngineer = SubjectAttributes("platform", Clearance.CONFIDENTIAL, active = true)
		val platformDocument = DocumentAttributes("platform", Clearance.CONFIDENTIAL, public = false)

		AuthStudyLogger.scenario("ABAC: 부서와 보안 등급이 맞아도 환경 속성이 다르면 거부한다")
		AuthStudyLogger.step(1, "같은 부서, 충분한 등급, 사내망이면 비공개 문서를 읽는다.")
		assertTrue(policy.canRead(platformEngineer, platformDocument, AccessEnvironment(corporateNetwork = true)))

		AuthStudyLogger.step(2, "같은 사용자라도 사외망이면 환경 조건에서 거부된다.")
		assertFalse(policy.canRead(platformEngineer, platformDocument, AccessEnvironment(corporateNetwork = false)))

		AuthStudyLogger.step(3, "부서가 다르면 필요한 보안 등급이 있어도 거부된다.")
		assertFalse(
			policy.canRead(
				SubjectAttributes("product", Clearance.RESTRICTED, active = true),
				platformDocument,
				AccessEnvironment(corporateNetwork = true),
			),
		)
	}
}
