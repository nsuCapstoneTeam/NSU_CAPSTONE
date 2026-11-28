// 내 행사 목록 훅 (NSU-125): 화면은 이 훅으로 목록을 읽고 등록·수정·삭제합니다.
// 실제 저장은 events-api.js가 모드(server·demo)에 따라 맡습니다.
// enabled: 목록을 불러올지 (서버 모드에서는 로그인한 행사 관계자일 때만)
// owner: 로그인한 계정이 바뀌면 목록을 다시 불러오기 위한 값 (userId 등)
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { eventsApi as defaultApi } from './events-api.js';
import { SETTLED_CODES, registrationKey as defaultKey } from './registration-key.js';

const startState = (enabled) => ({ events: [], status: enabled ? 'loading' : 'idle', error: null });

export default function useMyEvents({ enabled = true, owner = null, api = defaultApi, keyStore = defaultKey } = {}) {
  // status: 'idle'(불러오지 않음) | 'loading' | 'ready' | 'error'
  const [state, setState] = useState(() => startState(enabled));
  // 다시 불러오기를 누를 때마다 올림
  const [version, setVersion] = useState(0);

  // 계정이 바뀌거나 불러올지 여부가 바뀌면 예전 목록을 비우고 새로 시작 (렌더 중 상태 맞추기)
  const scope = `${enabled}:${owner ?? ''}`;
  const [loadedScope, setLoadedScope] = useState(scope);
  if (scope !== loadedScope) {
    setLoadedScope(scope);
    setState(startState(enabled));
  }

  // 같은 계정으로 돌아와도 이전 요청과 구분하도록 범위가 바뀔 때마다 새 객체를 사용
  const scopeRef = useRef(null);
  useLayoutEffect(() => {
    scopeRef.current = { changes: 0 };
    return () => {
      scopeRef.current = null;
    };
  }, [scope]);

  useEffect(() => {
    if (!enabled) return undefined;
    // 늦게 도착한 예전 응답이 새 목록을 덮지 않게
    let stale = false;
    const requestScope = scopeRef.current;
    const startedAt = requestScope.changes;
    function isCurrent() {
      if (stale || scopeRef.current !== requestScope) return false;
      if (requestScope.changes === startedAt) return true;
      // 등록·수정·삭제 전에 시작한 조회는 성공과 실패 모두 버리고 다시 받음
      setVersion((v) => v + 1);
      return false;
    }
    api.list().then(
      (events) => {
        if (isCurrent()) setState({ events, status: 'ready', error: null });
      },
      (error) => {
        if (isCurrent()) setState((prev) => ({ ...prev, status: 'error', error }));
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

  // 등록·수정·삭제 결과를 목록에 반영. 요청 중에 계정이 바뀌었으면 새 계정 목록에 넣지 않음
  function applyChange(startScope, change) {
    if (!startScope || scopeRef.current !== startScope) return;
    startScope.changes += 1;
    setState((prev) => ({ ...prev, events: change(prev.events) }));
  }

  // 등록: 등록 키는 결과가 확정될 때만 버림 (성공·이미 등록됨·없음). 나머지 실패는 같은 키로 다시 보냄
  async function create(event, { onRetry } = {}) {
    const startScope = scopeRef.current;
    try {
      const saved = await api.create(event, { key: keyStore.current(), onRetry });
      // 등록 키는 계정과 관계없이 결과가 확정되면 버림
      keyStore.discard();
      applyChange(startScope, (events) => [...events.filter((e) => e.id !== saved.id), saved]);
      return saved;
    } catch (err) {
      if (SETTLED_CODES.has(err?.code)) keyStore.discard();
      throw err;
    }
  }

  async function update(id, event) {
    const startScope = scopeRef.current;
    const saved = await api.update(id, event);
    applyChange(startScope, (events) => events.map((e) => (e.id === id ? saved : e)));
    return saved;
  }

  async function remove(id) {
    const startScope = scopeRef.current;
    await api.remove(id);
    applyChange(startScope, (events) => events.filter((e) => e.id !== id));
  }

  return { ...state, reload, create, update, remove, mode: api.mode };
}
