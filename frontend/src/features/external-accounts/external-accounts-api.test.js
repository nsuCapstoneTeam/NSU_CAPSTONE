// 외부 계정 API 창구 테스트 (실행: node --test src/features/external-accounts/external-accounts-api.test.js)
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { ExternalAccountsApiError, createExternalAccountsApi } from './external-accounts-api.js';

const ID = '0192f3a4-0000-7000-8000-000000000001';
const ARTIST = '0192f3a4-0000-7000-8000-0000000000aa';

// 마지막 요청을 기록하고 정해 둔 응답을 돌려주는 가짜 fetch
function fakeFetch(response) {
  const calls = [];
  const fetchImpl = async (url, init = {}) => {
    calls.push({ url, ...init });
    if (response instanceof Error) throw response;
    const { status = 200, body, headers = {} } = response;
    return {
      ok: status >= 200 && status < 300,
      status,
      headers: { get: (name) => headers[name.toLowerCase()] ?? null },
      text: async () => (body === undefined ? '' : JSON.stringify(body)),
    };
  };
  return { fetchImpl, calls };
}

function apiWith(response) {
  const { fetchImpl, calls } = fakeFetch(response);
  const api = createExternalAccountsApi({
    fetchImpl,
    authHeader: () => ({ Authorization: 'Bearer T' }),
  });
  return { api, calls };
}

describe('조회', () => {
  it('아티스트의 연결 목록을 읽어 items를 돌려준다', async () => {
    const { api, calls } = apiWith({ body: { items: [{ externalAccountId: ID }] } });
    const items = await api.list(ARTIST);
    assert.deepEqual(items, [{ externalAccountId: ID }]);
    assert.equal(calls[0].url, `/api/v1/artists/${ARTIST}/external-accounts`);
    assert.equal(calls[0].method, 'GET');
    assert.equal(calls[0].headers.Authorization, 'Bearer T');
  });
});

describe('연결 시작', () => {
  it('교체할 연결이 없으면 replaceExternalAccountId를 null로 보낸다', async () => {
    const { api, calls } = apiWith({ body: { authorizationUrl: 'https://accounts.google.com/x' } });
    const result = await api.startAuthorization();
    assert.equal(result.authorizationUrl, 'https://accounts.google.com/x');
    assert.equal(calls[0].url, '/api/v1/me/external-accounts/authorizations');
    assert.equal(calls[0].method, 'POST');
    assert.deepEqual(JSON.parse(calls[0].body), { provider: 'YOUTUBE', replaceExternalAccountId: null });
  });

  it('교체를 승인받은 연결의 ID를 그대로 보낸다', async () => {
    const { api, calls } = apiWith({ body: { authorizationUrl: 'https://accounts.google.com/x' } });
    await api.startAuthorization({ replaceExternalAccountId: ID });
    assert.deepEqual(JSON.parse(calls[0].body), { provider: 'YOUTUBE', replaceExternalAccountId: ID });
  });
});

describe('연결', () => {
  it('YouTube는 콜백으로 받은 state와 code를 oauth에 담아 보낸다', async () => {
    const { api, calls } = apiWith({ status: 201, body: { externalAccountId: ID } });
    const account = await api.connectYoutube({ state: 'S1', code: 'C1' });
    assert.equal(account.externalAccountId, ID);
    assert.equal(calls[0].url, '/api/v1/me/external-accounts');
    assert.deepEqual(JSON.parse(calls[0].body), {
      provider: 'YOUTUBE',
      oauth: { state: 'S1', code: 'C1' },
    });
  });

  it('Spotify는 주소와 교체할 연결 ID(없으면 null)를 보낸다', async () => {
    const { api, calls } = apiWith({ status: 201, body: { externalAccountId: ID } });
    await api.connectSpotify({ url: 'https://open.spotify.com/artist/abc' });
    assert.deepEqual(JSON.parse(calls[0].body), {
      provider: 'SPOTIFY',
      url: 'https://open.spotify.com/artist/abc',
      replaceExternalAccountId: null,
    });
  });
});

