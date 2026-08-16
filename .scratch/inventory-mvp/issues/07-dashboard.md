# 07 — 대시보드: 전체 재고 현황 + 품절임박

**What to build:** 로그인한 Member가 `/`(대시보드)에서 자신의 전체 상품 수와 총재고 합계를 확인하고, 품절임박(총재고가 기준 이하) 상품 목록을 볼 수 있다.

**Blocked by:** 03 — 상품(Product) CRUD

**Status:** ready-for-agent

- [x] 대시보드에 로그인한 Member 소유 Product의 총 개수와 `stockQuantity` 합계가 표시된다.
- [x] 대시보드에 `stockQuantity`가 `lowStockThreshold`(설정 안 됐으면 기본값 5) 이하인 Product 목록이 표시된다.
- [x] `lowStockThreshold`가 설정된 Product는 그 값이, 설정 안 된 Product는 기본값 5가 판정 기준으로 쓰인다.
- [x] 다른 Member 소유 Product는 개수·합계·품절임박 목록 어디에도 포함되지 않는다.
- [x] MockMvc + 실제 test H2로 상품 수/총재고 합계 표시, 기준 설정/미설정 각각에서의 품절임박 판정, 다른 Member 데이터 미포함을 검증하는 테스트가 있다.
