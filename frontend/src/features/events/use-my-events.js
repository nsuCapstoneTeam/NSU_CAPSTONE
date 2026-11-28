// 내 행사 목록 훅 (NSU-125): 화면은 이 훅으로 목록을 읽고 등록·수정·삭제합니다.
// 실제 저장은 events-api.js가 모드(server·demo)에 따라 맡습니다.
// enabled: 목록을 불러올지 (서버 모드에서는 로그인한 행사 관계자일 때만)
// owner: 로그인한 계정이 바뀌면 목록을 다시 불러오기 위한 값 (userId 등)
import { useCallback, useEffect, useRef, useState } from 'react';
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

  // 지금 계정 범위 (등록·수정·삭제 응답이 도착했을 때 같은 계정인지 확인용)
  const scopeRef = useRef(scope);
  useEffect(() => {
    scopeRef.current = scope;
  }, [scope]);
  // 등록·수정·삭제가 끝날 때마다 올림. 그 전에 시작한 목록 조회는 결과를 버리고 다시 조회
  const changes = useRef(0);

  useEffect(() => {
    if (!enabled) return undefined;
    // 늦게 도착한 예전 응답이 새 목록을 덮지 않게
    let stale = false;
    const startedAt = changes.current;
    api.list().then(
      (events) => {
        if (stale) return;
        // 조회 중에 등록·수정·삭제가 끝났으면 그 결과가 빠진 목록이라 다시 받음
        if (changes.current !== startedAt) setVersion((v) => v + 1);
        else setState({ events, status: 'ready', error: null });
      },
      (error) => {
        if (!stale) setState((prev) => ({ ...prev, status: 'error', error }));
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
    changes.current += 1;
    if (scopeRef.current !== startScope) return;
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
