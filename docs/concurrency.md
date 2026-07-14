# Concurrency

## 학습 질문

- 포인트, 재고, 쿠폰 수량 차감은 왜 단순 `find -> update`만으로 부족한가?
- `synchronized`가 해결하는 범위는 왜 단일 JVM 인스턴스에 한정되는가?
- 비관적 락과 낙관적 락은 각각 충돌을 피하는 방식과 감지하는 방식에서 어떻게 다른가?
- 낙관적 락 충돌을 재시도할 때 왜 반드시 새 트랜잭션에서 최신 값을 다시 읽어야 하는가?
- Redis 분산 락의 TTL과 소유 토큰은 어떤 장애 상황을 막기 위한 장치인가?
- 멱등성 키는 중복 요청과 동시 요청 중 어디까지 방어하는가?

## 재고 차감 예제

모든 예제는 재고 `20`개에 동시에 `20`번 차감을 요청합니다. 테스트는 단순히 결과만 비교하지 않고, `concurrency-study` 로그로 각 요청이 읽은 값, 락 획득, 충돌, 재시도 과정을 보여 줍니다.

| 방식 | 구현 | 관찰할 결과 | 적용 범위 |
| --- | --- | --- | --- |
| 무락 | `NoLockInventoryService` | 모든 요청이 `20`을 읽고 `19`를 저장해 최종 재고가 `19`가 된다. | 없음 |
| JVM 락 | `SynchronizedInventoryService` | 한 JVM 안에서는 DB 커밋까지 임계 구역에 포함해 차감이 직렬화되고 최종 재고가 `0`이 된다. | 단일 애플리케이션 인스턴스 |
| 비관적 락 | `PessimisticLockInventoryService` | `PESSIMISTIC_WRITE` 행 락을 얻은 순서로 최신 값을 읽어 최종 재고가 `0`이 된다. | DB를 공유하는 모든 인스턴스 |
| 낙관적 락 | `OptimisticLockInventoryService` | `version` 충돌을 감지하고 새 트랜잭션으로 재시도해 최종 재고가 `0`이 된다. | DB를 공유하는 모든 인스턴스 |
| Redis 락 | `RedisInventoryLockService` | Redis `SET NX`로 진입을 제한한 뒤 DB 트랜잭션에서 차감한다. | Redis를 공유하는 모든 인스턴스 |

`Inventory`와 `VersionedInventory`는 의도적으로 분리했습니다. `@Version`이 붙은 엔터티는 이미 낙관적 락이므로, 무락의 lost update를 정확히 재현하려면 별도 엔터티가 필요합니다.

`@Transactional`과 `synchronized`를 같은 메서드에 붙이는 것만으로는 충분하지 않습니다. Spring 트랜잭션 프록시는 대상 메서드의 반환 뒤에 커밋하므로, 모니터 락이 먼저 풀릴 수 있습니다. 이 예제는 `SynchronizedInventoryService`가 별도 트랜잭션 Bean 호출 전체를 감싸도록 구성해 커밋까지 직렬화합니다.

## 테스트 로그 읽는 법

```bash
./gradlew :apps:concurrency:test --rerun-tasks
```

무락 테스트는 `CountDownLatch`로 모든 요청이 조회를 끝낼 때까지 쓰기를 막습니다. 따라서 로그에서 `무락 조회 ... quantity=20`이 20번 나온 뒤 `무락 변경 ... changedQuantity=19`가 이어지고, 최종 수량은 `19`가 됩니다. 이 장치는 운영 코드가 아니라, 경쟁 상태를 확정적으로 재현하기 위한 테스트용 훅입니다.

낙관적 락 테스트도 첫 조회를 같은 `version`에서 멈춥니다. 한 요청의 update만 `where id = ? and version = ?` 조건을 통과하고, 나머지는 `OptimisticLockingFailureException`을 받아 새 트랜잭션에서 다시 조회합니다. 로그의 `낙관적 락 충돌`과 `낙관적 락 커밋 성공`을 순서대로 확인합니다.

## Redis 분산 락 실행 메모

`RedisInventoryLockService`는 `inventory:lock:{inventoryId}` 키에 UUID 소유 토큰을 저장합니다. 락 해제는 Lua 스크립트로 값이 같은 경우에만 삭제하므로, TTL이 지난 뒤 다른 요청이 획득한 락을 이전 요청이 지우지 않습니다.

로컬에서 Redis 예제를 직접 호출하려면 먼저 인프라를 실행합니다.

```bash
docker compose -f docker/docker-compose.yml up -d redis
```

이 구현은 즉시 획득에 실패하면 `LockNotAcquiredException`을 던지는 가장 작은 형태입니다. 운영에서는 요청 특성에 따라 대기 정책, TTL 연장, 장애 시 보상과 멱등성 키를 함께 설계해야 합니다. Redis 락은 DB 트랜잭션을 대체하지 않으며, 락 획득 후에만 짧은 DB 트랜잭션을 열어야 합니다.
