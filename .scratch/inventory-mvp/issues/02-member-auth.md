# 02 — 회원가입/로그인/로그아웃/내 정보

**What to build:** 방문자가 username/password/상호명으로 가입하고, 로그인해서 세션을 유지하며, 로그아웃하고, 로그인 상태에서 `/me`로 자기 정보(username, 상호명)를 확인할 수 있다. 이후 모든 화면은 이 인증에 기대어 로그인한 Member로 스코프된다.

**Blocked by:** 01 — 프로젝트 부트스트랩 & 베이스 레이아웃

**Status:** ready-for-agent

- [x] `Member extends BaseEntity`: `username`(unique, not null), `password`(BCrypt 해시로 저장), `businessName`(not null).
- [x] `/signup`에서 username/password/상호명으로 가입할 수 있고, 이미 존재하는 username이면 거부되고 에러가 화면에 표시된다.
- [x] Spring Security 폼 로그인(`/login`)으로 로그인하며, `UserDetailsService` 구현체가 `Member`를 조회해 인증한다. 비로그인 상태로 보호된 화면에 접근하면 로그인 화면으로 리다이렉트된다.
- [x] `/logout`으로 세션이 종료되고 이후 보호된 화면 접근 시 다시 로그인 화면으로 리다이렉트된다.
- [x] `/me`는 로그인한 Member 본인의 username/상호명만 보여주며, 비로그인 상태로는 접근할 수 없다.
- [x] 별도 Role 엔티티 없이, 인증된 모든 Member는 동일한 `ROLE_SELLER` 권한으로 취급된다.
- [x] MockMvc + 실제 test H2로 가입/중복가입 거부/로그인/로그아웃/`/me` 접근(로그인·비로그인 각각) 흐름을 검증하는 테스트가 있다.
