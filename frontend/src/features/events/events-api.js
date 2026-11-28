// 내 행사 창구 (NSU-125): 화면은 이 파일의 함수만 부르고, 서버와 체험 중 어디로 갈지는 여기서 정합니다.
//   - server: 행사 API (docs/api/03_events.md). 로그인 토큰을 붙이고, 등록에는 등록 키(Idempotency-Key)를 붙임
//   - demo  : 서버 없이 이 브라우저(localStorage 'hm-mvp-events-v1')에 저장
// 모드는 로그인과 같은 설정(VITE_AUTH_API)을 따릅니다. 화면에는 늘 한국어 이름·만 원 단위의 행사를 돌려줍니다.
// (실행 테스트: node --test src/features/events/events-api.test.js)
import { AUTH_API_MODE, errorCodeOf } from '../auth/api/auth-api.js';
import { tokenStore } from '../auth/api/auth-token.js';
import { localDemoStore } from '../../storage/local-demo-store.js';
import { isEventList } from '../my-page/organizer-events.js';
import { fromResponse, toRequestBody } from './event-api-mapper.js';

export const DEMO_EVENTS_KEY = 'hm-mvp-events-v1';

// 오류 코드 → 화면 안내 { title, body, tone('error'|'warn'), action }
//   action: 'date'(행사 날짜 칸으로) · 'list'(내 행사 목록으로) · 'retry'(다시 시도) · 없음
const NETWORK = {
  title: '서버에 연결하지 못했어요.',
  body: '입력한 내용은 그대로 있어요. 잠시 뒤 다시 시도해 주세요. 같은 등록으로 보내서 행사가 두 번 생기지 않아요.',
  action: 'retry',
};
export const EVENT_ERRORS = {
  EVENT_START_IN_PAST: { title: '행사 시작 시간이 이미 지났어요.', body: '날짜나 시작 시간을 지금보다 뒤로 바꿔 주세요.', action: 'date' },
  VALIDATION_ERROR: { title: '서버가 입력 값을 받지 않았어요.', body: '칸을 다시 확인해 주세요. 계속 안 되면 새로고침한 뒤 다시 시도해 주세요.' },
  INVALID_EVENT_ID: { title: '행사를 찾을 수 없어요.', body: '목록을 다시 불러와 주세요.', action: 'list' },
  IDEMPOTENCY_KEY_REUSED: {
    title: '이미 등록된 행사가 있어요.',
    body: '연결이 끊겼을 때 먼저 보낸 등록이 저장됐어요. 내 행사에서 그 행사를 열어 바꾼 내용을 고쳐 주세요.',
    tone: 'warn',
    action: 'list',
  },
  EVENT_ALREADY_STARTED: { title: '이미 시작한 행사는 고칠 수 없어요.', body: '', action: 'list' },
  EVENT_CANCELLED: { title: '취소된 행사는 고칠 수 없어요.', body: '', action: 'list' },
  EVENT_NOT_FOUND: { title: '행사를 찾을 수 없어요.', body: '이미 삭제된 행사예요. 목록을 다시 불러와 주세요.', action: 'list' },
  AUTHENTICATION_REQUIRED: { title: '로그인 시간이 지났어요.', body: '다시 로그인해 주세요.' },
  ACCESS_DENIED: { title: '이 계정으로는 행사를 다룰 수 없어요.', body: '행사 관계자 계정으로 로그인해 주세요.' },
  DEMO_WRITE_NOT_ALLOWED: { title: '이 계정으로는 행사를 등록할 수 없어요.', body: '시연 서버에서는 팀 계정만 행사를 등록할 수 있어요.' },
  EVENT_REGISTRATION_UNAVAILABLE: NETWORK,
  INTERNAL_SERVER_ERROR: NETWORK,
  NETWORK_ERROR: NETWORK,
  ACCOUNT_CHANGED: {
    title: '로그인한 계정이 바뀌어 등록을 멈췄어요.',
    body: '다시 보내는 동안 계정이 바뀌었어요. 등록할 계정으로 로그인한 뒤 내 행사에서 확인해 주세요.',
    action: 'list',
  },
  STORAGE_FAILED: { title: '브라우저에 저장하지 못했어요.', body: '시크릿 창이라면 일반 창에서 다시 시도해 주세요.' },
  UNKNOWN_ERROR: { title: '알 수 없는 문제가 생겼어요.', body: '잠시 뒤 다시 시도해 주세요.' },
};

