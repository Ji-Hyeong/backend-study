package com.jihyeong.study.concurrency.redis

import com.jihyeong.study.concurrency.inventory.InventoryTransactionService
import com.jihyeong.study.concurrency.support.ConcurrencyStudyLogger
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

class LockNotAcquiredException(inventoryId: Long) : IllegalStateException("재고 락을 획득하지 못했습니다. inventoryId=$inventoryId")

@Component
class RedisLockManager(
	private val redisTemplate: StringRedisTemplate,
) {

	fun tryLock(key: String, token: String, leaseTime: Duration): Boolean {
		return redisTemplate.opsForValue().setIfAbsent(key, token, leaseTime) == true
	}

	fun unlock(key: String, token: String) {
		// 만료 후 다른 요청이 획득한 락을 삭제하지 않도록 Redis 내부에서 소유 토큰을 비교하고 삭제합니다.
		redisTemplate.execute(UNLOCK_SCRIPT, listOf(key), token)
	}

	companion object {
		private val UNLOCK_SCRIPT = DefaultRedisScript<Long>().apply {
			setScriptText("if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end")
			setResultType(Long::class.java)
		}
	}
}

@Service
class RedisInventoryLockService(
	private val redisLockManager: RedisLockManager,
	private val inventoryTransactionService: InventoryTransactionService,
) {

	fun decrease(inventoryId: Long) {
		val key = "inventory:lock:$inventoryId"
		val token = UUID.randomUUID().toString()
		if (!redisLockManager.tryLock(key, token, LOCK_LEASE_TIME)) {
			ConcurrencyStudyLogger.state("Redis 락 획득 실패: inventoryId={}", inventoryId)
			throw LockNotAcquiredException(inventoryId)
		}

		try {
			ConcurrencyStudyLogger.state("Redis 락 획득: inventoryId={}, key={}", inventoryId, key)
			inventoryTransactionService.decrease(inventoryId)
		} finally {
			redisLockManager.unlock(key, token)
			ConcurrencyStudyLogger.state("Redis 락 해제 시도: inventoryId={}, key={}", inventoryId, key)
		}
	}

	companion object {
		private val LOCK_LEASE_TIME: Duration = Duration.ofSeconds(3)
	}
}
