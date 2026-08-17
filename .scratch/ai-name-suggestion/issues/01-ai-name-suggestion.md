# 01 — AI 상품명 추천 (키워드 입력 → 4개 컨셉 후보 생성 → 선택/재생성)

**What to build:** 셀러가 상품 등록 화면에서 키워드 몇 개를 콤마로 구분해 입력하고 "추천받기"를 누르면, Claude API가 심플/러블리/섹시/캐주얼 4가지 컨셉으로 상품명 후보를 하나씩 생성해 보여준다. 마음에 드는 후보를 클릭하면 상품명 입력칸에 바로 채워지고, "새로 만들기" 버튼으로 다시 뽑을 수 있다. AI 호출이 실패해도 화면은 죽지 않고 에러 안내와 함께 다시 시도할 수 있으며, 이 기능을 쓰지 않고 상품명을 직접 입력해도 등록 흐름은 그대로 동작한다.

**Blocked by:** None — can start immediately

**Status:** ready-for-agent

- [x] `com.wonjaego.ai` 패키지에 `NamingConcept`(SIMPLE/LOVELY/SEXY/CASUAL, 고정 4종·고정 순서, 한글 표시 라벨), `NameSuggestion`(concept + name), `NameSuggestionClient` 인터페이스(`List<NameSuggestion> suggest(List<String> keywords)`, 항상 4개를 컨셉 순서대로 반환), `NameSuggestionFailedException`이 있다.
- [x] `AnthropicNameSuggestionClient`(`NameSuggestionClient`의 유일한 구현체)가 Spring `RestClient`로 Anthropic Messages API(Claude Haiku 4.5, `claude-haiku-4-5-20251001`)를 호출한다. 응답은 자유 텍스트 파싱이 아니라 Tool Use 강제 호출로 받아 안정적으로 파싱한다. 호출 실패(네트워크 오류, 비2xx, 예상과 다른 응답 구조)는 모두 `NameSuggestionFailedException`으로 감싼다.
- [x] `wonjaego.ai.anthropic.api-key=${ANTHROPIC_API_KEY:}`로 환경변수에서 API 키를 주입한다. 키가 비어 있어도 앱은 정상 기동하며, 실제로 추천 기능을 호출했을 때만 실패로 이어진다(다른 기능에는 영향 없음).
- [x] `POST /products/name-suggestions` 엔드포인트: `@AuthenticationPrincipal MemberPrincipal`로 로그인 필요(비로그인 요청은 거부/리다이렉트). 요청 바디는 콤마로 구분된 키워드 원문 문자열이며, 서버에서 trim·중복 제거 후 처리한다(빈 키워드 목록은 거부). 성공 시 컨셉 4개 각각의 한글 라벨과 이름을 담은 JSON을 반환한다. 실패 시 `NameSuggestionFailedException` → `@ResponseStatus(BAD_GATEWAY)`로 매핑하고, 서버 로그에는 실패 원인을 상세히 남긴다.
- [x] 레이아웃의 `<head>`에 CSRF 토큰/헤더명을 담은 `<meta>` 태그를 추가하고, JS `fetch()` 호출이 이를 읽어 헤더에 실어 보낸다.
- [x] `products/list.html`의 상품명 입력칸 근처에 키워드 입력 + "AI 상품명 추천받기" 버튼(요청 중 비활성화·로딩 표시, 첫 성공 이후 "새로 만들기"로 라벨 변경) + 컨셉별 라벨이 붙은 4개의 클릭 가능한 후보 카드가 있다. 후보 클릭 시 페이지 새로고침 없이 상품명 입력칸 값만 갱신되며, 이후 직접 수정도 가능하다.
- [x] AI 호출 실패 시 후보 카드 자리에 에러 문구와 "다시 시도" 안내가 표시되고, 입력했던 키워드는 그대로 유지된다.
- [x] 키워드를 하나도 입력하지 않으면 추천 요청을 보낼 수 없다.
- [x] MockMvc + 실제 test H2 seam(기존 `ProductPhotoTest`/`ProductCrudTest`와 동일한 컨트롤러 통합 테스트 패턴)으로 검증한다. `com.wonjaego.testsupport` 아래 `NameSuggestionClient`를 대체하는 `@TestConfiguration` + `@Primary` stub 빈을 두어, 실제 Anthropic API 호출 없이 성공 응답(컨셉 4개 고정 순서)과 sentinel 키워드를 통한 실패 응답(비2xx, graceful 에러) 양쪽을 같은 시더에서 검증한다. 비로그인 요청 거부, 빈 키워드 거부도 함께 테스트한다.
- [x] `docs/adr/0004-ai-name-suggestion-client.md`에 RestClient 선택, Claude Haiku 4.5 선택, Tool Use 강제 구조화 응답, 환경변수 기반 시크릿 관리 결정을 기록한다.
