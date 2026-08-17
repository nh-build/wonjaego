Status: ready-for-agent

# AI 상품명 추천

## Problem Statement

셀러가 상품을 등록할 때, 어떤 상품명을 붙여야 눈에 잘 띄고 잘 팔릴지 매번 고민해야 한다. 특히 여러 채널에 같은 상품을 다른 톤으로 올리고 싶을 때(심플하게, 러블리하게 등) 여러 버전의 이름을 스스로 생각해내는 건 번거롭다.

## Solution

상품 등록 화면에 키워드(예: 니트, 겨울, 오버핏) 몇 개를 입력하면, AI가 심플/러블리/섹시/캐주얼 4가지 컨셉으로 각각 상품명 후보를 하나씩 만들어 보여준다. 마음에 드는 이름을 클릭하면 상품명 입력칸에 바로 채워지고, "새로 만들기" 버튼으로 다시 뽑을 수도 있다. 이 기능은 상품명 입력을 돕는 보조 도구일 뿐이며, 쓰지 않고 직접 타이핑해도 등록 흐름은 그대로 동작한다. AI 호출이 실패해도 화면은 죽지 않고 다시 시도하라는 안내만 뜬다.

## User Stories

1. As a 셀러, I want 상품 등록 화면에서 키워드 몇 개를 콤마로 구분해 입력할 수 있길, so that 내가 생각하는 상품의 특징을 AI에게 간단히 전달할 수 있다.
2. As a 셀러, I want 키워드를 입력하고 "추천받기" 버튼을 누르면 4가지 컨셉(심플/러블리/섹시/캐주얼)별 상품명 후보를 볼 수 있길, so that 여러 톤의 이름을 한 번에 비교해볼 수 있다.
3. As a 셀러, I want 각 후보 옆에 어떤 컨셉인지 라벨이 붙어 있길, so that 내가 원하는 분위기의 이름을 바로 골라낼 수 있다.
4. As a 셀러, I want 마음에 드는 후보를 클릭하면 상품명 입력칸에 자동으로 채워지길, so that 이름을 다시 타이핑할 필요가 없다.
5. As a 셀러, I want 자동으로 채워진 상품명을 다시 직접 수정할 수 있길, so that AI가 만든 이름을 그대로 쓰지 않고 조금 다듬어 쓸 수 있다.
6. As a 셀러, I want "새로 만들기" 버튼으로 같은 키워드에 대해 새로운 4개 후보를 다시 뽑을 수 있길, so that 처음 결과가 마음에 안 들면 다른 버전을 더 볼 수 있다.
7. As a 셀러, I want 추천을 기다리는 동안 로딩 상태를 볼 수 있길, so that 화면이 멈춘 건지 요청이 처리 중인 건지 알 수 있다.
8. As a 셀러, I want 추천 요청이 진행 중일 땐 버튼이 비활성화되길, so that 실수로 여러 번 눌러 중복 요청을 보내지 않는다.
9. As a 셀러, I want AI 호출이 실패하면 에러 메시지와 "다시 시도" 안내를 보길, so that 뭔가 잘못됐다는 걸 알고 다시 시도할 수 있다.
10. As a 셀러, I want AI 호출이 실패해도 상품 등록 화면 자체는 계속 정상 동작하길, so that 이 부가 기능 하나 때문에 상품을 등록하지 못하는 일이 없다.
11. As a 셀러, I want 실패 후에도 내가 입력했던 키워드가 그대로 남아있길, so that 재시도할 때 키워드를 다시 입력하지 않아도 된다.
12. As a 셀러, I want 키워드를 하나도 입력하지 않으면 추천 요청을 보낼 수 없길, so that 의미 없는 요청으로 시간을 낭비하지 않는다.
13. As a 셀러, I want 이 기능을 아예 쓰지 않고 상품명을 직접 입력해도 등록이 문제없이 되길, so that AI 추천은 어디까지나 선택 사항으로 남는다.
14. As a 셀러, I want 로그인하지 않은 상태로는 추천 기능을 쓸 수 없길, so that 내 계정과 무관한 사람이 내 API 사용량을 축내지 않는다.
15. As a 셀러, I want 생성된 후보들이 페이지를 벗어나면 사라지길(별도로 저장되지 않음), so that 상품 데이터에 불필요한 정보가 쌓이지 않는다.
16. As a 셀러, I want 사진 업로드나 옵션 그룹 입력과 이 기능이 서로 방해하지 않길, so that 등록 화면의 다른 입력 흐름이 그대로 유지된다.
17. As the app 운영자(개발자), I want API 키가 소스코드에 하드코딩되지 않고 환경변수로만 관리되길, so that 키가 실수로 저장소에 커밋되는 일이 없다.
18. As the app 운영자, I want API 키가 설정되지 않은 상태로도 앱 전체가 정상 기동되길, so that 이 기능 하나의 설정 누락이 서비스 전체 장애로 이어지지 않는다.
19. As the app 운영자, I want AI 호출 실패 시 서버 로그에 원인을 진단할 수 있는 정보가 남길, so that 사용자에게는 일반적인 안내만 보여주면서도 나는 문제를 추적할 수 있다.

