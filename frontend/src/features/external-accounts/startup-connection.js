// 콜백으로 돌아온 값으로 연결 요청을 한 번만 보내는 곳 (API 06 §4·§5.4)
// state는 한 번 쓰면 사라지므로 같은 값을 두 번 보내면 안 됩니다. StrictMode는 개발 중 효과를 두 번 돌리므로,
// 첫 호출이 요청을 시작해 Promise를 기억하고 이후 호출은 그 Promise를 그대로 돌려줍니다.
import { ExternalAccountsApiError } from './external-accounts-api.js';

// link: startup-link.js의 인스턴스, api: 외부 계정 API 창구
// 돌려주는 함수는 호출하면 null(할 일 없음) 또는 Promise<{ connected: true } | { error, message, afterConnectRequest }>
export function createStartupConnection(link, api) {
  let started = null;
  return function run() {
    if (started) return started;
    const callback = link.claim();
    if (!callback) return null;

    if (callback.kind === 'error') {
      const message = new ExternalAccountsApiError(callback.error).message;
      started = Promise.resolve({ error: callback.error, message, afterConnectRequest: false });
    } else {
      started = api.connectYoutube({ state: callback.state, code: callback.code }).then(
        () => ({ connected: true }),
        (error) => ({
          error: error.code ?? 'UNKNOWN_ERROR',
          message: error.message,
          afterConnectRequest: true,
        }),
      );
    }
    return started;
  };
}
