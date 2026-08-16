# 03 — BaseInitData 샘플 데이터를 새 구조로 재작성

**What to build:** dev 프로필로 처음 앱을 켠 셀러가 옵션이 있는 상품과 없는 상품이 섞인 샘플 데이터로 새 변형 구조의 전체 흐름(등록 → 변형별 SKU/재고 → Movement → 대시보드)을 바로 둘러볼 수 있다.

**Blocked by:** 01 — 스키마 전환: Product/OptionGroup/OptionValue/ProductVariant + 핵심 흐름 재배선, 02 — 변형별 SKU·품절임박 기준 개별 수정 화면

**Status:** ready-for-agent

- [ ] `BaseInitData`는 여전히 `dev` 프로필에서만 동작하고, Member가 하나도 없을 때만 샘플 데이터를 생성한다(재기동해도 중복 생성 안 됨).
- [ ] 샘플 Member 1명, SalesChannel 2~3개가 생성된다.
- [ ] 옵션이 있는 상품(최소 1개, 옵션 그룹 1개 이상 → 여러 ProductVariant 생성)과 옵션이 없는 상품(최소 1개, 단일 ProductVariant)이 섞여 있다.
- [ ] 생성된 ProductVariant 중 최소 1개는 SKU와 품절임박 기준이 채워져 있다.
- [ ] 생성된 ProductVariant 중 최소 1개는 최종 재고가 품절임박 기준 이하가 되어, 대시보드의 품절임박 목록에서 바로 확인된다.
- [ ] 생성된 Movement 이력(INBOUND/SALE/RETURN, 그리고 최소 1건의 EXCHANGE 포함)이 각 ProductVariant의 최종 `stockQuantity`와 앞뒤가 맞는다.
- [ ] `dev` 프로필로 기동 후 샘플 계정으로 로그인하면 대시보드(변형 개수/총재고/품절임박)/상품 목록/상품 상세(변형 목록 + Movement 이력)/채널 목록에서 위 샘플 데이터가 확인된다는 것을 검증하는 테스트가 있다.
