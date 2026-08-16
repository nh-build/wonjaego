Status: ready-for-agent

# 상품 옵션(Variant) 구조 전환

## Problem Statement

지금 Product는 flat 구조라, 색상·사이즈처럼 같은 상품 안에서 옵션만 다른 경우에도 셀러가 조합마다 SKU·상품명을 따로 만들어 별도 Product로 등록해야 한다. 이러면 "이 상품의 다른 옵션"이라는 관계가 시스템에 전혀 남지 않고, 옵션이 몇 개인 상품이든 조합 수만큼 반복 등록해야 해서 등록 자체가 번거롭다.

## Solution

Product는 상품명·가격만 갖는 상위 개념으로 바뀌고, 등록 시점에 옵션 그룹(예: 색상)과 각 그룹의 값(콤마로 구분 입력, 예: "블랙, 화이트")을 여러 개 추가할 수 있다. 저장하면 각 옵션 그룹 값들의 모든 조합이 ProductVariant(상품 변형)로 자동 생성되고, 이후 SKU·총재고·품절임박 기준·Movement는 전부 ProductVariant 단위로 관리된다. 옵션이 0개인 상품은 조합 없는 ProductVariant 1개(상품 자체)를 가지므로, 옵션이 필요 없는 단일 상품도 동일한 방식으로 계속 등록할 수 있다.

옵션 그룹/값은 등록 시점에만 입력하며 이후에는 바꿀 수 없다. 새로 생성된 ProductVariant는 재고 0·SKU 없음 상태로 시작하고, 셀러는 상품 상세 화면에서 각 변형의 SKU·품절임박 기준을 개별로 채우고, 입고 Movement로 재고를 채운다. SKU가 없어도 Movement는 기록할 수 있다.

## User Stories

