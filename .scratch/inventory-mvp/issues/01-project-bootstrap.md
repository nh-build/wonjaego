# 01 — 프로젝트 부트스트랩 & 베이스 레이아웃

**What to build:** `back/` 아래 Spring Boot(4.x, JDK 25, Gradle Kotlin DSL) 프로젝트를 초기화하고, 이후 모든 티켓이 올라탈 공통 기반(패키지 구조, 감사 필드가 있는 베이스 엔티티, dev/test 프로필, 베이스 레이아웃)을 갖춘다. 로그인 등 기능은 아직 없고, 앱이 뜨고 홈 화면이 렌더링되는 것까지가 범위다.

**Blocked by:** 없음 — 바로 시작 가능

**Status:** ready-for-agent

- [x] 루트 패키지 `com.wonjaego`, 메인 클래스 `com.wonjaego.WonjaegoApplication`에 `@EnableJpaAuditing`이 붙어 있고 `gradlew bootRun`으로 앱이 기동된다.
- [x] `spring.jpa.open-in-view=false`로 설정돼 있다.
- [x] `BaseEntity`(`@MappedSuperclass`)가 `id`, `createdAt`(`@CreatedDate`), `updatedAt`(`@LastModifiedDate`)를 갖고, `@EntityListeners(AuditingEntityListener.class)`가 적용돼 있다.
- [x] `dev` 프로필: 파일 기반 H2, `ddl-auto=update`, h2-console 활성화. `test` 프로필: 인메모리 H2, `ddl-auto=create`.
- [x] devtools, lombok, spring-data-jpa, validation, spring-security, thymeleaf, thymeleaf-extras-springsecurity6 의존성이 추가돼 있다(security 적용은 02에서 진행하되, 의존성은 여기서 준비).
- [x] Thymeleaf 베이스 레이아웃(공통 헤더/네비 뼈대)이 있고, `/`에 접속하면 placeholder 홈 화면이 200으로 렌더링된다.
- [x] `@SpringBootTest(webEnvironment = MOCK) + @AutoConfigureMockMvc` 기반 스모크 테스트가 홈 화면 200 응답을 검증한다.
