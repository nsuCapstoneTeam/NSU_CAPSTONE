// 콜백 값으로 연결 요청 보내기 테스트 (실행: node --test src/features/external-accounts/startup-connection.test.js)
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { ExternalAccountsApiError } from './external-accounts-api.js';
import { createStartupConnection } from './startup-connection.js';

// startupLink 가짜: 실제와 같이 claim은 처음 한 번만 값을 준다
function fakeLink(link) {
  let claimed = false;
  return {
    claim() {
      if (claimed) return null;
      claimed = true;
      return link;
    },
  };
}

function fakeApi({ fail } = {}) {
  const calls = [];
  return {
    calls,
    connectYoutube: async (args) => {
      calls.push(args);
      if (fail) throw fail;
      return { externalAccountId: 'a1' };
    },
  };
}

const CONNECT = { kind: 'connect', provider: 'YOUTUBE', state: 'S1', code: 'C1', tab: 'works' };

describe('createStartupConnection', () => {
  it('콜백 값이 있으면 state와 code로 연결 요청을 보내고 연결됐다고 알린다', async () => {
    const api = fakeApi();
    const run = createStartupConnection(fakeLink(CONNECT), api);
    assert.deepEqual(await run(), { connected: true });
    assert.deepEqual(api.calls, [{ state: 'S1', code: 'C1' }]);
  });

  it('StrictMode처럼 두 번 불러도 요청은 1번이고, 두 번째도 같은 결과를 받는다', async () => {
    const api = fakeApi();
    const run = createStartupConnection(fakeLink(CONNECT), api);
    const first = run();
    const second = run();
    assert.equal(first, second);
    assert.deepEqual(await second, { connected: true });
    assert.equal(api.calls.length, 1);
  });

  it('요청이 실패하면 오류 code를 알리고, 연결 요청 뒤의 오류임을 표시한다', async () => {
    const api = fakeApi({ fail: new ExternalAccountsApiError('EXTERNAL_ACCOUNT_AUTHORIZATION_INVALID', 400) });
    const run = createStartupConnection(fakeLink(CONNECT), api);
    assert.deepEqual(await run(), {
      error: 'EXTERNAL_ACCOUNT_AUTHORIZATION_INVALID',
      message: new ExternalAccountsApiError('EXTERNAL_ACCOUNT_AUTHORIZATION_INVALID').message,
      afterConnectRequest: true,
    });
  });

  it('사용자가 거절해서 돌아왔으면 요청 없이 그 오류를 알린다', async () => {
    const api = fakeApi();
    const run = createStartupConnection(
      fakeLink({ kind: 'error', error: 'EXTERNAL_ACCOUNT_AUTHORIZATION_DENIED', tab: 'works' }),
      api,
    );
    const result = await run();
    assert.equal(result.error, 'EXTERNAL_ACCOUNT_AUTHORIZATION_DENIED');
    assert.equal(result.afterConnectRequest, false);
    assert.equal(api.calls.length, 0);
  });

  it('돌아온 값이 없으면 아무것도 하지 않는다 (null)', () => {
    const api = fakeApi();
    const run = createStartupConnection(fakeLink(null), api);
    assert.equal(run(), null);
    assert.equal(api.calls.length, 0);
  });
});
