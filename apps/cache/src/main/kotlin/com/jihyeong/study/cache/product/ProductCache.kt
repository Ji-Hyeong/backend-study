package com.jihyeong.study.cache.product

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

enum class ProductCacheEntryType {
	FOUND,
	MISSING,
}

data class ProductCacheEntry(
	val type: ProductCacheEntryType,
	val product: ProductView? = null,
) {

	init {
		require((type == ProductCacheEntryType.FOUND) == (product != null)) {
			"FOUND는 상품 값을 가져야 하고 MISSING은 상품 값이 없어야 합니다."
		}
	}

	companion object {
		fun found(product: ProductView) = ProductCacheEntry(ProductCacheEntryType.FOUND, product)

		fun missing() = ProductCacheEntry(ProductCacheEntryType.MISSING)
	}
}

interface ProductCache {

	fun get(key: String): ProductCacheEntry?

	fun put(key: String, entry: ProductCacheEntry, ttl: Duration)

	fun evict(key: String)
}

@Component
class ProductCacheKey {

	fun detail(productId: Long): String = "product:detail:$productId"
}

@Component
class ProductCacheTtlPolicy {

	fun ttlFor(productId: Long, entry: ProductCacheEntry): Duration {
		if (entry.type == ProductCacheEntryType.MISSING) {
			return NEGATIVE_CACHE_TTL
		}

		// 키마다 만료 시각을 조금씩 다르게 만들어 대량의 인기 키가 같은 순간에 만료되는 것을 줄입니다.
		val jitterSeconds = Math.floorMod(productId, TTL_JITTER_RANGE_SECONDS.toLong())
		return FOUND_CACHE_TTL.plusSeconds(jitterSeconds)
	}

	companion object {
		private val FOUND_CACHE_TTL: Duration = Duration.ofSeconds(60)
		private val NEGATIVE_CACHE_TTL: Duration = Duration.ofSeconds(10)
		private const val TTL_JITTER_RANGE_SECONDS = 6
	}
}

@Configuration
class ProductCacheConfiguration {

	@Bean
	fun cacheClock(): Clock = Clock.systemUTC()

	@Bean
	@ConditionalOnProperty(name = ["study.cache.storage"], havingValue = "redis", matchIfMissing = true)
	fun redisProductCache(redisTemplate: StringRedisTemplate, objectMapper: ObjectMapper): ProductCache {
		return RedisProductCache(redisTemplate, objectMapper)
	}

	@Bean
	@ConditionalOnProperty(name = ["study.cache.storage"], havingValue = "memory")
	fun inMemoryProductCache(clock: Clock): ProductCache = InMemoryProductCache(clock)
}

class RedisProductCache(
	private val redisTemplate: StringRedisTemplate,
	private val objectMapper: ObjectMapper,
) : ProductCache {

	override fun get(key: String): ProductCacheEntry? {
		val serializedEntry = redisTemplate.opsForValue().get(key) ?: return null
		return objectMapper.readValue(serializedEntry, ProductCacheEntry::class.java)
	}

	override fun put(key: String, entry: ProductCacheEntry, ttl: Duration) {
		redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(entry), ttl)
	}

	override fun evict(key: String) {
		redisTemplate.delete(key)
	}
}

class InMemoryProductCache(
	private val clock: Clock,
) : ProductCache {

	private val values = ConcurrentHashMap<String, CachedValue>()

	override fun get(key: String): ProductCacheEntry? {
		val cachedValue = values[key] ?: return null
		if (cachedValue.expiresAt.isAfter(clock.instant())) {
			return cachedValue.entry
		}

		// 만료된 값을 조회 스레드가 직접 제거해 이후 요청이 오래된 값을 재사용하지 않게 합니다.
		values.remove(key, cachedValue)
		return null
	}

	override fun put(key: String, entry: ProductCacheEntry, ttl: Duration) {
		values[key] = CachedValue(entry, clock.instant().plus(ttl))
	}

	override fun evict(key: String) {
		values.remove(key)
	}

	private data class CachedValue(
		val entry: ProductCacheEntry,
		val expiresAt: Instant,
	)
}