describe('작업물', () => {
  it('후보를 읽어 items를 돌려준다', async () => {
    const { api, calls } = apiWith({ body: { items: [{ workId: 'v1' }] } });
    assert.deepEqual(await api.workCandidates(ID), [{ workId: 'v1' }]);
    assert.equal(calls[0].url, `/api/v1/me/external-accounts/${ID}/work-candidates`);
  });

  it('고른 순서 그대로 PUT으로 전체를 바꾼다', async () => {
    const { api, calls } = apiWith({ body: { externalAccountId: ID } });
    await api.chooseWorks(ID, ['b', 'a']);
    assert.equal(calls[0].method, 'PUT');
    assert.equal(calls[0].url, `/api/v1/me/external-accounts/${ID}/works`);
    assert.deepEqual(JSON.parse(calls[0].body), { workIds: ['b', 'a'] });
  });
});

describe('해제', () => {
  it('204면 성공이다', async () => {
    const { api, calls } = apiWith({ status: 204 });
    await api.disconnect(ID);
    assert.equal(calls[0].method, 'DELETE');
    assert.equal(calls[0].url, `/api/v1/me/external-accounts/${ID}`);
  });

  it('404는 이미 해제된 것이므로 성공으로 본다 (API 06 §9)', async () => {
    const { api } = apiWith({ status: 404, body: { code: 'EXTERNAL_ACCOUNT_NOT_FOUND' } });
    await api.disconnect(ID);
  });

  it('해제가 아닌 요청의 404는 성공으로 보지 않는다', async () => {
    const { api } = apiWith({ status: 404, body: { code: 'EXTERNAL_ACCOUNT_NOT_FOUND' } });
    await assert.rejects(api.chooseWorks(ID, []), (error) => error.code === 'EXTERNAL_ACCOUNT_NOT_FOUND');
    await assert.rejects(api.workCandidates(ID), (error) => error.code === 'EXTERNAL_ACCOUNT_NOT_FOUND');
  });
});

describe('오류', () => {
  it('서버가 준 code와 한국어 안내 문구를 담아 던진다', async () => {
    const { api } = apiWith({ status: 409, body: { code: 'EXTERNAL_ACCOUNT_CONNECTION_CHANGED' } });
    await assert.rejects(api.connectYoutube({ state: 'S', code: 'C' }), (error) => {
      assert.ok(error instanceof ExternalAccountsApiError);
      assert.equal(error.code, 'EXTERNAL_ACCOUNT_CONNECTION_CHANGED');
      assert.equal(error.status, 409);
      assert.match(error.message, /다른 기기|바뀌/);
      return true;
    });
  });

  it('429는 Retry-After(초)를 담는다', async () => {
    const { api } = apiWith({
      status: 429,
      body: { code: 'TOO_MANY_REQUESTS' },
      headers: { 'retry-after': '120' },
    });
    await assert.rejects(api.list(ARTIST), (error) => {
      assert.equal(error.code, 'TOO_MANY_REQUESTS');
      assert.equal(error.retryAfterSeconds, 120);
      return true;
    });
  });

  it('인터넷이 끊기면 NETWORK_ERROR다', async () => {
    const { api } = apiWith(new TypeError('fetch failed'));
    await assert.rejects(api.list(ARTIST), (error) => error.code === 'NETWORK_ERROR');
  });

  it('code가 없는 5xx는 NETWORK_ERROR, 그 밖은 UNKNOWN_ERROR다', async () => {
    await assert.rejects(apiWith({ status: 502 }).api.list(ARTIST), (e) => e.code === 'NETWORK_ERROR');
    await assert.rejects(apiWith({ status: 418 }).api.list(ARTIST), (e) => e.code === 'UNKNOWN_ERROR');
  });

  it('모르는 code도 안내 문구가 비지 않는다', async () => {
    const { api } = apiWith({ status: 400, body: { code: 'SOMETHING_NEW' } });
    await assert.rejects(api.list(ARTIST), (error) => error.message.length > 0);
  });
});
