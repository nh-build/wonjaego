# 04 — 판매채널(SalesChannel) CRUD

**What to build:** 로그인한 Member가 스마트스토어/에이블리/지그재그 등 자신이 판매하는 채널을 자유롭게 등록·조회·수정할 수 있다. 삭제 액션은 화면에 존재하되 실제 제약(Movement 참조 시 거부)은 05에서 완성된다.

**Blocked by:** 02 — 회원가입/로그인/로그아웃/내 정보

**Status:** ready-for-agent

- [ ] `SalesChannel extends BaseEntity`: `member`(`@ManyToOne`), `name`(해당 Member 범위 내 unique).
- [ ] `/channels`에서 채널 이름을 직접 입력해 등록할 수 있다(고정 목록이 아님).
- [ ] 같은 Member 안에서 채널 이름이 중복되면 등록/수정이 거부되고 에러가 화면에 표시된다.
- [ ] `/channels`에서 로그인한 Member 소유 채널 목록만 조회된다.
- [ ] `/channels/{id}/edit`에서 채널 이름을 수정할 수 있다. 다른 Member 소유 채널의 id로 접근하면 404가 반환된다.
- [ ] 삭제 액션 UI/엔드포인트는 존재하지만, 이 티켓 시점에는 Movement가 없으므로 항상 삭제가 성공한다(참조 제약은 05에서 추가).
- [ ] MockMvc + 실제 test H2로 등록/이름 중복 거부/목록 스코핑/수정/404 흐름을 검증하는 테스트가 있다.
