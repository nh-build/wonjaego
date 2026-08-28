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

**상품 사진** (Product Photo):
Product 하나에 선택적으로 붙일 수 있는 사진 한 장. 등록/수정 화면에서 업로드하며, 상품 목록·상세에 썸네일로 표시된다. 로그인한 소유자만 조회할 수 있다.
_Avoid_: 이미지(설명용으로만 사용), 썸네일(표시 방식일 뿐 별도 개념 아님)

**AI 상품명 추천**:
상품 등록 화면에서 셀러가 입력한 포인트 단어(필수, 자유 텍스트)와 컨셉/무드(선택, 자유 텍스트)를 바탕으로 상품명 후보 5개를 생성해 보여주는 보조 기능. 고정된 스타일 목록은 없다 — 컨셉/무드도 사용자가 매번 직접 입력한다. 클릭하면 상품명 입력칸에 채워질 뿐, 별도로 저장되지 않는다.
_Avoid_: 자동완성(형태는 비슷하지만 별도 개념), AI 추천(어떤 필드에 대한 추천인지 불명확하므로 "AI 상품명 추천"으로 항상 완전히 표기), 컨셉 버튼/스타일 선택(과거 구조 — 더 이상 쓰지 않음)

**SalesChannel** (주문 채널 태그):
Movement(입출고 기록)에 태그로 붙이는, 셀러가 직접 등록/관리하는 자유 텍스트 채널 이름. 고정 목록이 아니다 — "어느 채널에서 일어난 입출고인지"만 기록할 뿐, ChannelType처럼 실제 플랫폼과 API로 연동되지는 않는다. /channels/tags 화면에서 등록·수정·삭제한다.
_Avoid_: 판매 채널(ChannelType을 가리키는 용어로 예약됨 — 과거엔 SalesChannel도 이렇게 불렸으나 ChannelType 도입 후 혼동을 피하기 위해 이 이름을 씀), 플랫폼, 마켓

**ChannelType** (판매 채널):
셀러가 API로 연동할 수 있는, 고정된 외부 플랫폼 목록(지그재그/쿠팡/스마트스토어/에이블리). 셀러가 직접 등록하는 SalesChannel과 달리 코드에 정의된 고정 enum이며, 채널마다 API 연동 가능 여부(isIntegrationSupported)를 갖는다. 오늘은 지그재그만 true — 나머지는 "준비중"으로 표시되고 연동 버튼이 비활성화된다.
_Avoid_: 채널(SalesChannel과 혼동되므로 문맥 없이 단독으로 쓰지 않음), 판매채널(SalesChannel의 옛 이름과 혼동되므로 띄어 쓴 "판매 채널"만 사용)

**ChannelCredential** (채널 연동):
셀러 한 명이 ChannelType 하나에 대해 맺은 연동 한 건. 암호화된 API 키(CredentialEncryptor로 암호화, 평문 저장 금지), ChannelRole, 연동 상태(status), 연동일(connectedAt), 마지막 상품 가져오기 기록(lastImportedAt/lastImportedCount)을 갖는다. "연동 먼저 → 관리화면에서 역할 선택" 흐름을 따른다 — /channels(화면1)에서 연동만 하고, /channels/{id}(화면2)에서 역할을 정하고 상태를 관리한다. 해제(disconnect)해도 행은 남고 암호화된 키만 지워진다 — role과 lastImportedAt 이력을 재연동 시에도 잃지 않기 위해서다.
_Avoid_: 채널 키(암호화 저장이라는 성격이 드러나지 않으므로), API 연동(설명용으로만 사용)

**ChannelRole** (채널 역할):
연동된 ChannelCredential이 하는 역할 — ChannelType 자체가 아니라 "셀러가 이 채널을 연동한 인스턴스"에 붙는, 관리 화면에서 언제든 바꿀 수 있는 값이다. PRODUCT_SOURCE(상품 소스)는 상품/재고/가격을 가져오는 기준 채널, ORDER_SYNC(주문 연동, 기본값)는 주문·교환·환불만 가져와 기존 재고에 반영하는 채널이다. 한 셀러당 PRODUCT_SOURCE는 최대 1개 — 서비스 계층에서 검증하며 어기면 에러다(상품 중복 방지).
_Avoid_: 채널 타입(ChannelType과 혼동), 연동 방식

**총재고** (Stock Quantity):
ProductVariant 하나가 갖는 단일 재고 수량. 여러 SalesChannel이 이 하나의 수량을 공유하며, 특정 채널에 독립적으로 배정된 재고는 존재하지 않는다. 오버셀링(같은 재고를 여러 채널에 중복 판매)을 막는 것이 이 구조의 핵심 목적이다.
_Avoid_: 채널별 재고, 재고 배정(이 프로젝트에서는 쓰지 않는 개념)

**Movement** (입출고 기록):
ProductVariant의 총재고를 변동시키는 이력. 어느 SalesChannel에서 발생했는지 태그로 기록하지만, 실제 증감은 항상 ProductVariant의 공유 총재고에 반영된다. 타입은 입고(INBOUND)/판매(SALE)/교환(EXCHANGE)/반품(RETURN) 중 하나이며 MVP는 전량 수동 입력이다.
_Avoid_: 입출고 기록(설명용으로만 사용, 엔티티명은 Movement)

**품절임박 기준** (Low Stock Threshold):
총재고가 이 수량 이하로 떨어지면 대시보드에 경고를 표시하는 기준값. ProductVariant 단위로 설정 가능하며(nullable), 비워두면 시스템 기본값(5)을 따른다.
_Avoid_: 안전재고, 최소재고 (재고 계획 개념과 혼동되므로 사용하지 않음)