## Implementation Decisions

- **패키지**: `com.wonjaego.ai` 신설. `NamingConcept` enum(`SIMPLE`/`LOVELY`/`SEXY`/`CASUAL`, 고정 4종·고정 순서, 한글 표시 라벨은 심플/러블리/섹시/캐주얼)과 `NameSuggestion`(concept + name), `NameSuggestionClient` 인터페이스(`List<NameSuggestion> suggest(List<String> keywords)`, 항상 4개를 컨셉 순서대로 반환)를 둔다.
- **`AnthropicNameSuggestionClient`**: `NameSuggestionClient`의 유일한 실제 구현체. Spring `RestClient`(`RestClient.Builder` 자동 구성 활용, 새 의존성 추가 없음)로 Anthropic Messages API를 호출한다. 모델은 Claude Haiku 4.5(`claude-haiku-4-5-20251001`). 응답은 자유 텍스트 파싱이 아니라 **Tool Use 강제 호출**(하나의 tool만 정의하고 `tool_choice`로 강제)로 받아, 컨셉별 이름 4개가 담긴 JSON을 안정적으로 파싱한다. 호출 실패(네트워크 오류, 비2xx 응답, 예상과 다른 tool 응답 구조 등)는 모두 하나의 예외(`NameSuggestionFailedException`)로 감싸 던진다.
- **설정/시크릿**: `application.properties`에 `wonjaego.ai.anthropic.api-key=${ANTHROPIC_API_KEY:}`(빈 문자열 기본값)로 환경변수를 주입한다. 이 프로젝트 최초의 시크릿 설정값이며, 새 로컬 설정 파일이나 `.gitignore` 항목은 추가하지 않고 개발자가 셸에서 `ANTHROPIC_API_KEY`를 export한 뒤 실행하는 것을 전제로 한다. 키가 비어 있어도 앱은 정상 기동하며, 실제로 추천 기능을 호출했을 때만 실패로 이어진다(다른 기능에는 영향 없음).
- **엔드포인트**: `POST /products/name-suggestions` — `@AuthenticationPrincipal MemberPrincipal`로 로그인만 요구(등록 전 단계라 특정 Product 소유권 검사는 해당 없음). 요청 바디는 콤마로 구분된 키워드 원문 문자열 하나(기존 옵션 값 파싱과 동일하게 서버에서 trim·중복 제거). 성공 시 컨셉 4개 각각의 한글 라벨과 이름을 담은 JSON을 반환한다. 실패 시(`NameSuggestionFailedException`) `@ResponseStatus(BAD_GATEWAY)`로 매핑해 비2xx 응답을 내려주고, 서버 로그에는 실패 원인을 상세히 남긴다.
- **CSRF**: 이 앱은 세션 기반 폼 로그인 + CSRF 보호를 쓰고 있으므로, 새 JSON 엔드포인트도 CSRF 토큰이 필요하다. 레이아웃의 `<head>`에 CSRF 토큰/헤더명을 담은 `<meta>` 태그를 추가하고, JS의 `fetch()` 호출이 이를 읽어 헤더에 실어 보낸다.
- **등록 화면 UI**: `products/list.html`의 상품명 입력칸 근처에, 옵션 그룹 fieldset과 같은 스타일의 카드 섹션을 추가한다. 콤마 구분 키워드 입력칸 + "AI 상품명 추천받기" 버튼(요청 진행 중엔 비활성화·로딩 표시, 첫 성공 이후엔 라벨이 "새로 만들기"로 바뀜) + 컨셉별 라벨이 붙은 4개의 클릭 가능한 후보 카드. 후보 클릭 시 자바스크립트로 상품명 입력칸 값만 갱신한다(페이지 새로고침 없음, 기존 옵션 UX 스크립트와 동일하게 `<script th:inline="none">` IIFE, 순수 바닐라 JS·fetch). 실패 시 카드 자리에 에러 문구 + "다시 시도" 안내를 표시하고, 키워드 입력값은 그대로 유지한다.
- **범위**: 상품 등록 화면(`products/list.html`)에만 적용한다. 상품 수정 화면(`edit.html`)은 이번 범위에 포함하지 않는다.

