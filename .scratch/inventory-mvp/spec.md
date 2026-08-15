Status: ready-for-agent

# 원재고 MVP — 멀티채널 재고관리 웹앱

## Problem Statement

1인 셀러는 스마트스토어·에이블리·지그재그 등 여러 채널에 같은 상품을 올려두고 판매한다. 채널마다 재고를 따로 손으로 맞추다 보니, 한 채널에서 재고가 소진돼도 다른 채널에는 반영되지 않아 실제로는 없는 물건이 팔리는 오버셀링이 생긴다. 또한 입고·판매·교환·반품이 일어날 때마다 여러 채널 관리자 화면을 오가며 수기로 재고를 맞춰야 해서 시간이 들고 실수가 잦다.

## Solution

원재고는 셀러가 상품과 판매 채널을 한 번 등록해두면, 채널에 상관없이 상품마다 하나의 공유 총재고를 관리하는 웹앱이다. 어느 채널에서 입고·판매·교환·반품이 일어나든 그 자리에서 Movement로 기록하면 즉시 Product의 공유 총재고에 반영되고, 대시보드에서 전체 재고 현황과 품절임박 상품을 한눈에 확인할 수 있다. 모바일에서 빠르게 기록할 수 있도록 PWA로 제공해 홈 화면에 설치할 수 있게 한다.

MVP는 전량 수동 입력이며, 플랫폼 API 자동연동은 2차 범위다.

## User Stories

