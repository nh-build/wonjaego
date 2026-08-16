# 02 — 변형별 SKU·품절임박 기준 개별 수정 화면

**What to build:** 셀러가 상품 상세 화면에서 각 ProductVariant(옵션 조합)의 SKU와 품절임박 기준을 개별적으로 채우고 수정할 수 있다. SKU가 없어도 이미(01에서) Movement는 기록할 수 있었지만, 이 화면에서 정리해 넣을 수 있게 된다.

**Blocked by:** 01 — 스키마 전환: Product/OptionGroup/OptionValue/ProductVariant + 핵심 흐름 재배선

**Status:** ready-for-agent

- [x] `/products/{productId}` 상세 화면의 변형 목록에서 각 ProductVariant로 수정 화면 진입점이 있다.
- [x] 수정 화면에서 해당 ProductVariant의 SKU와 품절임박 기준을 입력·수정할 수 있다(둘 다 비워둘 수 있음).
- [x] SKU를 입력했을 때, 같은 Member의 다른 ProductVariant와 SKU가 겹치면 거부되고 에러가 화면에 표시된다(자기 자신은 제외하고 비교).
- [x] 변형의 원 소유 Product가 다른 Member 소유이면 404가 반환된다(입력값이 잘못돼 있어도 검증보다 먼저 확인).
- [x] 존재하지 않거나 해당 Product에 속하지 않는 변형 id로 접근하면 404가 반환된다.
- [x] 품절임박 기준을 비우면 시스템 기본값(5)이 판정에 쓰이고, 화면에도 "기본값(5)"처럼 표시된다.
- [x] MockMvc + 실제 test H2로 SKU·기준 수정, Member 범위 SKU 중복 거부, 다른 회원 소유 변형/상품에 대한 404(입력값 오류와 함께인 경우 포함)를 검증하는 테스트가 있다.