1. As a 셀러, I want 상품 등록 시 옵션 그룹(예: 색상)을 여러 개 추가할 수 있길, so that 하나의 상품에 여러 종류의 옵션을 정의할 수 있다.
2. As a 셀러, I want 각 옵션 그룹의 값을 콤마로 구분해 한 번에 입력할 수 있길, so that 여러 값을 일일이 반복 입력하지 않아도 된다.
3. As a 셀러, I want 콤마로 입력한 값들의 앞뒤 공백과 중복이 자동으로 정리되길, so that 실수로 "블랙 "과 "블랙"을 다른 값으로 취급하지 않는다.
4. As a 셀러, I want 옵션 그룹들의 값 조합이 저장 시 자동으로 모두 생성되길, so that 조합마다 상품을 따로 등록하지 않아도 된다.
5. As a 셀러, I want 옵션을 하나도 추가하지 않고도 상품을 등록할 수 있길, so that 옵션이 필요 없는 단일 상품도 그대로 관리할 수 있다.
6. As a 셀러, I want 옵션 없는 상품이 등록 즉시 하나의 재고 단위(변형)로 취급되길, so that 옵션 유무와 상관없이 재고 관리 방식이 일관된다.
7. As a 셀러, I want 상품 등록 화면에서 상품명과 가격만 입력하면 되길(재고는 이 화면에서 다루지 않음), so that 등록 폼이 옵션 조합 수에 따라 길어지지 않는다.
8. As a 셀러, I want 상품 등록 직후 생성된 각 변형이 재고 0·SKU 없음 상태로 시작하길, so that 등록 화면에서 조합마다 값을 일일이 입력하지 않아도 된다.
9. As a 셀러, I want 상품 상세 화면에서 그 상품에 속한 모든 변형(옵션 조합)을 목록으로 볼 수 있길, so that 상품 하나의 전체 재고 현황을 한눈에 파악할 수 있다.
10. As a 셀러, I want 각 변형의 SKU와 품절임박 기준을 상품 상세에서 개별적으로 수정할 수 있길, so that 조합마다 다른 관리 기준을 둘 수 있다.
11. As a 셀러, I want 변형의 SKU가 내 다른 상품/변형의 SKU와 겹치면 거부되길, so that SKU가 여전히 내 전체 상품군에서 유일한 식별자로 쓰일 수 있다.
12. As a 셀러, I want SKU를 아직 정하지 않은 변형에도 입고 Movement를 기록해 재고를 채울 수 있길, so that 재고부터 먼저 채우고 SKU는 나중에 정리할 수 있다.
13. As a 셀러, I want Movement 등록 화면에서 상품이 아니라 변형(옵션 조합)을 선택하길, so that 정확히 어느 조합의 재고가 변하는지 지정할 수 있다.
14. As a 셀러, I want 입고/판매/반품/교환 Movement가 선택한 변형의 총재고에만 반영되길, so that 같은 상품의 다른 옵션 조합 재고에 영향을 주지 않는다.
15. As a 셀러, I want 변형의 재고가 부족한 판매/교환을 시도하면 거부되길, so that 실제로 없는 재고가 팔리지 않는다.
16. As a 셀러, I want 여러 채널에서 같은 변형을 팔아도 하나의 공유 재고가 같이 줄어들길, so that 옵션 조합 단위에서도 오버셀링이 방지된다.
17. As a 셀러, I want 다른 회원 소유의 상품/변형을 대상으로 조회·수정·Movement 등록을 시도하면 404가 반환되길, so that 내 데이터가 다른 사람에게 노출되거나 조작되지 않는다.
18. As a 셀러, I want 대시보드의 "상품 수"가 실제 관리 중인 변형(SKU) 개수를 보여주길, so that 총재고·품절임박과 같은 단위로 숫자를 이해할 수 있다.
19. As a 셀러, I want 대시보드의 총재고 합계가 모든 변형의 재고를 더한 값이길, so that 옵션 단위로 흩어진 재고를 하나의 숫자로 확인할 수 있다.
20. As a 셀러, I want 품절임박 목록이 변형 단위로 판정되길(설정된 기준 또는 기본값 5 이하), so that 어느 색상/사이즈가 곧 품절되는지 구체적으로 알 수 있다.
21. As a 셀러, I want 품절임박 목록에서 상품명과 옵션 조합을 함께 볼 수 있길, so that 상품 상세로 바로 이동해 확인할 수 있다.
22. As a 셀러, I want 상품 삭제 시 그 상품에 속한 변형 중 하나라도 Movement 이력이 있으면 삭제가 거부되길, so that 재고 이력이 실수로 사라지지 않는다.
23. As a 셀러, I want 옵션 그룹/값은 상품 등록 이후 바꿀 수 없다는 것이 화면에서도 분명하길(옵션을 바꾸려면 삭제 후 재등록), so that 이력과 조합 구조 사이의 불일치가 생기지 않는다.
24. As a 셀러, I want 상품명·가격은 등록 후에도 계속 수정할 수 있길, so that 옵션 구조와 무관한 정보는 자유롭게 관리할 수 있다.
25. As a 신규 개발 환경 이용자, I want dev 프로필로 처음 켰을 때 옵션이 있는 상품과 없는 상품이 섞인 샘플 데이터를 보길, so that 새 구조의 전체 흐름을 바로 둘러볼 수 있다.

## Implementation Decisions

- **Product**: member, name, price만 남는다. sku·stockQuantity·lowStockThreshold는 제거되고 ProductVariant로 옮겨간다. Product는 1:N OptionGroup, 1:N ProductVariant를 갖는다.
- **OptionGroup**: Product에 속한 옵션 종류 하나(name)와 1:N OptionValue. 등록 시점에만 생성되며 이후 추가/수정/삭제 엔드포인트는 없다.
- **OptionValue**: OptionGroup에 속한 값 하나. 콤마로 구분된 입력 문자열을 트림·중복 제거해 생성한다(순서는 입력 순서를 보존).
- **ProductVariant**: 실제 재고 단위. Product 하나와, Product에 속한 각 OptionGroup에서 고른 OptionValue 조합(0개 이상, 그룹당 정확히 1개)으로 식별된다. `sku`(nullable), `stockQuantity`, `lowStockThreshold`(nullable)를 갖는다.
  - 생성 시점: 모든 OptionGroup의 OptionValue를 카티전 곱으로 조합해 자동 생성. 옵션 0개면 조합 없는 ProductVariant 1개.
  - 초기 상태: `stockQuantity=0`, `sku=null`, `lowStockThreshold=null`.
  - SKU 유일성: Member 전체 범위에서 유일 (Product 단위가 아님).