1. As a 방문자, I want 회원가입(username, password, 상호명)을 할 수 있길, so that 내 셀러 계정으로 재고를 관리할 수 있다.
2. As a 방문자, I want 이미 사용 중인 username으로는 가입이 거부되길, so that 계정이 서로 섞이지 않는다.
3. As a 등록된 Member, I want 로그인할 수 있길, so that 내 상품과 재고에 접근할 수 있다.
4. As a 로그인한 Member, I want 로그아웃할 수 있길, so that 공용 기기에서 내 계정을 안전하게 종료할 수 있다.
5. As a 로그인한 Member, I want 내 정보(username, 상호명)를 조회할 수 있길, so that 내 계정 정보를 확인할 수 있다.
6. As a 로그인한 Member, I want Product를 등록(상품명, SKU, 가격, 초기 총재고, 품절임박 기준)할 수 있길, so that 판매할 상품을 시스템에 반영할 수 있다.
7. As a 로그인한 Member, I want 같은 SKU로 중복 등록하면 거부되길, so that 상품을 헷갈리지 않고 식별할 수 있다.
8. As a 로그인한 Member, I want 내 Product 목록을 조회할 수 있길, so that 전체 상품 현황을 파악할 수 있다.
9. As a 로그인한 Member, I want Product 상세(정보 + Movement 이력)를 조회할 수 있길, so that 특정 상품의 재고가 왜 이 수치인지 추적할 수 있다.
10. As a 로그인한 Member, I want Product 정보(상품명, SKU, 가격, 품절임박 기준)를 수정할 수 있길, so that 가격 변경 등 상품 정보를 최신으로 유지할 수 있다.
11. As a 로그인한 Member, I want Movement 이력이 없는 Product를 삭제할 수 있길, so that 더 이상 팔지 않는 상품을 정리할 수 있다.
12. As a 로그인한 Member, I want Movement 이력이 있는 Product는 삭제가 거부되길, so that 재고 기록의 무결성이 깨지지 않는다.
13. As a 로그인한 Member, I want SalesChannel을 자유롭게 등록(이름 직접 입력)할 수 있길, so that 스마트스토어/에이블리/지그재그는 물론 앞으로 늘어날 채널도 반영할 수 있다.
14. As a 로그인한 Member, I want 내 SalesChannel 목록을 조회할 수 있길, so that 어떤 채널에서 판매 중인지 파악할 수 있다.
15. As a 로그인한 Member, I want SalesChannel 이름을 수정할 수 있길, so that 채널명이 바뀌어도 기록을 계속 이어갈 수 있다.
16. As a 로그인한 Member, I want Movement 이력이 없는 SalesChannel을 삭제할 수 있길, so that 더 이상 쓰지 않는 채널을 정리할 수 있다.
17. As a 로그인한 Member, I want Movement 이력이 있는 SalesChannel은 삭제가 거부되길, so that 재고 기록의 무결성이 깨지지 않는다.
18. As a 로그인한 Member, I want 입고(INBOUND) Movement를 기록할 수 있길, so that 새로 들어온 재고를 총재고에 반영할 수 있다.
19. As a 로그인한 Member, I want 판매(SALE) Movement를 기록할 수 있길, so that 어느 채널에서 팔렸는지와 함께 총재고를 줄일 수 있다.
20. As a 로그인한 Member, I want 반품(RETURN) Movement를 기록할 수 있길, so that 되돌아온 재고를 총재고에 다시 더할 수 있다.
21. As a 로그인한 Member, I want 같은 Product 안에서 발생한 교환(단순 재발송 등)을 재고 변동 없이 기록할 수 있길, so that 재고 수치를 왜곡하지 않으면서 이력을 남길 수 있다.
22. As a 로그인한 Member, I want 서로 다른 Product 간 교환을 기록하면 원래 Product 재고가 늘고 새 Product 재고가 줄길, so that 실제 재고 상태가 정확히 반영된다.
23. As a 로그인한 Member, I want 판매나 교환으로 재고가 0 밑으로 내려가는 Movement는 거부되길, so that 실재하지 않는 재고가 팔린 것처럼 기록되지 않는다.
24. As a 로그인한 Member, I want Movement에 메모를 남길 수 있길, so that 나중에 왜 이런 변동이 있었는지 맥락을 알 수 있다.
25. As a 로그인한 Member, I want 한 번 기록한 Movement는 수정/삭제할 수 없고 실수는 반대 방향 Movement로 정정하길, so that 재고 이력이 항상 감사 가능한 상태로 유지된다.
26. As a 로그인한 Member, I want 대시보드에서 상품 수와 전체 재고 합계를 볼 수 있길, so that 전체 재고 규모를 한눈에 파악할 수 있다.
27. As a 로그인한 Member, I want 대시보드에서 총재고가 품절임박 기준 이하인 상품 목록을 볼 수 있길, so that 재입고가 필요한 상품을 놓치지 않는다.
28. As a 로그인한 Member, I want 품절임박 기준을 비워두면 기본값(5)이 적용되길, so that 상품마다 일일이 기준을 정하지 않아도 경고를 받을 수 있다.
29. As a 로그인한 Member, I want 다른 Member의 Product/SalesChannel/Movement에는 접근할 수 없길, so that 내 재고 데이터가 다른 셀러에게 노출되지 않는다.
30. As a 모바일 사용자, I want 이 웹앱을 홈 화면에 설치할 수 있길, so that 앱처럼 빠르게 열어서 재고를 기록할 수 있다.
31. As a 처음 앱을 켠 셀러, I want 샘플 회원/채널/상품/Movement 데이터가 이미 들어있길, so that 빈 화면이 아니라 실제 사용 흐름을 바로 둘러볼 수 있다.

## Implementation Decisions

