// 지도 데이터 불러오기 (NSU-117): 전국은 처음에, 시·도는 누르거나 마우스를 올릴 때 따로 받습니다.
// map-data/ 파일은 scripts/build-region-map.mjs가 만듭니다. 손으로 고치지 마세요.
import { useEffect, useState } from 'react';

const LOADERS = {
  nation: () => import('./map-data/nation.js'),
  11: () => import('./map-data/11.js'),
  12: () => import('./map-data/12.js'),
  26: () => import('./map-data/26.js'),
  27: () => import('./map-data/27.js'),
  28: () => import('./map-data/28.js'),
  30: () => import('./map-data/30.js'),
  31: () => import('./map-data/31.js'),
  36: () => import('./map-data/36.js'),
  41: () => import('./map-data/41.js'),
  43: () => import('./map-data/43.js'),
  44: () => import('./map-data/44.js'),
  47: () => import('./map-data/47.js'),
  48: () => import('./map-data/48.js'),
  50: () => import('./map-data/50.js'),
  51: () => import('./map-data/51.js'),
  52: () => import('./map-data/52.js'),
};

// 받은 데이터와 받는 중인 요청 (같은 지도를 두 번 받지 않게)
const loaded = new Map();
const pending = new Map();

// level: 'nation' 또는 시·도 코드 2자리. 실패하면 다음에 다시 시도할 수 있게 요청을 지움
export function loadMap(level) {
  if (loaded.has(level)) return Promise.resolve(loaded.get(level));
  if (!pending.has(level)) {
    const load = LOADERS[level];
    if (!load) return Promise.reject(new Error(`지도 데이터가 없는 지역: ${level}`));
    pending.set(
      level,
      load()
        .then((module) => {
          loaded.set(level, module.default);
          return module.default;
        })
        .finally(() => pending.delete(level)),
    );
  }
  return pending.get(level);
}

// 마우스를 올린 시·도를 미리 받아 두기 (실패는 무시, 누를 때 다시 받음)
export function preloadMap(level) {
  loadMap(level).catch(() => {});
}

// { data, error, retry }: data는 받기 전이면 null
export function useMapData(level) {
  const [, setVersion] = useState(0);
  // failed: { level, reload } (reload: 지도 파일 자체를 못 받은 경우)
  const [failed, setFailed] = useState(null);
  const [attempt, setAttempt] = useState(0);
  // 레벨이 바뀌면 이전 레벨의 실패 표시를 지움 (A에서 실패 → B → 다시 A로 오면 받는 동안 오류 대신 로딩)
  // 렌더 중에 이전 레벨과 비교해서 지움 (React 문서의 'props가 바뀔 때 state 조정' 방식)
  const [shownLevel, setShownLevel] = useState(level);
  if (shownLevel !== level) {
    setShownLevel(level);
    setFailed(null);
  }

  useEffect(() => {
    if (loaded.has(level)) return undefined;
    let alive = true;
    loadMap(level).then(
      () => alive && setVersion((v) => v + 1),
      // 파일을 받지 못하면 브라우저는 TypeError를 냄 (네트워크 끊김, 배포 뒤 예전 파일이 지워짐)
      (err) => alive && setFailed({ level, reload: err instanceof TypeError }),
    );
    return () => {
      alive = false;
    };
  }, [level, attempt]);

  return {
    data: loaded.get(level) ?? null,
    error: failed?.level === level,
    // 다시 시도. 파일 자체를 못 받았으면 같은 주소를 다시 요청해도 실패할 수 있어(배포 뒤 예전 파일이 지워진 경우)
    // 페이지를 새로 불러옴 (Vite 문서가 권하는 방식)
    retry: () => {
      if (failed?.reload) {
        window.location.reload();
        return;
      }
      setFailed(null);
      setAttempt((a) => a + 1);
    },
  };
}
