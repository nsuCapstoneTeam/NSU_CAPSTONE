// 프런트엔드 체험 데이터 전용 저장소입니다.
// API, 쿠키, 인증 토큰, 서버 DB에 접근하지 않습니다.
// 기존 키를 유지하여 저장된 체험 정보를 보존합니다.
const DEMO_KEYS = new Set([
  'hallaemallae-theme',
  'hm-mvp-profile-v1',
  'hm-mvp-posts-v1',
  'hm-mvp-schedules-v1',
  'hm-mvp-saved-v1',
  'hm-mvp-session-v1',
]);

function assertDemoKey(key) {
  if (!DEMO_KEYS.has(key))
    throw new Error('허용되지 않은 체험 데이터 키입니다.');
}

// 저장소를 인자로 받아, 브라우저 없이도 동작을 검증할 수 있습니다.
export function createLocalDemoStore(getStorage) {
  return {
    read(key, fallback, validate = () => true) {
      assertDemoKey(key);
      try {
        const raw = getStorage().getItem(key);
        if (raw === null) return fallback;
        // 이전 홈페이지가 저장한 일반 문자열 테마도 호환합니다.
        const value =
          key === 'hallaemallae-theme' && ['light', 'dark'].includes(raw)
            ? raw
            : JSON.parse(raw);
        return value !== null && validate(value) ? value : fallback;
      } catch {
        return fallback;
      }
    },
    write(key, value) {
      assertDemoKey(key);
      try {
        getStorage().setItem(key, JSON.stringify(value));
        return true;
      } catch {
        return false;
      }
    },
  };
}

export const localDemoStore = createLocalDemoStore(() => window.localStorage);
