# 원재고 (Wonjaego)

1인 셀러가 여러 판매 채널에 걸친 재고를 한 곳에서 관리하는 웹앱. 1차 MVP는 플랫폼 API 연동 없이 전량 수동 입력이다.

## Language

**Member**:
앱에 가입한 1인 셀러. username, password, 상호명(사업체명)을 가진다.
_Avoid_: User, 사용자, 셀러(별도 엔티티로 취급하지 않음)

**Product**:
셀러가 판매하는 상품. 상품명과 가격을 갖는다. 실제 재고 단위가 아니라, 하나 이상의 ProductVariant를 묶는 상위 개념이다. 옵션 그룹은 등록 시점에만 정할 수 있고 이후 옵션 구조 자체는 바꿀 수 없다(상품명·가격은 계속 수정 가능).
_Avoid_: Item, SKU(Product의 필드가 아니라 ProductVariant의 필드), flat 상품(과거 구조 — 더 이상 쓰지 않음)

**OptionGroup** (옵션 그룹):
Product에 속하는 옵션의 종류 하나(예: "색상", "사이즈"). name과 여러 OptionValue를 갖는다. 등록 시점에만 입력하며 이후 추가·수정·삭제하지 않는다.
_Avoid_: 옵션 타입, 속성

**OptionValue** (옵션 값):
OptionGroup에 속하는 값 하나(예: "블랙"). 등록 화면에서 콤마로 구분해 한 번에 입력받고, 앞뒤 공백 제거와 중복 제거를 거쳐 생성된다.
_Avoid_: 옵션 항목

**ProductVariant** (상품 변형):
실제 재고 단위. Product 하나와 그 Product에 속한 각 OptionGroup에서 고른 OptionValue 조합(0개 이상)으로 식별된다. sku(nullable), 총재고, 품절임박 기준을 갖는다. 등록 시 각 OptionGroup의 값들을 모두 조합(카티전 곱)해 자동 생성되며, 옵션이 0개인 Product는 조합도 없는 ProductVariant 1개(상품 자체)를 갖는다. 생성 직후에는 재고 0·SKU 없음 상태이며, SKU가 없어도 Movement를 기록할 수 있다.
_Avoid_: SKU(단독으로는 ProductVariant의 필드를 가리킬 때만 사용), 옵션 조합(설명용으로만 사용, 엔티티명은 ProductVariant)

**SalesChannel** (판매채널):
셀러가 ProductVariant를 판매하는 외부 플랫폼(스마트스토어, 에이블리, 지그재그 등). 고정 목록이 아니라 셀러가 직접 등록/관리하는 엔티티다.
_Avoid_: 플랫폼, 마켓

**총재고** (Stock Quantity):
ProductVariant 하나가 갖는 단일 재고 수량. 여러 SalesChannel이 이 하나의 수량을 공유하며, 특정 채널에 독립적으로 배정된 재고는 존재하지 않는다. 오버셀링(같은 재고를 여러 채널에 중복 판매)을 막는 것이 이 구조의 핵심 목적이다.
_Avoid_: 채널별 재고, 재고 배정(이 프로젝트에서는 쓰지 않는 개념)

**Movement** (입출고 기록):
ProductVariant의 총재고를 변동시키는 이력. 어느 SalesChannel에서 발생했는지 태그로 기록하지만, 실제 증감은 항상 ProductVariant의 공유 총재고에 반영된다. 타입은 입고(INBOUND)/판매(SALE)/교환(EXCHANGE)/반품(RETURN) 중 하나이며 MVP는 전량 수동 입력이다.
_Avoid_: 입출고 기록(설명용으로만 사용, 엔티티명은 Movement)

**품절임박 기준** (Low Stock Threshold):
총재고가 이 수량 이하로 떨어지면 대시보드에 경고를 표시하는 기준값. ProductVariant 단위로 설정 가능하며(nullable), 비워두면 시스템 기본값(5)을 따른다.
_Avoid_: 안전재고, 최소재고 (재고 계획 개념과 혼동되므로 사용하지 않음)
