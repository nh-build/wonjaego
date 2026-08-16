---
status: accepted
---

# Product를 flat 구조에서 Product/OptionGroup/OptionValue/ProductVariant 구조로 바꾼다

Product는 더 이상 그 자체로 재고 단위가 아니다. 상품명·가격만 갖는 상위 개념이 되고, SKU·총재고·품절임박 기준은 새로 도입한 ProductVariant(상품 변형)로 옮겨간다. Product는 등록 시점에 여러 OptionGroup(예: 색상)을 가질 수 있고, 각 OptionGroup은 콤마로 구분해 입력한 OptionValue(예: 블랙, 화이트)를 갖는다. 저장 시 OptionGroup들의 OptionValue를 모두 조합(카티전 곱)해 ProductVariant가 자동 생성되며, 옵션이 0개인 Product는 조합 없는 ProductVariant 1개(상품 자체)를 갖는다. Movement는 이제 Product가 아니라 ProductVariant를 대상으로 기록된다.

옵션 구조는 등록 시점에만 입력할 수 있고, 이후에는 바꿀 수 없다 — 옵션을 바꾸려면 이력 없는 Product를 삭제하고 다시 등록해야 한다. ProductVariant는 생성 직후 재고 0·SKU 없음 상태로 시작하며, SKU 없이도 Movement(입고 등)를 기록할 수 있다. SKU는 Member 전체 범위에서 유일해야 한다.

## Considered Options

- **flat 구조 유지, 옵션은 Product 필드로 흉내** (예: "색상: 블랙" 같은 문자열을 상품명에 포함): 조합이 늘어날 때마다 상품을 개별로 반복 등록해야 하고, "이 상품의 다른 옵션"이라는 관계 자체가 시스템에 없어 대시보드·재고 집계가 상품별로 흩어진다.
- **Product/OptionGroup/OptionValue/ProductVariant 구조 (채택)**: 옵션 조합을 자동 생성해 반복 입력을 없애고, 재고·SKU·품절임박 기준을 실제 판매 단위(변형)에 정확히 대응시킨다.
- **옵션 구조를 등록 후에도 자유롭게 수정 가능하게 설계**: 검토했으나 기각. 기존 ProductVariant에 쌓인 Movement 이력을 남긴 채 옵션 조합을 추가/삭제하려면 "어떤 조합이 없어졌을 때 이력을 어떻게 할지"를 정해야 하는데, 지금 이 diff 로직까지 구현하는 건 범위가 크다. 옵션 구조를 등록 시점에 고정하면 이 문제 자체가 생기지 않는다.

## Consequences

- 기존 Flat Product 스키마/샘플 데이터는 마이그레이션 없이 새 구조로 교체한다. 이 프로젝트는 아직 실사용자가 없는 개발 초기 단계라 마이그레이션 비용을 들일 이유가 없었다.
- 옵션 구조를 등록 후에 바꾸고 싶다는 요구가 생기면, 이 ADR을 재검토하고 "이력이 없는 조합만 추가/삭제 가능" 같은 부분 재생성 로직을 별도로 설계해야 한다.
- [0001](./0001-shared-total-stock-across-channels.md)의 "공유 총재고" 주체가 Product에서 ProductVariant로 바뀌었다 — 채널별 독립 재고를 쓰지 않는다는 핵심 결정 자체는 이 변경으로 바뀌지 않는다.
