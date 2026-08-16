# 01 — 스키마 전환: Product/OptionGroup/OptionValue/ProductVariant + 핵심 흐름 재배선

**What to build:** 셀러가 상품 등록 화면에서 옵션 그룹(콤마로 값 입력)을 여러 개 추가하면, 저장 시 모든 값 조합이 ProductVariant로 자동 생성된다. 옵션을 하나도 추가하지 않으면 상품 자체가 조합 없는 변형 1개로 등록된다. 이후 재고(Movement)와 대시보드 집계는 전부 이 변형 단위로 동작한다.

**Blocked by:** None — can start immediately

**Status:** ready-for-agent

- [x] `Product`는 member/name/price만 갖는다(sku·stockQuantity·lowStockThreshold 제거).
- [x] `OptionGroup`(Product 1:N, name)과 `OptionValue`(OptionGroup 1:N)가 존재한다.
- [x] `ProductVariant`(sku nullable, stockQuantity, lowStockThreshold nullable)가 Product와 (0개 이상의) OptionValue 조합으로 식별된다.
- [x] `/products` 등록 화면에서 상품명·가격 입력 + 옵션 그룹을 여러 개 추가할 수 있다(그룹 이름 + 콤마 구분 값 입력).
- [x] 콤마로 입력한 값은 트림·중복 제거된 뒤 조합 생성에 쓰인다(예: "블랙 , 블랙, 화이트 " → 블랙, 화이트).
- [x] 저장 시 각 옵션 그룹 값들의 카티전 곱만큼 ProductVariant가 자동 생성된다. 옵션 그룹이 0개면 조합 없는 ProductVariant 1개가 생성된다.
- [x] 새로 생성된 ProductVariant는 `stockQuantity=0`, `sku=null`, `lowStockThreshold=null` 상태로 시작한다.
- [x] `sku`에는 Member 범위 유일성 제약이 걸려 있다(DB 레벨 `@UniqueConstraint(member_id, sku)` + 서비스 레벨 조회 메서드 준비 완료. 이 티켓에는 SKU를 직접 입력하는 화면이 없어 HTTP 경로로 exercise하는 테스트는 02번 티켓 몫으로 남겨둠).
- [x] `/products/{id}` 상세 화면에서 그 상품에 속한 모든 ProductVariant를 옵션 조합 라벨(예: "블랙 / S", 옵션 0개면 상품명만)과 함께 목록으로 볼 수 있다.
- [x] `/movements/new`에서 Product가 아니라 ProductVariant를 선택해(상품명 + 옵션 조합 라벨로 표시) 입고/판매/반품/교환 Movement를 기록할 수 있다. 교환의 "새 상품" 선택도 ProductVariant 선택으로 바뀐다.
- [x] Movement 기록은 선택한 ProductVariant의 총재고에만 반영되며, 재고가 부족해지는 판매/교환은 거부된다.
- [x] 다른 회원 소유의 상품/변형을 대상으로 조회·Movement 등록을 시도하면 404가 반환된다.
- [x] 상품 삭제 시, 그 상품에 속한 ProductVariant 중 하나라도 Movement 이력이 있으면 삭제가 거부된다.
- [x] 대시보드의 "상품 수"는 ProductVariant 개수를, 총재고 합계는 모든 ProductVariant 재고의 합을 보여준다. 품절임박 목록은 변형 단위로 판정되며(기준 미설정 시 기본값 5) 상품명 + 옵션 조합 라벨로 표시된다.
- [x] 상품명·가격은 등록 후에도 수정할 수 있다(옵션 그룹/값은 이 티켓 범위에서 수정 화면이 없음 — 등록 시점에만 정해짐).
- [x] MockMvc + 실제 test H2로 위 흐름(옵션 조합 생성, 옵션 0개 케이스, 콤마 트림/중복 제거, 변형 대상 Movement 각 타입, 재고 부족 거부, 다른 회원 소유 404, 삭제 제약, 대시보드 집계)을 검증하는 테스트가 있다. 기존 Flat 구조를 가정했던 테스트는 이 새 구조에 맞게 다시 작성했다.