// 행사 API 실패. code로 어떤 실패인지 구분하고, notice()로 화면 안내를 얻음
export class EventsApiError extends Error {
  constructor(code, status = 0) {
    const notice = EVENT_ERRORS[code] ?? EVENT_ERRORS.UNKNOWN_ERROR;
    super(notice.title);
    this.name = 'EventsApiError';
    this.code = code;
    this.status = status;
  }
  notice() {
    return EVENT_ERRORS[this.code] ?? EVENT_ERRORS.UNKNOWN_ERROR;
  }
}
export const noticeFor = (error) =>
  error instanceof EventsApiError ? error.notice() : EVENT_ERRORS.UNKNOWN_ERROR;

// 다시 보내도 되는 실패: 응답을 못 받았거나(네트워크) 서버 오류(5xx)
const retryable = (error) => error.code === 'NETWORK_ERROR' || error.status >= 500;

// 응답 글을 JSON으로. 비었거나 JSON이 아니면 null
function parseJson(text) {
  try {
    return text ? JSON.parse(text) : null;
  } catch {
    return null;
  }
}

// ── 서버 모드 ──────────────────────────────────────────────
// retryDelays: 자동 재시도 간격(ms). 3번까지, 그 뒤에는 화면의 [다시 시도] (행사 API §5.3.1)
function createServerApi({ fetchImpl, authHeader, sleep, retryDelays }) {
  // auth: 이 요청에 붙일 로그인 헤더 (등록 재시도는 처음 보낼 때의 헤더를 계속 씀)
  async function request(method, path, { body, headers = {}, auth = authHeader() } = {}) {
    let response;
    let text;
    try {
      response = await fetchImpl(path, {
        method,
        headers: { ...auth, ...(body ? { 'Content-Type': 'application/json' } : {}), ...headers },
        body: body ? JSON.stringify(body) : undefined,
      });
      // 본문을 읽다 끊겨도 응답을 못 받은 것으로 봄 (빈 응답으로 숨기지 않음)
      text = await response.text();
    } catch {
      throw new EventsApiError('NETWORK_ERROR');
    }
    const data = parseJson(text);
    if (!response.ok) {
      // code 없는 400(Spring ProblemDetail)은 입력 오류, 401은 로그인 만료로 봄
      const code =
        data?.code ||
        (response.status === 400 ? 'VALIDATION_ERROR' : response.status === 401 ? 'AUTHENTICATION_REQUIRED' : errorCodeOf(data, response.status));
      throw new EventsApiError(code, response.status);
    }
    return data;
  }

  // 내 행사 목록: 다가오는 행사·지난 행사를 마지막 쪽(totalPages)까지 모두 받아 하나로 (페이지당 100개)
  async function listPeriod(period) {
    const items = [];
    for (let page = 0, totalPages = 1; page < totalPages; page += 1) {
      const data = await request('GET', `/api/v1/me/events?period=${period}&page=${page}&size=100`);
      items.push(...(data?.items ?? []));
      totalPages = Number(data?.totalPages) || 0;
    }
    return items;
  }

  return {
    mode: 'server',
    async list() {
      const [upcoming, past] = await Promise.all([listPeriod('UPCOMING'), listPeriod('PAST')]);
      return [...upcoming, ...past].map(fromResponse);
    },
    // 등록: 같은 등록 키로 보내고, 응답을 못 받거나 5xx면 간격을 늘려 가며 다시 보냄
    // onRetry(n, total): n번째로 다시 보낼 때 (화면 표시용)
    // 기다리는 동안 로그인 계정이 바뀌면 남은 재시도를 멈춤 (다른 계정으로 등록되지 않게)
    async create(event, { key, onRetry = () => {} } = {}) {
      const body = toRequestBody(event);
      const auth = authHeader();
      const sameAccount = () => JSON.stringify(authHeader()) === JSON.stringify(auth);
      for (let attempt = 0; ; attempt += 1) {
        try {
          return fromResponse(await request('POST', '/api/v1/events', { body, auth, headers: { 'Idempotency-Key': key } }));
        } catch (error) {
          if (!retryable(error) || attempt >= retryDelays.length) throw error;
          onRetry(attempt + 1, retryDelays.length);
          await sleep(retryDelays[attempt]);
          if (!sameAccount()) throw new EventsApiError('ACCOUNT_CHANGED');
        }
      }
    },
    async update(id, event) {
      return fromResponse(await request('PUT', `/api/v1/events/${id}`, { body: toRequestBody(event) }));
    },
    // 삭제: 다시 보낸 삭제의 404는 앞서 보낸 삭제가 이미 지운 것이므로 성공으로 봄 (행사 API §5.6)
    async remove(id) {
      try {
        await request('DELETE', `/api/v1/events/${id}`);
      } catch (error) {
        if (error.code !== 'EVENT_NOT_FOUND') throw error;
      }
    },
  };
}

