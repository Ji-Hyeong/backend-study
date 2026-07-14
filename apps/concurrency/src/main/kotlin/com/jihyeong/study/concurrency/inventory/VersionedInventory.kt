package com.jihyeong.study.concurrency.inventory

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version

@Entity
@Table(name = "versioned_inventories")
class VersionedInventory(
	@Column(nullable = false)
	var quantity: Int,
) {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	val id: Long? = null

	@Version
	var version: Long? = null

	fun decrease() {
		check(quantity > 0) { "재고가 부족합니다." }
		quantity -= 1
	}
}
