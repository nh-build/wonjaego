# 05 — 재고 기록: 입고/판매/반품 Movement + 총재고 반영

**What to build:** 로그인한 Member가 어느 채널에서 발생했는지와 함께 입고(INBOUND)/판매(SALE)/반품(RETURN) 재고 변동을 기록하면, 해당 Product의 공유 총재고(`stockQuantity`)에 즉시 반영된다. 재고가 0 밑으로 내려가는 기록은 거부된다. 이 티켓에서 Product/SalesChannel 삭제에 "참조하는 Movement가 있으면 거부" 제약을 완성한다.

**Blocked by:** 03 — 상품(Product) CRUD, 04 — 판매채널(SalesChannel) CRUD

**Status:** ready-for-agent

- [ ] `Movement extends BaseEntity`: `product`(`@ManyToOne`, not null), `salesChannel`(`@ManyToOne`, not null), `type`(enum, 이 티켓에서는 `INBOUND`/`SALE`/`RETURN`만 사용), `quantityChange`(부호 있는 정수), `memo`(nullable).
- [ ] `/movements/new`에서 Product + Channel + 타입(INBOUND/SALE/RETURN) + 수량 + 메모(선택)로 Movement를 등록할 수 있다.
- [ ] Movement가 생성되는 트랜잭션 안에서 서비스 레이어가 해당 Product의 `stockQuantity`를 `quantityChange`만큼 갱신한다(INBOUND/RETURN은 양수, SALE은 음수).
- [ ] 갱신 결과 `stockQuantity`가 음수가 되는 Movement는 저장 자체가 거부되고 에러가 화면에 표시된다(Product도 Movement도 저장되지 않음).
- [ ] Movement는 로그인한 Member 소유의 Product/Channel에 대해서만 생성할 수 있다(다른 Member 소유 리소스를 대상으로 지정하면 거부/404).
- [ ] `/products/{id}` 상세 화면에서 해당 Product의 Movement 이력(타입/채널/수량변화/메모/일시)을 조회할 수 있다.
- [ ] Product 삭제 시, 해당 Product를 참조하는 Movement가 하나라도 있으면 거부되고 이유가 담긴 에러가 표시된다. SalesChannel 삭제도 동일하게 참조하는 Movement가 있으면 거부된다.
- [ ] MockMvc + 실제 test H2로 각 타입별 Movement 생성 후 `stockQuantity` 변화, 재고 부족 시 거부, 다른 Member 리소스 대상 거부, Movement 존재 시 Product/Channel 삭제 거부를 검증하는 테스트가 있다.
