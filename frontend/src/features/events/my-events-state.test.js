import test from 'node:test';
import assert from 'node:assert/strict';
import { applyChange, listFailed, listLoaded, scopeOf, startState } from './my-events-state.js';

const A = scopeOf(true, 'user-a');
const B = scopeOf(true, 'user-b');
const EVENT = { id: 'evt-1', name: '가을 축제' };

test('계정 A로 시작한 등록이 계정 B로 바뀐 뒤 끝나면 B의 목록에 넣지 않는다', () => {
  // 1) A의 목록 2) 계정이 B로 바뀜(렌더 중 새 범위로 시작) 3) A의 등록 응답 도착
  const before = { ...startState(true, A), status: 'ready' };
  const switched = startState(true, B);
  assert.equal(before.scope, A);
  const state = applyChange(A, (events) => [...events, EVENT])(switched);
  assert.deepEqual(state.events, []);
  assert.equal(state.scope, B);
});

test('같은 계정의 등록 결과는 목록에 넣는다', () => {
  const state = applyChange(A, (events) => [...events, EVENT])({ ...startState(true, A), status: 'ready' });
  assert.deepEqual(state.events, [EVENT]);
});

test('다른 계정 범위의 목록 조회 결과와 실패는 버린다', () => {
  const b = startState(true, B);
  assert.equal(listLoaded(A, [EVENT])(b), b);
  assert.equal(listFailed(A, new Error('x'))(b), b);
  assert.deepEqual(listLoaded(B, [EVENT])(b).events, [EVENT]);
  assert.equal(listFailed(B, new Error('x'))(b).status, 'error');
});

test('불러오지 않는 범위(서버 모드 비로그인)는 idle로 시작한다', () => {
  assert.equal(startState(false, scopeOf(false, null)).status, 'idle');
  assert.equal(startState(true, A).status, 'loading');
});
