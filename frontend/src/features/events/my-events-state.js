// 내 행사 목록 상태를 바꾸는 순수 함수 (use-my-events.js에서 setState 갱신 함수로 씀, NSU-125)
// 상태: { scope, events, status, error }
//   scope: 이 목록이 누구의 것인지 ('불러오는지:계정'). 계정이 바뀌면 새 scope로 새로 시작
// 응답이 도착했을 때 상태에 저장된 scope와 요청을 시작한 scope를 비교해, 다른 계정의 결과는 버립니다.
// (실행 테스트: node --test src/features/events/my-events-state.test.js)

export const scopeOf = (enabled, owner) => `${enabled}:${owner ?? ''}`;

// 새 범위의 처음 상태 (불러올 때는 'loading', 아니면 'idle')
export const startState = (enabled, scope) => ({
  scope,
  events: [],
  status: enabled ? 'loading' : 'idle',
  error: null,
});

// 목록 조회 결과. 요청한 범위가 지금 범위와 다르면 그대로 둠
export const listLoaded = (scope, events) => (prev) =>
  prev.scope === scope ? { ...prev, events, status: 'ready', error: null } : prev;

export const listFailed = (scope, error) => (prev) =>
  prev.scope === scope ? { ...prev, status: 'error', error } : prev;

// 등록·수정·삭제 결과 반영. 요청을 시작한 범위가 지금 범위와 다르면 새 계정 목록에 넣지 않음
export const applyChange = (scope, change) => (prev) =>
  prev.scope === scope ? { ...prev, events: change(prev.events) } : prev;
