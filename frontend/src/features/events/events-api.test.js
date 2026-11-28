import test from 'node:test';
import assert from 'node:assert/strict';
import { EventsApiError, createEventsApi, noticeFor } from './events-api.js';
import { createLocalDemoStore } from '../../storage/local-demo-store.js';
import { EMPTY_DETAILS, normalizeEvent } from '../my-page/organizer-events.js';
import { toRequestBody } from './event-api-mapper.js';

const EVENT = normalizeEvent({
  name: '가을 축제',
  eventType: '대학 축제',
  date: '2026-12-30',
  startTime: '18:00',
  endTime: '20:00',
  address: '',
  regionCode: '11440',
  region: '',
  venue: '본무대',
  artistType: '밴드',
  genres: ['록'],
  budgetMin: '30',
  budgetMax: '50',
  audience: '500',
  performanceMinutes: '',
  description: '야외 무대',
  notes: '',
  details: EMPTY_DETAILS,
  stagePlan: null,
  visibility: 'PUBLIC',
});
const SERVER_EVENT = { id: 'evt-1', status: 'OPEN', eventPartnerId: 'p', ...toRequestBody(EVENT) };

// 응답을 차례로 돌려주는 가짜 fetch. 'offline'이면 네트워크 실패
function fakeFetch(responses) {
  const calls = [];
  const fetchImpl = async (url, init) => {
    calls.push({ url, ...init, body: init.body ? JSON.parse(init.body) : undefined });
    const next = responses.shift();
    if (next === 'offline') throw new TypeError('Failed to fetch');
    const { status = 200, body = null } = next;
    return { ok: status < 400, status, text: async () => (body === null ? '' : JSON.stringify(body)) };
  };
  return { fetchImpl, calls };
}
const server = (responses, extra = {}) => {
  const fake = fakeFetch(responses);
  const api = createEventsApi({
    mode: 'server',
    fetchImpl: fake.fetchImpl,
    authHeader: () => ({ Authorization: 'Bearer t' }),
    sleep: async () => {},
    ...extra,
  });
  return { api, calls: fake.calls };
};

test('서버 등록: 토큰과 등록 키를 붙여 보내고, 화면 행사로 돌려준다', async () => {
  const { api, calls } = server([{ status: 201, body: SERVER_EVENT }]);
  const saved = await api.create(EVENT, { key: 'k-1' });
  assert.equal(saved.id, 'evt-1');
  assert.equal(saved.budgetMax, 50);
  assert.equal(calls[0].url, '/api/v1/events');
  assert.equal(calls[0].method, 'POST');
  assert.equal(calls[0].headers['Idempotency-Key'], 'k-1');
  assert.equal(calls[0].headers.Authorization, 'Bearer t');
  assert.equal(calls[0].body.budgetMax, 500000);
});

test('서버 등록: 연결이 끊기거나 5xx면 같은 등록 키로 3번까지 다시 보낸다', async () => {
  const retries = [];
  const waits = [];
  const { api, calls } = server(['offline', { status: 503, body: { code: 'EVENT_REGISTRATION_UNAVAILABLE' } }, { status: 201, body: SERVER_EVENT }], {
    sleep: async (ms) => waits.push(ms),
  });
  const saved = await api.create(EVENT, { key: 'k-2', onRetry: (n, total) => retries.push(`${n}/${total}`) });
  assert.equal(saved.id, 'evt-1');
  assert.deepEqual(retries, ['1/3', '2/3']);
  assert.deepEqual(waits, [1000, 2000]);
  assert.ok(calls.every((c) => c.headers['Idempotency-Key'] === 'k-2'));
});

test('서버 등록: 3번 다시 보내도 안 되면 실패를 알리고, 4xx는 다시 보내지 않는다', async () => {
  const down = server(['offline', 'offline', 'offline', 'offline']);
  await assert.rejects(down.api.create(EVENT, { key: 'k' }), (e) => e.code === 'NETWORK_ERROR');
  assert.equal(down.calls.length, 4);

  const past = server([{ status: 400, body: { code: 'EVENT_START_IN_PAST' } }]);
  await assert.rejects(past.api.create(EVENT, { key: 'k' }), (e) => e.code === 'EVENT_START_IN_PAST');
  assert.equal(past.calls.length, 1);
  assert.equal(noticeFor(new EventsApiError('EVENT_START_IN_PAST')).action, 'date');
});

test('서버 등록: 본문을 읽다 끊기면 빈 응답이 아니라 연결 실패로 보고 다시 보낸다', async () => {
  let n = 0;
  const api = createEventsApi({
    mode: 'server',
    authHeader: () => ({ Authorization: 'Bearer t' }),
    sleep: async () => {},
    fetchImpl: async () => {
      n += 1;
      const text =
        n === 1
          ? async () => {
              throw new TypeError('network');
            }
          : async () => JSON.stringify(SERVER_EVENT);
      return { ok: true, status: 201, text };
    },
  });
  const saved = await api.create(EVENT, { key: 'k' });
  assert.equal(saved.id, 'evt-1');
  assert.equal(n, 2);
});

