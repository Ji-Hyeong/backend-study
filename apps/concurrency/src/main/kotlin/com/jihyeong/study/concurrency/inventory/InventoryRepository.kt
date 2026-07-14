package com.jihyeong.study.concurrency.inventory

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface InventoryRepository : JpaRepository<Inventory, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select inventory from Inventory inventory where inventory.id = :id")
	fun findByIdWithPessimisticLock(id: Long): Inventory?
}

interface VersionedInventoryRepository : JpaRepository<VersionedInventory, Long>
