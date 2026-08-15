# 03 — 상품(Product) CRUD

**What to build:** 로그인한 Member가 자신의 Product를 등록(상품명/SKU/가격/초기 총재고/품절임박 기준)하고, 목록·상세를 조회하고, 정보를 수정할 수 있다. 삭제 액션은 화면에 존재하되 실제 제약(Movement 참조 시 거부)은 05에서 완성된다.

**Blocked by:** 02 — 회원가입/로그인/로그아웃/내 정보

**Status:** ready-for-agent

- [ ] `Product extends BaseEntity`: `member`(`@ManyToOne`), `name`, `sku`(해당 Member 범위 내 unique), `price`(`BigDecimal`), `stockQuantity`(`int`, 0 이상), `lowStockThreshold`(`Integer`, nullable).
- [ ] `/products`에서 상품명/SKU/가격/초기 총재고/품절임박 기준(비워도 됨)으로 Product를 등록할 수 있다.
- [ ] 같은 Member 안에서 SKU가 중복되면 등록/수정이 거부되고 에러가 화면에 표시된다. 초기 총재고에 음수를 입력하면 거부된다.
- [ ] `/products`에서 로그인한 Member 소유 Product 목록만 조회된다. 다른 Member의 Product 목록은 섞여 보이지 않는다.
- [ ] `/products/{id}`에서 Product 상세(정보)를 조회할 수 있다. 다른 Member 소유 Product의 id로 접근하면 404가 반환된다.
- [ ] `/products/{id}/edit`에서 상품명/SKU/가격/품절임박 기준을 수정할 수 있다(재고는 이 화면에서 직접 수정하지 않음 — 재고 변경은 05의 Movement를 통해서만 이뤄진다).
- [ ] 삭제 액션 UI/엔드포인트는 존재하지만, 이 티켓 시점에는 Movement가 없으므로 항상 삭제가 성공한다(참조 제약은 05에서 추가).
- [ ] MockMvc + 실제 test H2로 등록/SKU 중복 거부/목록 스코핑/상세 404/수정 흐름을 검증하는 테스트가 있다.
