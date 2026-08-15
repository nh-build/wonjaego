# 06 — 교환(Exchange) Movement

**What to build:** 로그인한 Member가 교환을 기록할 수 있다. 같은 Product 안에서의 교환은 재고 변동 없이 이력만 남고, 서로 다른 Product 간 교환은 원 Product 재고가 늘고 새 Product 재고가 주는 두 건의 Movement가 한 트랜잭션으로 함께 생성된다.

**Blocked by:** 05 — 재고 기록: 입고/판매/반품 Movement + 총재고 반영

**Status:** ready-for-agent

- [ ] `Movement.type`에 `EXCHANGE`가 사용 가능해진다.
- [ ] `/movements/new`의 교환 폼에서 채널 + 원 Product + 수량 + 메모(선택)를 입력하고, 새 Product를 비워두면 "동일 Product 교환"으로 처리돼 `quantityChange = 0`인 `EXCHANGE` Movement 한 건만 생성되며 `stockQuantity`는 변하지 않는다.
- [ ] 새 Product를 원 Product와 다르게 지정하면, 원 Product에 `quantityChange = +N`, 새 Product에 `quantityChange = -N`인 `EXCHANGE` Movement 두 건이 한 트랜잭션 안에서 함께 생성되고 각각의 `stockQuantity`에 반영된다.
- [ ] 타 Product 간 교환에서 새 Product의 재고가 부족해 음수가 되는 경우, 두 Movement 모두 저장되지 않고(원 Product 쪽도 롤백) 에러가 표시된다.
- [ ] 교환의 원/새 Product는 모두 로그인한 Member 소유여야 하며, 아니면 거부/404된다.
- [ ] `/products/{id}` 상세의 Movement 이력에서 교환 기록도 다른 타입과 함께 시간순으로 확인할 수 있다.
- [ ] MockMvc + 실제 test H2로 동일 Product 교환(재고 불변), 타 Product 간 교환(양쪽 재고 반영), 재고 부족 시 두 Movement 모두 롤백을 검증하는 테스트가 있다.
