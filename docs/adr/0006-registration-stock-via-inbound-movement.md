---
status: accepted
---

# 상품 등록 시 입력한 조합별 재고는 ProductVariant에 직접 set하지 않고, INBOUND Movement로 채운다

상품 등록 화면(옵션 & 재고)에서 조합별로 입력한 재고 숫자는 "초기 입고"로 해석한다. `ProductService.create()`는 각 ProductVariant를 항상 재고 0으로 생성한 뒤, 입력된 재고가 0보다 큰 조합마다 `MovementType.INBOUND` Movement를 하나씩 기록해 재고를 채운다. 재고가 0인 조합은 Movement를 남기지 않고 그대로 0에 머문다. 즉 등록 화면의 재고 입력 UI가 있어도, 실제 재고 증감 경로는 언제나 Movement 하나뿐이다 — ProductVariant의 재고 필드를 등록 로직이 직접 대입하는 경로는 존재하지 않는다.

이 결정에 따라 `Movement.salesChannel`(`sales_channel_id`)을 NOT NULL에서 nullable로 바꿨다. 초기 입고·재고 실사 같은 조정은 특정 판매 채널에서 일어난 사건이 아니므로 채널을 강제로 채울 이유가 없다. 채널이 없는 Movement는 상세 화면 등에서 `-`로 표시한다.

## Considered Options

- **등록 폼에서 재고를 직접 대입 (기각)**: 목업 그대로 조합별 숫자를 `ProductVariant.stockQuantity`에 바로 저장하는 방식. 구현은 가장 단순하지만, "재고는 오직 Movement를 통해서만 변한다"는 기존 원칙([0001](./0001-shared-total-stock-across-channels.md))을 깨고, 등록 시점의 재고 변화가 입출고 기록에 남지 않아 감사 추적이 끊긴다.
- **등록 시 INBOUND Movement 자동 생성 (채택)**: UI는 목업과 동일하게 재고 숫자를 입력받지만, 저장 시 이를 초기 입고 Movement로 변환한다. 재고가 왜 그 값이 됐는지 항상 Movement 이력으로 설명 가능하고, 기존 재고 변경 경로(판매/반품/교환/실사)와 동일한 메커니즘을 그대로 재사용한다.

## Consequences

- 등록 시 재고를 입력한 조합 수만큼 Movement 행이 함께 생긴다 — 상품 하나를 등록할 때 변형이 여러 개면 그만큼 "상품 등록 시 초기 재고" 메모가 붙은 INBOUND 기록이 남는다.
- `Movement.salesChannel`이 nullable이 되면서, 채널을 가정하고 짜여진 조회·표시 로직은 null을 항상 처리해야 한다(예: `LEFT JOIN FETCH`, `movement.salesChannel != null` 분기).
