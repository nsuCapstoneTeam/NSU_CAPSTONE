// 내 행사 목록 훅 (NSU-125): 화면은 이 훅으로 목록을 읽고 등록·수정·삭제합니다.
// 실제 저장은 events-api.js가 모드(server·demo)에 따라 맡습니다.
// enabled: 목록을 불러올지 (서버 모드에서는 로그인한 행사 관계자일 때만)
// owner: 로그인한 계정이 바뀌면 목록을 다시 불러오기 위한 값 (userId 등)
import { useCallback, useEffect, useState } from 'react';
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

  useEffect(() => {
    if (!enabled) return undefined;
    // 늦게 도착한 예전 응답이 새 목록을 덮지 않게
    let stale = false;
    api.list().then(
      (events) => {
        if (!stale) setState({ events, status: 'ready', error: null });
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

  const setEvents = (change) => setState((prev) => ({ ...prev, events: change(prev.events) }));

  // 등록: 등록 키는 결과가 확정될 때만 버림 (성공·이미 등록됨·없음). 나머지 실패는 같은 키로 다시 보냄
  async function create(event, { onRetry } = {}) {
    try {
      const saved = await api.create(event, { key: keyStore.current(), onRetry });
      keyStore.discard();
      setEvents((events) => [...events.filter((e) => e.id !== saved.id), saved]);
      return saved;
    } catch (err) {
      if (SETTLED_CODES.has(err?.code)) keyStore.discard();
      throw err;
    }
  }

  async function update(id, event) {
    const saved = await api.update(id, event);
    setEvents((events) => events.map((e) => (e.id === id ? saved : e)));
    return saved;
  }

  async function remove(id) {
    await api.remove(id);
    setEvents((events) => events.filter((e) => e.id !== id));
  }

  return { ...state, reload, create, update, remove, mode: api.mode };
}
