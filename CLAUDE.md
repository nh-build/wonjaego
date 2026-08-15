# 작업지침
- 한국어 사용
- 일반적인 작업 흐름 : /grill-with-docs, /to-spec, /to-tickets, /implement
  - /implement 는 내부적으로 /tdd 로 구현하고 커밋 전에 /code-review 를 수행
- 버그 발생 시 /diagnosing-bugs, 아키텍처 개선이 필요할 때만 /improve-codebase-architecture
- 최대한 mattpocock 스킬들을 활용

## Agent skills

### Issue tracker

이슈는 `.scratch/<feature-slug>/` 아래 로컬 마크다운 파일로 관리됩니다. See `docs/agents/issue-tracker.md`.

### Triage labels

기본 5개 표준 라벨(`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`)을 그대로 사용합니다. See `docs/agents/triage-labels.md`.

### Domain docs

single-context — repo 루트에 `CONTEXT.md` + `docs/adr/`. See `docs/agents/domain.md`.
