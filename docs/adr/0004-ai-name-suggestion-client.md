---
status: accepted
---

# AI 상품명 추천은 RestClient + Claude Haiku 4.5 + Tool Use 강제 호출로 구현한다

상품 등록 화면의 "AI 상품명 추천" 기능은 `NameSuggestionClient` 인터페이스 뒤에서 `AnthropicNameSuggestionClient`(유일한 구현체)가 Anthropic Messages API를 호출한다. 이 프로젝트가 외부 HTTP API를 호출하는 것도, 시크릿을 설정으로 관리하는 것도, 테스트에서 외부 서비스를 대체 빈으로 바꾸는 것도 모두 처음이라 아래 네 가지를 함께 결정했다.

## Considered Options — HTTP 클라이언트

- **Anthropic 공식 Java SDK 추가**: 타입 안전하고 재시도/스트리밍을 대신 처리해주지만, 이 프로젝트는 지금까지 Spring Boot 스타터 외의 서드파티 클라이언트 의존성을 추가한 적이 없다(프론트엔드도 빌드 파이프라인 없이 CDN만 사용). 새 의존성 하나를 위해 이 원칙을 깨야 한다.
- **Spring `RestClient` (채택)**: `spring-boot-starter-webmvc`가 이미 끌어오는 `spring-web`에 포함돼 있어 새 의존성이 필요 없다. 다만 이 프로젝트의 Boot 4.1 스타터 구성(`spring-boot-starter-webmvc`, `spring-web`)에는 `RestClient.Builder` 자동 구성 빈이 없었다 — `RestClient.builder()`를 직접 호출해 구성한다.
- **JDK 내장 `java.net.http.HttpClient`**: 의존성은 필요 없지만 Spring 관용구(RestClient의 request/response 매핑, 예외 계층)를 못 쓰고 보일러플레이트가 늘어난다.

## Considered Options — 모델

- **Claude Sonnet 5**: 품질은 더 높지만 짧은 상품명 생성에는 과한 비용/지연.
- **Claude Haiku 4.5 (`claude-haiku-4-5-20251001`, 채택)**: 빠르고 저렴하며 이 작업(키워드 몇 개로 짧은 창작 문구 4개 생성)엔 충분한 품질.

## Considered Options — 구조화된 응답

- **프롬프트로 "JSON만 출력해줘" 요청 후 텍스트 파싱**: 구현은 간단하지만 모델이 설명을 덧붙이거나 형식을 어길 여지가 있어 파싱이 깨지기 쉽다.
- **Tool Use 강제 호출 (채택)**: 컨셉 4개(SIMPLE/LOVELY/SEXY/CASUAL)를 필드로 갖는 tool 스키마 하나를 정의하고 `tool_choice`로 강제 호출시킨다. 모델이 자유 텍스트로 답할 여지가 없어 파싱 실패 가능성이 훨씬 낮다. 스키마는 `NamingConcept` enum에서 직접 생성해, 컨셉이 추가되어도 스키마와 파싱 로직이 어긋나지 않는다.

## Considered Options — 시크릿 관리

- **새 로컬 전용 설정 파일(`application-local.properties`, `.env` 등) 도입**: 이 프로젝트 최초의 로컬 시크릿 컨벤션이 되지만, 별도 파일/로딩 메커니즘을 새로 만들어야 한다.
- **환경변수 플레이스홀더 (채택)**: `wonjaego.ai.anthropic.api-key=${ANTHROPIC_API_KEY:}`로 표준 Spring 프로퍼티 플레이스홀더만 쓴다. 빈 문자열 기본값을 둬서, 키가 없어도 앱 전체는 정상 기동하고 실제로 추천 기능을 호출했을 때만 실패로 이어진다. 개발자는 실행 전에 셸에서 `ANTHROPIC_API_KEY`를 export한다.

## Considered Options — 테스트 대체

- **Mockito `@MockBean`**: 이 프로젝트는 지금까지 Mockito를 테스트에 쓴 적이 없다(`FileStorage`도 테스트에서 진짜 로컬 디스크 구현체를 그대로 씀).
- **`@TestConfiguration` + `@Primary` stub 빈 (채택)**: `NameSuggestionClient`를 구현하는 "진짜 구현체 하나 더"를 테스트 소스에 두고 `@Primary`로 우선시킨다. `FileStorage`처럼 "진짜 객체를 쓰되 정체가 테스트용"이라는 정신을 유지하면서, 실제 네트워크 호출 없이 성공/실패(sentinel 키워드) 양쪽 경로를 같은 MockMvc 시더에서 검증한다.

## Update (2026-08-17) — 고정 컨셉을 사용자 입력 기반 자유 생성으로 전환

당초 "구조화된 응답" 절에서 채택한 설계(심플/러블리/섹시/캐주얼 4개 컨셉을 tool 스키마의 고정 필드로 강제)를 폐기했다. 사용자가 실제로 원한 것은 고정된 4가지 스타일 중 고르는 것이 아니라, 본인이 입력한 "포인트 단어"(필수)와 "컨셉/무드"(선택, 자유 텍스트)를 AI가 조합해 상품명 후보를 만들어주는 것이었다.

- **변경된 부분**: tool 스키마를 "컨셉별 고정 필드 4개"에서 "문자열 배열(`names`) 1개, `minItems`/`maxItems`로 정확히 5개 강제"로 바꿨다. `NamingConcept`/`NameSuggestion` 타입은 삭제했고, `NameSuggestionClient.suggest(keywords, mood)`가 컨셉 라벨 없이 `List<String>`을 반환한다.
- **유지된 부분**: RestClient, Claude Haiku 4.5, Tool Use 강제 호출(자유 텍스트 파싱이 아님), 환경변수 시크릿 관리, `@TestConfiguration`/`@Primary` 테스트 대체 빈 — 위 네 가지 결정과 그 근거는 여전히 유효하다.
- 이 변경으로 "컨셉이 추가되어도 스키마와 파싱 로직이 어긋나지 않는다"는 기존 근거는 더 이상 적용되지 않는다(컨셉 자체가 없어졌으므로) — 대신 이제는 스키마의 `names` 배열과 파싱 로직의 개수 검증(`size() == 5`)이 어긋나지 않도록 `SUGGESTION_COUNT` 상수 하나로 묶어둔다.

## Consequences

- 이 기능을 계기로 이 프로젝트에 (1) 외부 HTTP 클라이언트 호출, (2) 환경변수 기반 시크릿, (3) `@TestConfiguration`/`@Primary` 테스트 대체 빈이라는 세 가지 패턴이 처음 자리잡았다. 이후 다른 외부 API 연동이 필요해지면 이 결정들을 선례로 재사용할 수 있어야 한다.
- 실제 `AnthropicNameSuggestionClient`(진짜 Claude API를 호출하는 부분)는 자동화된 테스트로 커버되지 않는다 — `LocalFileStorage`와 마찬가지로 dev 프로필 부트 + 실제 API 키로만 수동 검증한다.
- 나중에 다른 AI 공급자로 바꾸려면 `NameSuggestionClient`의 새 구현체 하나만 추가하면 되고, 컨트롤러/화면 코드는 건드릴 필요가 없어야 한다 — 그렇지 않다면 이 추상화가 제 역할을 못 한 것이다.