**패키지/부트스트랩**
- `back/` 아래 Spring Boot 4.x, JDK 25, Gradle Kotlin DSL. 루트 패키지 `com.wonjaego`, 메인 클래스 `com.wonjaego.WonjaegoApplication`에 `@EnableJpaAuditing`.
- 의존성: `spring-boot-starter-web`, `spring-boot-starter-thymeleaf`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-validation`, `spring-boot-devtools`, `lombok`, `com.h2database:h2`, 그리고 Thymeleaf에서 CSRF 토큰과 `sec:authorize` 등을 쓰기 위한 `thymeleaf-extras-springsecurity6`(사용자가 명시하지 않았지만 Spring Security + Thymeleaf 조합의 필수 보완재로 추가).
- `spring.jpa.open-in-view=false`. 모든 트랜잭션 경계는 `@Transactional`을 붙인 서비스 메서드 레벨.
- 프로필: `dev`(기본) — 파일 기반 H2, `ddl-auto=update`, h2-console 활성화. `test` — 인메모리 H2, `ddl-auto=create`.

**공통 베이스**
- `BaseEntity`(`@MappedSuperclass`, `@EntityListeners(AuditingEntityListener.class)`): `id`(`Long`, IDENTITY), `createdAt`/`updatedAt`(`@CreatedDate`/`@LastModifiedDate`). 모든 엔티티가 상속.

**엔티티**
- `Member extends BaseEntity`: `username`(unique, not null), `password`(BCrypt 해시 저장), `businessName`(not null). 별도 Role 엔티티 없이 인증된 Member는 전부 동일 권한(`ROLE_SELLER`)으로 취급.
- `Product extends BaseEntity`: `member`(`@ManyToOne`), `name`, `sku`(Member 범위 내 unique), `price`(`BigDecimal`), `stockQuantity`(`int`, 0 이상만 허용), `lowStockThreshold`(`Integer`, nullable — null이면 대시보드에서 기본값 5로 취급).
- `SalesChannel extends BaseEntity`: `member`(`@ManyToOne`), `name`(Member 범위 내 unique).
- `Movement extends BaseEntity`: `product`(`@ManyToOne`, not null), `salesChannel`(`@ManyToOne`, not null), `type`(enum `INBOUND`/`SALE`/`EXCHANGE`/`RETURN`), `quantityChange`(부호 있는 `int` — INBOUND/RETURN은 양수, SALE은 음수, EXCHANGE는 두 건 중 한쪽은 양수·한쪽은 음수 또는 동일 상품 교환일 땐 0), `memo`(`String`, nullable).
- 교환은 두 가지 경로로 처리:
  - 같은 Product 내 교환(단순 재발송/불량 교체 등): `quantityChange = 0`인 `EXCHANGE` Movement 한 건만 생성. 재고에는 영향 없음.
  - 서로 다른 Product 간 교환: 원 Product에 `quantityChange = +N`, 새 Product에 `quantityChange = -N`인 `EXCHANGE` Movement 두 건을 한 트랜잭션 안에서 함께 생성. 두 Movement를 서로 연결하는 FK는 두지 않고(MVP 범위 밖), `memo`로 맥락을 남기는 것으로 충분하다고 판단.
- `Product.stockQuantity`는 계산값이 아니라 저장 필드. Movement가 생성되는 트랜잭션 안에서 서비스 레이어가 `stockQuantity += quantityChange`로 갱신하고, 결과가 음수가 되면 검증 실패로 저장을 막는다. Movement 테이블은 그 변동의 감사 이력 역할만 한다.

**권한/보안**
- Spring Security 폼 로그인. `UserDetailsService` 구현체가 `Member`를 조회해 `UserDetails`로 감싼다. 비밀번호는 `BCryptPasswordEncoder`.
- CSRF는 기본 활성화 상태로 두고 Thymeleaf 폼에서 자동 포함.
- 모든 조회/수정은 로그인한 Member로 스코프. 다른 Member 소유의 Product/SalesChannel/Movement를 id로 접근하면 403이 아니라 404로 응답해 존재 여부를 노출하지 않는다.
- Product/SalesChannel 삭제는 해당 리소스를 참조하는 Movement가 하나라도 있으면 거부(존재 여부는 삭제 전 조회로 판단해 사용자에게 명확한 에러 메시지로 안내).

**화면(Thymeleaf)**
- `/signup`, `/login`, `/logout`(Spring Security 기본 처리), `/me`(내 정보 조회 전용, 수정 기능 없음).
- `/products`(목록 + 등록 폼), `/products/{id}`(상세 + Movement 이력), `/products/{id}/edit`(수정), 삭제는 목록/상세에서 POST 액션.
- `/channels`(목록 + 등록 폼), `/channels/{id}/edit`, 삭제는 목록에서 POST 액션.
- `/movements/new`(Movement 등록 폼 — 타입 선택에 따라 필요한 입력이 달라짐: INBOUND/SALE/RETURN은 Product+Channel+수량, EXCHANGE는 원 Product/새 Product(선택, 비우면 동일 Product 교환)+Channel+수량).
- `/`(대시보드 — 상품 수, 전체 재고 합계, 품절임박 상품 목록).

**프론트/PWA**
- Tailwind CSS + DaisyUI는 별도 Node 빌드 파이프라인 없이 CDN(Play CDN)으로 로드. 백엔드가 Gradle 단일 빌드로 끝나도록 하기 위한 선택.
- `manifest.json`(앱 이름, 아이콘, `display: standalone`)과 최소한의 서비스워커(정적 자산 캐시 우선 전략만, 오프라인 데이터 동기화는 범위 밖)를 두어 홈 화면 설치를 지원.

**샘플 데이터**
- `BaseInitData`(`ApplicationRunner`)는 `dev` 프로필에서만 동작하고, Member가 하나도 없을 때만 실행(재실행 시 중복 생성 방지). 샘플 Member 1명, SalesChannel 2~3개(스마트스토어/에이블리/지그재그), Product 3~4개(품절임박 상태를 보여주기 위해 최소 1개는 기준 이하로), 그리고 그 Product들의 `stockQuantity`와 앞뒤가 맞는 INBOUND/SALE/RETURN/EXCHANGE Movement 이력을 생성.

## Testing Decisions

- 좋은 테스트는 컨트롤러가 어떤 SQL을 날리는지가 아니라, "이 요청을 보내면 사용자 입장에서 관찰 가능한 결과(응답 상태/리다이렉트/렌더된 화면 데이터, 그리고 이후 조회 시 DB에 반영된 상태)가 이렇게 바뀐다"만 검증한다. 내부 서비스 메서드 호출 여부나 구현 디테일은 검증하지 않는다.
- 이번 스펙은 그린필드라 기존 테스트 관례는 없음. 이번 스펙에서 정한 컨트롤러 seam(`@SpringBootTest(webEnvironment = MOCK)` + `@AutoConfigureMockMvc`, 목킹 없는 실제 H2 test DB)이 이후 스펙들의 기본 관례가 된다.
- 테스트 대상: 회원가입/로그인 흐름, Product CRUD(및 SKU 중복·삭제 제약), SalesChannel CRUD(및 삭제 제약), Movement 등록(INBOUND/SALE/RETURN/동일 상품 교환/타 상품 간 교환) 후 관련 Product의 `stockQuantity` 변화, 재고 부족 시 Movement 거부, 대시보드의 품절임박 판정(기준 미설정 시 기본값 5 적용 포함), 다른 Member 소유 리소스 접근 시 404.
- 인증이 필요한 요청은 `@WithMockUser` 또는 실제 로그인 흐름(폼 로그인 후 세션 유지)으로 처리 — 어느 쪽을 쓸지는 구현 단계에서 결정.

## Out of Scope

- 스마트스토어/에이블리/지그재그 등 플랫폼 API 자동연동(2차 범위).
- 채널별 독립 재고 배정/채널별 노출 상한.
- 상품 옵션(Variant) 분리 구조 — Product는 Flat 유지.
- 관리자 등 SELLER 이외의 역할/권한.
- 비밀번호 재설정, 이메일 인증, 회원정보 수정(비밀번호 변경 등).
- Movement 수정/삭제, 서로 다른 Product 간 교환 두 건을 하나로 묶어 보여주는 UI/FK.
- 재고 부족 시 이메일/푸시 등 능동적 알림 — 대시보드 조회 시 표시만 한다.
- 오프라인 상태에서의 데이터 입력/동기화(서비스워커는 정적 자산 캐시만 담당).
- 다국어, 타임존 처리.

## Further Notes

- `docs/adr/0001-shared-total-stock-across-channels.md`에 채널별 배정 대신 공유 총재고를 택한 이유가 기록돼 있음 — 구현 중 이 결정을 뒤집고 싶어지면 먼저 그 ADR을 재검토할 것.
- 가격은 원화 기준이라 소수점이 필요 없지만, 추후 다른 통화나 할인 계산을 고려해 `BigDecimal`로 저장(스케일 0).
- `CONTEXT.md`의 용어(Member/Product/SalesChannel/Movement/총재고/품절임박 기준)를 코드 네이밍과 커밋/PR 설명에서도 그대로 사용할 것.
