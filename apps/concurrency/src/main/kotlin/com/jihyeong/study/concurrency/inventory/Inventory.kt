package com.jihyeong.study.concurrency.inventory

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "inventories")
class Inventory(
	@Column(nullable = false)
	var quantity: Int,
) {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	val id: Long? = null

	fun decrease() {
		check(quantity > 0) { "재고가 부족합니다." }
		quantity -= 1
	}
}