test('서버 등록: 다시 보내기를 기다리는 동안 계정이 바뀌면 멈추고, 보낸 요청은 처음 계정의 토큰을 쓴다', async () => {
  let token = 'A';
  const sent = [];
  const api = createEventsApi({
    mode: 'server',
    authHeader: () => ({ Authorization: `Bearer ${token}` }),
    sleep: async () => {
      token = 'B';
    },
    fetchImpl: async (url, init) => {
      sent.push(init.headers.Authorization);
      return { ok: false, status: 503, text: async () => JSON.stringify({ code: 'EVENT_REGISTRATION_UNAVAILABLE' }) };
    },
  });
  await assert.rejects(api.create(EVENT, { key: 'k' }), (e) => e.code === 'ACCOUNT_CHANGED');
  assert.deepEqual(sent, ['Bearer A']);
});

test('서버 오류 코드가 없으면 400은 입력 오류, 401은 로그인 만료로 본다', async () => {
  const bad = server([{ status: 400, body: { title: 'Bad Request' } }]);
  await assert.rejects(bad.api.update('evt-1', EVENT), (e) => e.code === 'VALIDATION_ERROR');
  const expired = server([{ status: 401 }]);
  await assert.rejects(expired.api.update('evt-1', EVENT), (e) => e.code === 'AUTHENTICATION_REQUIRED');
});

test('서버 삭제: 이미 지워져 404가 와도 성공으로 본다', async () => {
  const { api, calls } = server([{ status: 404, body: { code: 'EVENT_NOT_FOUND' } }]);
  await api.remove('evt-1');
  assert.equal(calls[0].method, 'DELETE');
  assert.equal(calls[0].url, '/api/v1/events/evt-1');
  const denied = server([{ status: 403, body: { code: 'ACCESS_DENIED' } }]);
  await assert.rejects(denied.api.remove('evt-1'), (e) => e.code === 'ACCESS_DENIED');
});

test('서버 목록: 10쪽이 넘어도 마지막 쪽까지 받는다', async () => {
  let pages = 0;
  const api = createEventsApi({
    mode: 'server',
    authHeader: () => ({}),
    fetchImpl: async (url) => {
      const page = Number(new URL(url, 'http://x').searchParams.get('page'));
      const upcoming = url.includes('UPCOMING');
      if (upcoming) pages += 1;
      const body = upcoming
        ? { items: [{ ...SERVER_EVENT, id: `evt-${page}` }], totalPages: 12 }
        : { items: [], totalPages: 0 };
      return { ok: true, status: 200, text: async () => JSON.stringify(body) };
    },
  });
  const list = await api.list();
  assert.equal(pages, 12);
  assert.equal(list.length, 12);
});

test('서버 목록: 다가오는 행사와 지난 행사를 쪽마다 모두 받아 합친다', async () => {
  const page = (items, totalPages) => ({ body: { items, page: 0, size: 100, totalElements: items.length, totalPages } });
  const { api, calls } = server([
    page([SERVER_EVENT], 2),
    page([], 0),
    page([{ ...SERVER_EVENT, id: 'evt-2' }], 2),
  ]);
  const list = await api.list();
  assert.deepEqual(
    list.map((e) => e.id),
    ['evt-1', 'evt-2'],
  );
  assert.deepEqual(
    calls.map((c) => c.url),
    [
      '/api/v1/me/events?period=UPCOMING&page=0&size=100',
      '/api/v1/me/events?period=PAST&page=0&size=100',
      '/api/v1/me/events?period=UPCOMING&page=1&size=100',
    ],
  );
});

test('서버 목록: 다음 쪽의 응답이 비었거나 페이지 정보가 없으면 부분 목록을 반환하지 않는다', async () => {
  for (const body of [null, { items: [] }, { totalPages: 2 }]) {
    const { api } = server([
      { body: { items: [SERVER_EVENT], totalPages: 2 } },
      { body: { items: [], totalPages: 0 } },
      { body },
    ]);
    await assert.rejects(api.list(), { code: 'UNKNOWN_ERROR' });
  }
});

// 체험 모드
function memoryStorage() {
  const data = new Map();
  return {
    getItem: (k) => (data.has(k) ? data.get(k) : null),
    setItem: (k, v) => data.set(k, String(v)),
    removeItem: (k) => data.delete(k),
  };
}
const demo = (now = Date.parse('2026-11-28T00:00:00+09:00')) => {
  const storage = memoryStorage();
  let n = 0;
  return createEventsApi({
    mode: 'demo',
    store: createLocalDemoStore(() => storage),
    now: () => now,
    newId: () => `demo-${++n}`,
  });
};

test('체험 모드: 이 브라우저에 등록·수정·삭제한다', async () => {
  const api = demo();
  const saved = await api.create(EVENT);
  assert.equal(saved.id, 'demo-1');
  assert.equal(saved.status, 'OPEN');
  await api.update('demo-1', { ...EVENT, name: '겨울 축제' });
  assert.equal((await api.list())[0].name, '겨울 축제');
  await api.remove('demo-1');
  assert.deepEqual(await api.list(), []);
});

test('체험 모드도 시작 시간이 지난 행사는 서버처럼 거절한다', async () => {
  const api = demo();
  await assert.rejects(api.create({ ...EVENT, date: '2026-11-01' }), (e) => e.code === 'EVENT_START_IN_PAST');
  await assert.rejects(api.update('none', EVENT), (e) => e.code === 'EVENT_NOT_FOUND');
});
