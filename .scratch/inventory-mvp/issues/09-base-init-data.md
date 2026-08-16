# 09 — BaseInitData 샘플 데이터

**What to build:** dev 프로필로 처음 앱을 켠 셀러가 빈 화면이 아니라, 샘플 Member/채널/상품/재고 이력이 이미 채워진 상태에서 바로 전체 사용 흐름(로그인 → 대시보드 → 상품/채널/이력)을 둘러볼 수 있다.

**Blocked by:** 02 — 회원가입/로그인/로그아웃/내 정보, 03 — 상품(Product) CRUD, 04 — 판매채널(SalesChannel) CRUD, 05 — 재고 기록: 입고/판매/반품 Movement + 총재고 반영, 06 — 교환(Exchange) Movement

**Status:** ready-for-agent

- [x] `BaseInitData`(`ApplicationRunner`)는 `dev` 프로필에서만 동작하고, `test` 프로필에서는 실행되지 않는다.
- [x] Member가 하나도 없을 때만 샘플 데이터를 생성한다(재기동해도 중복 생성되지 않는다).
- [x] 샘플 Member 1명(username/password/상호명), SalesChannel 2~3개(스마트스토어/에이블리/지그재그), Product 3~4개가 생성된다.
- [x] 생성된 Product 중 최소 1개는 품절임박 기준 이하 상태로 만들어져, 대시보드의 품절임박 목록에서 바로 확인된다.
- [x] 생성된 Movement 이력(INBOUND/SALE/RETURN, 그리고 최소 1건의 EXCHANGE 포함)이 각 Product의 최종 `stockQuantity`와 앞뒤가 맞는다(합산했을 때 실제 저장된 재고 수량과 일치).
- [x] `dev` 프로필로 기동 후 샘플 계정으로 로그인하면 대시보드/상품 목록/채널 목록/상품 상세의 Movement 이력에서 위 샘플 데이터가 확인된다는 것을 검증하는 테스트(또는 MockMvc 기반 통합 테스트)가 있다.
