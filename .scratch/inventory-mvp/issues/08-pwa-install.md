# 08 — PWA: 홈 화면 설치 지원

**What to build:** 모바일 사용자가 원재고를 브라우저 홈 화면에 설치해 앱처럼 열 수 있다. 정적 자산은 최소한의 서비스워커로 캐시되지만, 오프라인 데이터 입력/동기화는 다루지 않는다.

**Blocked by:** 01 — 프로젝트 부트스트랩 & 베이스 레이아웃

**Status:** ready-for-agent

- [x] `manifest.json`(앱 이름, 아이콘, `display: standalone`)이 제공되고 베이스 레이아웃에 링크돼 있다.
- [x] 최소 서비스워커가 등록되어 정적 자산(CSS/JS/아이콘 등)을 캐시 우선 전략으로 서빙한다. API/화면 응답이나 데이터는 캐시하지 않는다.
- [x] Tailwind CSS + DaisyUI는 Node 빌드 파이프라인 없이 CDN(Play CDN)으로 로드된다.
- [x] 모바일 브라우저(또는 Lighthouse 등 PWA 검사 도구)에서 "홈 화면에 추가" 요건(manifest + service worker + HTTPS 또는 localhost)을 충족한다.
- [x] 서비스워커 등록으로 인해 기존 화면(로그인 필요 여부, 리다이렉트 등)의 동작이 깨지지 않는다 — 관련 기존 MockMvc 테스트가 그대로 통과한다.
