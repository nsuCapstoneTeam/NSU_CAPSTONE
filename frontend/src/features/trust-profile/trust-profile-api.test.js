// Trust Profile API 창구 테스트 (실행: node --test src/features/trust-profile/trust-profile-api.test.js)
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { ERROR_MESSAGES as AUTH_ERROR_MESSAGES } from '../auth/api/auth-api.js';
import {
  ERROR_MESSAGES,
  TrustProfileApiError,
  createTrustProfileApi,
  verificationItemsOf,
} from './trust-profile-api.js';

const ARTIST = '0192f3a4-7b1c-7d2e-8f00-1a2b3c4d5e6f';

// 계약 §6.1 형태의 응답. verifications만 바꿔 쓴다
const profileWith = (verifications = []) => ({
  artistId: ARTIST,
  policyVersion: 'reliability-v1',
  verifications,
  reliability: { confidenceLevel: 'LOW', observationCount: 0, ratePercent: null },
  evidence: {
    observations: {
      completedCount: 0,
      artistCancellations: { noticeAtLeast7DaysCount: 0, notice24HoursTo7DaysCount: 0, noticeUnder24HoursCount: 0 },
      noShowCount: 0,
    },
    exclusions: [],
  },
  riskSignals: [],
  activityFreshness: { lastPerformedMonth: null, completedCountLast12Months: 0 },
});

// 마지막 요청을 기록하고 정해 둔 응답을 돌려주는 가짜 fetch
function fakeFetch(response) {
  const calls = [];
  const fetchImpl = async (url, init = {}) => {
    calls.push({ url, ...init });
    if (response instanceof Error) throw response;
    const { status = 200, body, text } = response;
    return {
      ok: status >= 200 && status < 300,
      status,
      headers: { get: () => null },
      text: async () => text ?? (body === undefined ? '' : JSON.stringify(body)),
    };
  };
  return { fetchImpl, calls };
}

function apiWith(response) {
  const { fetchImpl, calls } = fakeFetch(response);
  const api = createTrustProfileApi({
    fetchImpl,
    authHeader: () => ({ Authorization: 'Bearer T' }),
  });
  return { api, calls };
}

describe('조회', () => {
  it('아티스트의 Trust Profile을 로그인 헤더와 함께 GET으로 읽어 본문을 돌려준다', async () => {
    const profile = profileWith([{ type: 'IDENTITY', provider: null }]);
    const { api, calls } = apiWith({ body: profile });
    assert.deepEqual(await api.getTrustProfile(ARTIST), profile);
    assert.equal(calls[0].url, `/api/v1/artists/${ARTIST}/trust-profile`);
    assert.equal(calls[0].method, 'GET');
    assert.equal(calls[0].headers.Authorization, 'Bearer T');
  });

  it('artistId의 /, ?, #는 경로를 바꾸지 못하게 인코딩해 보낸다', async () => {
    const { api, calls } = apiWith({ body: profileWith() });
    await api.getTrustProfile('../me?x=1#y');
    assert.equal(calls[0].url, '/api/v1/artists/..%2Fme%3Fx%3D1%23y/trust-profile');
  });
});

// 화면은 이 결과를 그대로 <VerifiedBadge badgeItems=… listItems=…>에 넘긴다
describe('배지 항목으로 바꾸기', () => {
  it('받은 Trust Profile에서 실명 본인확인만 색을 켜고, 증빙 확인 세 항목과 외부 계정은 목록에만 둔다', async () => {
    const { api } = apiWith({
      body: profileWith([
        { type: 'IDENTITY', provider: null },
        { type: 'EXTERNAL_ACCOUNT', provider: 'YOUTUBE' },
        { type: 'BUSINESS_REGISTRATION', provider: null },
        { type: 'PERFORMANCE_PARTICIPATION', provider: null },
        { type: 'OFFICIAL_ACTIVITY', provider: null },
      ]),
    });
    assert.deepEqual(verificationItemsOf(await api.getTrustProfile(ARTIST)), {
      badgeItems: ['실명 본인확인'],
      listItems: [
        '실명 본인확인',
        'YouTube 채널 제어권 확인',
        '사업자등록 확인',
        '실연 참여 확인',
        '공식 활동 확인',
      ],
    });
  });
});

