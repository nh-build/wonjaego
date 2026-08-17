---
status: accepted
---

# 상품 사진은 FileStorage 추상화 뒤에서 로컬 파일시스템에 저장한다

Product는 선택적으로 사진 한 장을 가질 수 있다. Product는 저장소가 발급한 불투명한 키(`photoKey`)만 갖고, 실제 파일 입출력은 `FileStorage` 인터페이스(`store`/`load`/`delete`, 키 기반)를 통해 이뤄진다. MVP는 이 인터페이스의 유일한 구현체로 `LocalFileStorage`(서버 로컬 폴더 저장)를 쓴다.

## Considered Options

- **ProductService가 직접 파일 I/O 수행**: 지금 당장은 더 간단하지만, `Product`/`ProductService`/컨트롤러 여러 곳에 "로컬 디스크에 저장돼 있다"는 가정이 흩어져 박힌다. 나중에 클라우드 스토리지로 옮기려면 이 가정이 스며든 모든 지점을 찾아 고쳐야 한다.
- **FileStorage 추상화 (채택)**: 스토리지 종류를 갈아끼우는 지점을 인터페이스 하나로 좁힌다. `Product.photoKey`는 파일 경로가 아니라 저장소가 발급한 키이므로, DB 스키마 자체가 로컬 파일시스템이라는 사실을 몰라도 된다.

## Consequences

지금은 구현체가 `LocalFileStorage` 하나뿐이다 — 실제로 쓸 두 번째 구현체(예: S3)가 생기기 전까지는 이 추상화가 "지금 당장 필요 없는 일반화"처럼 보일 수 있지만, 사용자가 명시적으로 "나중에 클라우드로 교체 가능하게" 요청했으므로 의도된 설계다. 나중에 클라우드 구현체를 추가할 때 `Product`/`ProductService`는 건드릴 필요가 없어야 한다 — 그렇지 않다면 이 추상화가 제 역할을 못 한 것이다.