// ── 체험 모드: 이 브라우저에 저장 ─────────────────────────────
// 시작 시간이 지난 행사는 서버처럼 EVENT_START_IN_PAST로 거절 (화면 흐름을 똑같이 확인할 수 있게)
function createDemoApi({ store, now, newId }) {
  const read = () => store.read(DEMO_EVENTS_KEY, [], isEventList);
  const write = (events) => {
    if (!store.write(DEMO_EVENTS_KEY, events)) throw new EventsApiError('STORAGE_FAILED');
  };
  const startsAt = (e) => new Date(`${e.date}T${e.startTime}:00+09:00`).getTime();
  const checkStart = (e) => {
    if (startsAt(e) <= now()) throw new EventsApiError('EVENT_START_IN_PAST', 400);
  };
  const find = (events, id) => {
    const found = events.find((e) => e.id === id);
    if (!found) throw new EventsApiError('EVENT_NOT_FOUND', 404);
    return found;
  };
  return {
    mode: 'demo',
    async list() {
      return read();
    },
    async create(event) {
      checkStart(event);
      const saved = { ...event, id: newId(), status: 'OPEN' };
      write([...read(), saved]);
      return saved;
    },
    async update(id, event) {
      const events = read();
      const current = find(events, id);
      if (current.status === 'CANCELLED') throw new EventsApiError('EVENT_CANCELLED', 409);
      if (startsAt(current) <= now()) throw new EventsApiError('EVENT_ALREADY_STARTED', 409);
      checkStart(event);
      const saved = { ...event, id, status: current.status ?? 'OPEN' };
      write(events.map((e) => (e.id === id ? saved : e)));
      return saved;
    },
    async remove(id) {
      write(read().filter((e) => e.id !== id));
    },
  };
}

// 모드·fetch·저장소·시계를 바꿔 끼울 수 있게 만든 공장 함수 (테스트에서 사용)
export function createEventsApi({
  mode = AUTH_API_MODE,
  fetchImpl = (...args) => fetch(...args),
  authHeader = () => tokenStore.authHeader(),
  sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
  retryDelays = [1000, 2000, 4000],
  store = localDemoStore,
  now = () => Date.now(),
  newId = () => crypto.randomUUID(),
} = {}) {
  return mode === 'server'
    ? createServerApi({ fetchImpl, authHeader, sleep, retryDelays })
    : createDemoApi({ store, now, newId });
}

// 화면에서 쓰는 기본 창구
export const eventsApi = createEventsApi();