describe('성공인데 Trust Profile이 아닌 응답', () => {
  // 프록시나 화면 폴백이 HTML을 200으로 돌려줄 수 있다. 빈 프로필로 취급하면 "확인된 항목 없음"으로 잘못 알리게 된다
  it('JSON이 아닌 200 응답은 UNKNOWN_ERROR로 던진다', async () => {
    const { api } = apiWith({ status: 200, text: '<!doctype html><html></html>' });
    await assert.rejects(api.getTrustProfile(ARTIST), (error) => {
      assert.ok(error instanceof TrustProfileApiError);
      assert.equal(error.code, 'UNKNOWN_ERROR');
      assert.equal(error.status, 200);
      return true;
    });
  });

  it('verifications 배열이 없는 JSON 200 응답도 UNKNOWN_ERROR로 던진다', async () => {
    for (const body of [{}, { verifications: null }, [], 'ok']) {
      const { api } = apiWith({ status: 200, body });
      await assert.rejects(api.getTrustProfile(ARTIST), (error) => error.code === 'UNKNOWN_ERROR', JSON.stringify(body));
    }
  });
});

describe('오류', () => {
  it('서버가 준 code와 상태, 한국어 안내 문구를 담아 던진다', async () => {
    const { api } = apiWith({
      status: 404,
      body: { code: 'ARTIST_NOT_FOUND', message: '아티스트를 찾을 수 없습니다.' },
    });
    await assert.rejects(api.getTrustProfile(ARTIST), (error) => {
      assert.ok(error instanceof TrustProfileApiError);
      assert.equal(error.code, 'ARTIST_NOT_FOUND');
      assert.equal(error.status, 404);
      assert.equal(error.message, '찾을 수 없는 아티스트예요.');
      return true;
    });
  });

  it('인터넷이 끊기거나 서버가 꺼져 있으면 NETWORK_ERROR다', async () => {
    const { api } = apiWith(new TypeError('fetch failed'));
    await assert.rejects(api.getTrustProfile(ARTIST), (error) => {
      assert.ok(error instanceof TrustProfileApiError);
      assert.equal(error.code, 'NETWORK_ERROR');
      assert.equal(error.status, 0);
      return true;
    });
  });

  it('JSON이 아닌 오류 응답은 code를 추측한다: 5xx는 NETWORK_ERROR, 그 밖은 UNKNOWN_ERROR', async () => {
    const bad = (status) => apiWith({ status, text: '<html>Bad Gateway</html>' }).api.getTrustProfile(ARTIST);
    await assert.rejects(bad(502), (error) => error.code === 'NETWORK_ERROR' && error.status === 502);
    await assert.rejects(bad(418), (error) => error.code === 'UNKNOWN_ERROR' && error.status === 418);
  });
});

// 계약 §3의 응답 상태 표를 읽어, 새 code를 계약에 더하고 안내 문구를 빠뜨리는 일을 막는다
describe('계약 §3의 오류 코드 안내 문구', () => {
  const contract = readFileSync(new URL('../../../../docs/api/01_trust_profile.md', import.meta.url), 'utf8');
  const section = contract.slice(contract.indexOf('## 3. 응답 상태'), contract.indexOf('## 4. 응답 본문'));
  // 표의 마지막 칸이 `CODE`인 줄만 모은다 (칸 안의 EVENT_PARTNER 같은 말은 code가 아니다)
  const codes = [
    ...new Set(
      section
        .split('\n')
        .filter((line) => line.startsWith('|'))
        .map((line) => line.split('|').filter((cell) => cell.trim()).at(-1).trim())
        .map((cell) => cell.match(/^`([A-Z][A-Z0-9_]+)`$/)?.[1])
        .filter(Boolean),
    ),
  ];

  it('계약에서 오류 코드를 읽어 온다', () => {
    assert.deepEqual(codes.toSorted(), [
      'ACCESS_DENIED',
      'ARTIST_NOT_FOUND',
      'AUTHENTICATION_REQUIRED',
      'INTERNAL_SERVER_ERROR',
      'INVALID_ARTIST_ID',
    ]);
  });

  it('표의 모든 code에 안내 문구가 있다', () => {
    const missing = codes.filter((code) => !ERROR_MESSAGES[code]);
    assert.deepEqual(missing, []);
  });

  it('로그인 쪽에도 있는 공통 코드는 로그인 쪽 문구를 그대로 쓴다', () => {
    const shared = codes.filter((code) => AUTH_ERROR_MESSAGES[code]);
    assert.deepEqual(shared.toSorted(), ['ACCESS_DENIED', 'AUTHENTICATION_REQUIRED', 'INTERNAL_SERVER_ERROR']);
    for (const code of shared) {
      assert.equal(ERROR_MESSAGES[code], AUTH_ERROR_MESSAGES[code], code);
    }
  });
});
