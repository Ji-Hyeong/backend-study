package com.jihyeong.study.cache.product

import com.jihyeong.study.cache.support.CacheStudyLogger
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Component
class ProductSource {

	private val products = ConcurrentHashMap<Long, ProductView>()
	private val queryCounts = ConcurrentHashMap<Long, AtomicInteger>()

	init {
		resetForStudy()
	}

	fun findById(productId: Long): ProductView? {
		val queryCount = queryCounts.computeIfAbsent(productId) { AtomicInteger() }.incrementAndGet()
		val product = products[productId]
		CacheStudyLogger.state(
			"원본 저장소 조회: productId={}, queryCount={}, found={}",
			productId,
			queryCount,
			product != null,
		)
		return product
	}

	fun update(productId: Long, name: String, price: Long) {
		require(products.containsKey(productId)) { "상품을 찾을 수 없습니다. productId=$productId" }
		products[productId] = ProductView(productId, name, price)
		CacheStudyLogger.state("원본 저장소 갱신: productId={}, name={}, price={}", productId, name, price)
	}

	fun queryCount(productId: Long): Int = queryCounts[productId]?.get() ?: 0

	fun currentProduct(productId: Long): ProductView? = products[productId]

	fun resetForStudy() {
		products.clear()
		products.putAll(
			mapOf(
				101L to ProductView(101L, "Kotlin in Action", 39000),
				102L to ProductView(102L, "Spring Boot Guide", 42000),
				103L to ProductView(103L, "Redis Handbook", 35000),
				104L to ProductView(104L, "Cache Patterns", 28000),
				105L to ProductView(105L, "Before Update", 30000),
				106L to ProductView(106L, "Consistent Update", 31000),
			),
		)
		queryCounts.clear()
	}
}
