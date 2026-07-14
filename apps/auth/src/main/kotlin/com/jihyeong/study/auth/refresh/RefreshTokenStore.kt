package com.jihyeong.study.auth.refresh

import com.jihyeong.study.auth.support.AuthStudyLogger
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

enum class RefreshTokenConsumeResult {
	CONSUMED,
	REUSED,
	REVOKED,
	NOT_FOUND,
}

interface RefreshTokenStore {

	fun create(userId: Long, tokenId: String, ttl: Duration)

	fun rotate(userId: Long, previousTokenId: String, nextTokenId: String, ttl: Duration): RefreshTokenConsumeResult

	fun revokeAll(userId: Long)
}

@Configuration
class RefreshTokenStoreConfiguration {

	@Bean
	fun refreshTokenClock(): Clock = Clock.systemUTC()

	@Bean
	@ConditionalOnProperty(name = ["study.auth.refresh-token-store"], havingValue = "redis", matchIfMissing = true)
	fun redisRefreshTokenStore(redisTemplate: StringRedisTemplate): RefreshTokenStore {
		return RedisRefreshTokenStore(redisTemplate)
	}

	@Bean
	@ConditionalOnProperty(name = ["study.auth.refresh-token-store"], havingValue = "memory")
	fun inMemoryRefreshTokenStore(clock: Clock): RefreshTokenStore = InMemoryRefreshTokenStore(clock)
}

class InMemoryRefreshTokenStore(
	private val clock: Clock,
) : RefreshTokenStore {

	private val tokens = ConcurrentHashMap<String, StoredRefreshToken>()
	private val tokenIdsByUser = ConcurrentHashMap<Long, MutableSet<String>>()

	@Synchronized
	override fun create(userId: Long, tokenId: String, ttl: Duration) {
		tokens[tokenId] = StoredRefreshToken(userId, RefreshTokenStatus.ACTIVE, clock.instant().plus(ttl))
		tokenIdsByUser.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }.add(tokenId)
		AuthStudyLogger.state("refresh token 저장: userId={}, tokenId={}", userId, tokenId)
	}

	@Synchronized
	override fun rotate(
		userId: Long,
		previousTokenId: String,
		nextTokenId: String,
		ttl: Duration,
	): RefreshTokenConsumeResult {
		val token = tokens[previousTokenId] ?: return RefreshTokenConsumeResult.NOT_FOUND
		if (token.userId != userId || !token.expiresAt.isAfter(clock.instant())) {
			return RefreshTokenConsumeResult.NOT_FOUND
		}

		return when (token.status) {
			RefreshTokenStatus.ACTIVE -> {
				tokens[previousTokenId] = token.copy(status = RefreshTokenStatus.USED)
				tokens[nextTokenId] = StoredRefreshToken(userId, RefreshTokenStatus.ACTIVE, clock.instant().plus(ttl))
				tokenIdsByUser.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }.add(nextTokenId)
				RefreshTokenConsumeResult.CONSUMED
			}
			RefreshTokenStatus.USED -> {
				revokeAllInternal(userId)
				RefreshTokenConsumeResult.REUSED
			}
			RefreshTokenStatus.REVOKED -> RefreshTokenConsumeResult.REVOKED
		}
	}

	@Synchronized
	override fun revokeAll(userId: Long) {
		revokeAllInternal(userId)
	}

	private fun revokeAllInternal(userId: Long) {
		tokenIdsByUser[userId].orEmpty().forEach { tokenId ->
			tokens.computeIfPresent(tokenId) { _, token -> token.copy(status = RefreshTokenStatus.REVOKED) }
		}
		AuthStudyLogger.state("사용자 refresh token 전체 폐기: userId={}", userId)
	}

	private data class StoredRefreshToken(
		val userId: Long,
		val status: RefreshTokenStatus,
		val expiresAt: Instant,
	)
}

private enum class RefreshTokenStatus {
	ACTIVE,
	USED,
	REVOKED,
}

class RedisRefreshTokenStore(
	private val redisTemplate: StringRedisTemplate,
) : RefreshTokenStore {

	override fun create(userId: Long, tokenId: String, ttl: Duration) {
		val tokenKey = tokenKey(tokenId)
		redisTemplate.opsForHash<String, String>().putAll(
			tokenKey,
			mapOf("userId" to userId.toString(), "status" to RefreshTokenStatus.ACTIVE.name),
		)
		redisTemplate.expire(tokenKey, ttl)
		redisTemplate.opsForSet().add(userIndexKey(userId), tokenId)
		redisTemplate.expire(userIndexKey(userId), ttl)
		AuthStudyLogger.state("Redis refresh token 저장: userId={}, tokenId={}", userId, tokenId)
	}

	override fun rotate(
		userId: Long,
		previousTokenId: String,
		nextTokenId: String,
		ttl: Duration,
	): RefreshTokenConsumeResult {
		val result = redisTemplate.execute(
			ROTATE_SCRIPT,
			listOf(tokenKey(previousTokenId), tokenKey(nextTokenId), userIndexKey(userId)),
			userId.toString(),
			nextTokenId,
			ttl.toMillis().toString(),
		)
		return when (result) {
			1L -> RefreshTokenConsumeResult.CONSUMED
			2L -> RefreshTokenConsumeResult.REUSED
			3L -> RefreshTokenConsumeResult.REVOKED
			else -> RefreshTokenConsumeResult.NOT_FOUND
		}
	}

	override fun revokeAll(userId: Long) {
		redisTemplate.opsForSet().members(userIndexKey(userId)).orEmpty().forEach { tokenId ->
			redisTemplate.opsForHash<String, String>().put(tokenKey(tokenId), "status", RefreshTokenStatus.REVOKED.name)
		}
		AuthStudyLogger.state("Redis 사용자 refresh token 전체 폐기: userId={}", userId)
	}

	private fun tokenKey(tokenId: String): String = "auth:refresh:$tokenId"

	private fun userIndexKey(userId: Long): String = "auth:refresh:user:$userId"

	companion object {
		// 이전 토큰 소비와 다음 토큰 생성, 재사용 시 세션 폐기를 하나의 Redis script로 실행합니다.
		private val ROTATE_SCRIPT = DefaultRedisScript<Long>().apply {
			setScriptText(
				"if redis.call('exists', KEYS[1]) == 0 then return 0 end " +
					"if redis.call('hget', KEYS[1], 'userId') ~= ARGV[1] then return 0 end " +
					"local status = redis.call('hget', KEYS[1], 'status') " +
					"if status == 'ACTIVE' then " +
						"redis.call('hset', KEYS[1], 'status', 'USED'); " +
						"redis.call('hset', KEYS[2], 'userId', ARGV[1], 'status', 'ACTIVE'); " +
						"redis.call('pexpire', KEYS[2], ARGV[3]); " +
						"redis.call('sadd', KEYS[3], ARGV[2]); redis.call('pexpire', KEYS[3], ARGV[3]); return 1 " +
					"end " +
					"if status == 'USED' then " +
						"local tokenIds = redis.call('smembers', KEYS[3]); " +
						"for _, tokenId in ipairs(tokenIds) do redis.call('hset', 'auth:refresh:' .. tokenId, 'status', 'REVOKED'); end; return 2 " +
					"end " +
					"return 3",
			)
			setResultType(Long::class.java)
		}
	}
}