## Testing Decisions

- 기존과 동일한 **단일 컨트롤러 seam**(MockMvc 풀스택 + 실제 test H2)으로 검증한다. 새 요소는 `NameSuggestionClient`를 대체하는 `@TestConfiguration` + `@Primary` stub 빈 하나뿐이며(`com.wonjaego.testsupport` 아래), 실제 Anthropic API를 테스트에서 호출하지 않는다.
- stub은 평상시 키워드에는 컨셉 4개에 대한 고정 응답을 돌려주고, 특정 sentinel 키워드가 포함되면 `NameSuggestionFailedException`을 던지도록 만들어 실패 경로(비2xx 응답, 에러 처리)도 같은 seam 안에서 검증한다.
- 테스트 대상: 정상 키워드 입력 시 4개 컨셉 응답, 키워드 없이 요청 시 거부, 비로그인 요청 시 리다이렉트/거부, AI 호출 실패 시 graceful한 에러 응답, (백엔드가 컨셉 순서를 항상 SIMPLE/LOVELY/SEXY/CASUAL 순으로 반환하는지).
- 실제 `AnthropicNameSuggestionClient`(RestClient로 진짜 Claude API를 호출하는 부분)는 이 seam으로 커버되지 않는다 — `FileStorage`/`LocalFileStorage`와 마찬가지로, 실제 네트워크 호출 자체는 자동화된 테스트 대상이 아니라 dev 프로필 부트 + 실제 API 키로 수동 검증하는 대상이다.
- 자바스크립트(후보 클릭 시 상품명 채우기, 로딩/비활성화 상태, 실패 시 문구 표시)는 서버 사이드 테스트 대상이 아니다 — 옵션 UX 티켓과 동일하게 렌더링된 마크업 구조 확인으로 대체한다.
- Prior art: `ProductPhotoTest`(같은 컨트롤러에 새 엔드포인트를 추가하고 외부 자원 접근을 다루는 최근 사례), `ProductCrudTest`(ownership/입력 검증 패턴).

## Out of Scope

- 상품 수정 화면(`edit.html`)에는 이번에 추가하지 않는다.
- 컨셉당 여러 개(2개 이상) 후보 생성 — 컨셉당 정확히 1개.
- 생성된 후보의 저장/이력 조회 — 완전히 휘발성이며 Product 엔티티에는 어떤 필드도 추가되지 않는다.
- 서버 단 사용량/속도 제한(rate limiting) — 클라이언트 측 "요청 중 버튼 비활성화" 정도만 두고, 실제 남용 방지는 범위 밖으로 미룬다.
- Claude 외 다른 AI 공급자 지원, 스트리밍 응답.
- 키워드 자동完성/추천, 상품명 외 다른 필드(가격 등)에 대한 AI 추천.

## Further Notes

- `docs/adr/0003-product-photo-storage-abstraction.md`와 같은 자리에 새 ADR(`0004-ai-name-suggestion-client.md` 예정)을 추가해 RestClient 선택, Claude Haiku 4.5 선택, Tool Use 강제 구조화 응답, 환경변수 기반 시크릿 관리 결정을 기록한다.
- `CONTEXT.md`에 "AI 상품명 추천" 용어(컨셉 4종 고정: 심플/러블리/섹시/캐주얼)를 짧게 추가한다.
- 이 기능은 이 프로젝트에서 처음으로 (1) 외부 HTTP 클라이언트, (2) 환경변수 기반 시크릿, (3) 테스트용 `@TestConfiguration`/`@Primary` 대체 빈을 도입한다 — 기존 코드베이스에 직접적인 선례가 없으므로 구현 시 이 세 가지가 이후 다른 기능에서도 재사용 가능한 패턴으로 자리잡도록 신경 쓴다.