- **Movement**: 대상이 Product에서 ProductVariant로 바뀐다(`variant` 필드로 교체). SalesChannel은 계속 태그로만 쓰이고, 실제 증감은 ProductVariant의 총재고에 반영된다. SKU가 없는 ProductVariant에도 Movement를 기록할 수 있다.
- **상품 등록 화면**: 상품명·가격 입력 + 옵션 그룹을 여러 개 추가하는 UI(그룹 이름 + 콤마 값 입력). 재고 입력란은 없다(재고는 등록 화면이 다루지 않음).
- **상품 상세 화면**: 그 상품에 속한 모든 ProductVariant를 목록으로 보여주고(옵션 조합 표시, SKU, 총재고, 품절임박 기준), 각 변형을 개별 수정(SKU·품절임박 기준)하는 진입점을 제공한다. Movement 이력도 변형 단위로 볼 수 있어야 한다.
- **Movement 등록 화면**: Product가 아니라 ProductVariant를 선택한다(상품명 + 옵션 조합으로 표시). 교환(EXCHANGE)의 "새 상품" 선택도 ProductVariant 선택으로 바뀐다.
- **변형 표시 라벨**: 옵션 값들을 OptionGroup 생성 순서대로 " / "로 이어붙여 표시(예: "블랙 / S"). 옵션이 0개인 변형은 상품명만 표시.
- **상품 삭제**: 그 상품에 속한 ProductVariant 중 하나라도 Movement 이력이 있으면 삭제를 거부한다(기존의 Product 단위 검사를 변형 전체에 대한 집계 검사로 일반화).
- **대시보드**: "상품 수"는 ProductVariant 개수를 센다(기존 Product 개수 대신). 총재고 합계와 품절임박 목록은 모든 ProductVariant를 대상으로 계산하며, 품절임박 목록 항목은 상품명 + 옵션 조합 라벨로 표시한다.
- **마이그레이션 없음**: 기존 Flat Product 스키마/데이터는 새 구조로 교체한다. `BaseInitData`와 기존 Flat 구조 기반 테스트는 새 구조에 맞게 다시 작성한다.
- CONTEXT.md, `docs/adr/0001-shared-total-stock-across-channels.md`(주체 갱신), `docs/adr/0002-flat-product-to-variant-structure.md`(신규)는 이미 갱신되어 있다.

## Testing Decisions

- 기존과 동일하게 **단일 컨트롤러 seam**(MockMvc 풀스택 + 실제 test H2)으로 검증한다. Service 레이어(옵션 조합 생성, 재고 반영)에 대한 별도 유닛 테스트 seam은 추가하지 않는다.
- 테스트 대상: 상품 등록(옵션 그룹 입력 → 조합 자동 생성, 콤마 트림/중복 제거, 옵션 0개 케이스), 변형 관리(SKU·품절임박 기준 수정, Member 범위 SKU 중복 거부, 다른 회원 소유 404), 변형 대상 Movement 등록(입고/판매/반품/교환, 재고 부족 거부, SKU 없어도 기록 가능), 상품 삭제 제약(변형 중 하나라도 이력 있으면 거부), 대시보드(변형 개수/총재고 합계/품절임박 목록이 여러 상품·변형에 걸쳐 정확한지), BaseInitData(새 구조의 샘플 데이터가 로그인 후 화면 전반에서 확인되는지).
- Prior art: 기존 `ProductCrudTest`, `MovementRecordingTest`, `ExchangeMovementTest`, `DashboardTest`, `BaseInitDataTest`와 동일한 패턴(`AuthTestSupport.signUpAndLogin` 세션 헬퍼, ownership 404를 validation 분기 이전에 무조건 검사하는 패턴)을 그대로 따른다.

## Out of Scope

- 옵션 그룹/값을 상품 등록 이후에 추가·수정·삭제하는 기능 (ADR-0002에서 기각한 대안).
- 변형별 가격 오버라이드 — 가격은 계속 Product 레벨.
- 채널별 독립 재고 배정 (ADR-0001 유지).
- 기존 Flat 데이터의 자동 마이그레이션.
- 플랫폼 API 자동연동 (기존 MVP 스펙에서도 2차 범위).

## Further Notes

이 스펙은 기존 `inventory-mvp` 스펙의 Product 관련 결정을 대체한다. 이미 구현된 9개 티켓 중 Product/Movement/Dashboard/BaseInitData를 다루는 부분이 이 스펙의 영향을 받으며, 티켓 분해 시 스키마/엔티티 변경(Product/OptionGroup/OptionValue/ProductVariant, Movement 대상 교체)을 가장 먼저 두고, 그 위에 등록 화면 → 변형 관리 화면 → Movement 화면 → 대시보드 → BaseInitData/기존 테스트 재작성 순으로 의존성을 쌓는 것을 권장한다.
