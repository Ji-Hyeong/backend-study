# Cache

## 학습 질문

- Cache-Aside에서 첫 조회와 이후 조회는 각각 어디를 읽고, 누가 캐시를 채우는가?
- cache stampede는 왜 TTL 만료나 cold start 뒤에 발생하는가?
- single-flight는 어떤 요청을 기다리게 하고, 원본 저장소 부하는 어떻게 줄이는가?
- 없는 값을 캐시하는 negative caching은 왜 짧은 TTL이 필요한가?
- 캐시 삭제와 원본 갱신 사이에는 어떤 순서 역전이 생기며, TTL은 이를 해결하는가?
- TTL jitter는 같은 시각의 만료를 어떻게 분산하는가?

## 상품 상세 조회 예제

모든 예제는 `product:detail:{productId}` 키를 사용합니다. `ProductSource`는 DB를 대신하는 원본 저장소이며, 조회 횟수를 기록해 캐시가 실제로 원본 부하를 줄였는지 확인합니다.

| 주제 | 구현 | 테스트에서 관찰할 결과 |
| --- | --- | --- |
| Cache-Aside | `getCacheAside` | 첫 요청은 원본을 읽고 캐시에 저장하며, 두 번째 요청은 캐시 HIT로 원본 조회 횟수가 `1`이다. |
| Cache Stampede | `getCacheAside` + `CountDownLatch` | 20개 요청이 같은 cache miss를 확인하면 원본 조회도 `20`번 발생한다. |
| Single-Flight | `getWithSingleFlight` | 같은 키의 진행 중 조회를 `CompletableFuture` 하나로 합쳐 원본 조회가 `1`번이다. |
| Negative Caching | `ProductCacheEntry.MISSING` | 없는 상품의 첫 조회 결과를 10초 동안 저장해 반복 조회의 원본 접근을 줄인다. |
| Stale Data | `evictThenUpdate` | 캐시 삭제 후 원본 갱신 전에 reader가 이전 값을 다시 캐시하면, 원본이 바뀐 뒤에도 stale cache가 남는다. |
| TTL Jitter | `ProductCacheTtlPolicy` | 정상 상품은 60~65초, 없는 상품은 10초 TTL을 사용한다. |

## 테스트 로그 읽는 법

```bash
./gradlew :apps:cache:test --rerun-tasks
```

stampede 테스트는 모든 요청을 `캐시 MISS` 직후에 멈춘 뒤 원본 조회를 동시에 허용합니다. 따라서 로그의 `원본 저장소 조회`가 20번 나오는 것을 확인할 수 있습니다. 이 대기 훅은 운영 코드의 요구 사항이 아니라, 경쟁 상태를 항상 같은 방식으로 재현하기 위한 테스트 장치입니다.

single-flight 테스트에서는 가장 먼저 `inFlightLoads`에 `CompletableFuture`를 넣은 요청만 원본을 읽습니다. 뒤따른 요청은 `single-flight 대기열 참여` 로그를 남기고 같은 결과를 기다립니다. 이 방식은 한 애플리케이션 인스턴스 안에서만 요청을 합칩니다.

## Redis 실행 메모

기본 실행 환경은 Redis를 `ProductCache` 저장소로 사용합니다. 테스트 프로필은 CI에서 독립 실행되도록 `InMemoryProductCache`를 주입하지만, Cache-Aside의 키·TTL·값 구조는 동일합니다.

```bash
docker compose -f docker/docker-compose.yml up -d redis
./gradlew :apps:cache:bootRun
```

Redis는 여러 인스턴스가 공유하는 캐시 저장소 역할을 하지만, single-flight의 `ConcurrentHashMap`은 인스턴스마다 분리됩니다. 여러 서버에서 stampede를 합치려면 Redis 락, 요청 병합 계층, stale-while-revalidate 같은 별도 전략을 검토해야 합니다.

`updateThenEvict`도 모든 경합을 제거하는 만능 해법은 아닙니다. 강한 정합성이 필요하면 버전 값, outbox 기반 무효화, 재삭제 같은 보완 전략을 서비스 특성에 맞춰 선택해야 합니다.
