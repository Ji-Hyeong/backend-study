package com.jihyeong.study.transaction.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "study_orders")
class StudyOrder(
	@Column(nullable = false)
	var productName: String,
) {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	val id: Long? = null

	fun rename(productName: String) {
		this.productName = productName
	}
}
