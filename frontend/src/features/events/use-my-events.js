// 내 행사 목록 훅 (NSU-125): 화면은 이 훅으로 목록을 읽고 등록·수정·삭제합니다.
// 실제 저장은 events-api.js가 모드(server·demo)에 따라 맡습니다.
// enabled: 목록을 불러올지 (서버 모드에서는 로그인한 행사 관계자일 때만)
// owner: 로그인한 계정이 바뀌면 목록을 다시 불러오기 위한 값 (userId 등)
// 응답을 목록에 넣을지는 상태에 함께 저장한 계정 범위(scope)로 setState 갱신 함수 안에서 정합니다 (my-events-state.js).
import { useCallback, useEffect, useRef, useState } from 'react';
import { eventsApi as defaultApi } from './events-api.js';
import { applyChange, listFailed, listLoaded, scopeOf, startState } from './my-events-state.js';
import { SETTLED_CODES, registrationKey as defaultKey } from './registration-key.js';

export default function useMyEvents({ enabled = true, owner = null, api = defaultApi, keyStore = defaultKey } = {}) {
  // 이 렌더의 계정 범위. 등록·수정·삭제는 시작할 때 이 값을 잡아 둠
  const scope = scopeOf(enabled, owner);
  // { scope, events, status: 'idle'|'loading'|'ready'|'error', error }
  const [state, setState] = useState(() => startState(enabled, scope));
  // 다시 불러오기를 누를 때마다 올림
  const [version, setVersion] = useState(0);
  // 계정이 바뀌면 예전 목록을 비우고 새 범위로 시작 (렌더 중 상태 맞추기)
  if (state.scope !== scope) setState(startState(enabled, scope));

  // 등록·수정·삭제가 끝날 때마다 올림. 그 전에 시작한 목록 조회는 결과를 버리고 다시 조회
  const changes = useRef(0);

  useEffect(() => {
    if (!enabled) return undefined;
    const listScope = scopeOf(enabled, owner);
    // 늦게 도착한 예전 응답이 새 목록을 덮지 않게
    let stale = false;
    const startedAt = changes.current;
    api.list().then(
      (events) => {
        if (stale) return;
        // 조회 중에 등록·수정·삭제가 끝났으면 그 결과가 빠진 목록이라 다시 받음
        if (changes.current !== startedAt) setVersion((v) => v + 1);
        else setState(listLoaded(listScope, events));
      },
      (error) => {
        if (!stale) setState(listFailed(listScope, error));
      },
    );
    return () => {
      stale = true;
    };
  }, [api, enabled, owner, version]);

  const reload = useCallback(() => {
    if (!enabled) return;
    setState((prev) => ({ ...prev, status: 'loading', error: null }));
    setVersion((v) => v + 1);
  }, [enabled]);

  // 결과 반영: 요청을 시작한 범위와 지금 상태의 범위가 같을 때만 (갱신 함수 안에서 비교)
  function commit(startScope, change) {
    changes.current += 1;
    setState(applyChange(startScope, change));
  }

  // 등록: 등록 키는 결과가 확정될 때만 버림 (성공·이미 등록됨·없음). 나머지 실패는 같은 키로 다시 보냄
  async function create(event, { onRetry } = {}) {
    const startScope = scope;
    try {
      const saved = await api.create(event, { key: keyStore.current(), onRetry });
      // 등록 키는 계정과 관계없이 결과가 확정되면 버림
      keyStore.discard();
      commit(startScope, (events) => [...events.filter((e) => e.id !== saved.id), saved]);
      return saved;
    } catch (err) {
      if (SETTLED_CODES.has(err?.code)) keyStore.discard();
      throw err;
    }
  }

  async function update(id, event) {
    const startScope = scope;
    const saved = await api.update(id, event);
    commit(startScope, (events) => events.map((e) => (e.id === id ? saved : e)));
    return saved;
  }

  async function remove(id) {
    const startScope = scope;
    await api.remove(id);
    commit(startScope, (events) => events.filter((e) => e.id !== id));
  }

  const { events, status, error } = state;
  return { events, status, error, reload, create, update, remove, mode: api.mode };
}
