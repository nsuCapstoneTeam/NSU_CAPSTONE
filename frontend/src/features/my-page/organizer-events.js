// 행사 관계자 '내 행사' 도우미 (NSU-56·NSU-115, 요구사항 EVT-041·EVT-042)
// 화면 없이 행사 데이터만 다루는 순수 함수 모음
// (실행 테스트: node --test src/features/my-page/organizer-events.test.js)

import { regionLabel } from './event-place.js';
import { GENRE_MAX as GENRE_LIMIT, currentGenres } from '../events/event-codes.js';

// 선택지는 행사 API 계약(docs/api/03_events.md §3.3)의 한국어 이름. 서버에는 코드로 보냄 (features/events/event-codes.js)
export { EVENT_TYPES, ARTIST_TYPES, EVENT_GENRES, GENRE_MAX } from '../events/event-codes.js';
// EVT-043·행사 API §4: PRIVATE은 지금은 등록한 사람만 봄 (아티스트 조건 판정은 아직 없음)
export const VISIBILITIES = [
  { value: 'PUBLIC', label: '전체 공개', description: '로그인한 아티스트라면 누구나 볼 수 있어요.' },
  {
    value: 'PRIVATE',
    label: '비공개',
    description: '지금은 아티스트 정보가 없어 등록한 사람만 볼 수 있어요. 조건에 맞는 아티스트에게 보여 주는 기능은 준비 중이에요.',
  },
];

// 현장 정보(행사 API §3.4 details). 글로 적는 칸 [id, 이름, 예시]와 제공 여부 [id, 이름]
export const DETAIL_TEXTS = [
  ['rehearsal', '리허설 시간', '예: 공연 2시간 전 30분'],
  ['stageSize', '무대 크기', '예: 8m × 6m'],
  ['microphones', '마이크', '예: 무선 2개, 유선 4개'],
  ['instruments', '악기', '예: 드럼 세트, 베이스 앰프'],
];
export const DETAIL_TEXT_MAX = 100;
export const DETAIL_FLAGS = [
  ['isPaProvided', 'PA (음향 장비)'],
  ['isWaitingRoomProvided', '대기실'],
  ['isMealProvided', '식사'],
  ['isTransportationFeeProvided', '교통비'],
  ['isAccommodationProvided', '숙박'],
];
// 제공 여부: true(제공) · false(안 함) · null(미정)
export const EMPTY_DETAILS = {
  rehearsal: '',
  stageSize: '',
  microphones: '',
  instruments: '',
  isPaProvided: null,
  isWaitingRoomProvided: null,
  isMealProvided: null,
  isTransportationFeeProvided: null,
  isAccommodationProvided: null,
};

// 새 행사 입력 초기값 (EVT-042 필수 15개)
export const EMPTY_EVENT = {
  name: '', // 행사명
  eventType: '', // 행사 종류
  date: '', // 행사 날짜 'YYYY-MM-DD'
  startTime: '', // 공연 시작 'HH:MM'
  endTime: '', // 공연 종료 'HH:MM'
  address: '', // 장소 주소 (주소 검색으로 고른 도로명 주소, 체험판)
  regionCode: '', // 지역: 시·군·구 코드 5자리 (주소로 자동, 못 찾으면 직접 고름)
  region: '', // 지역 이름 '경기도 수원시' (regionCode로 만듦, 예전 행사는 '서울'처럼 저장돼 있음)
  venue: '', // 장소 이름
  artistType: '', // 희망 아티스트 형태
  genres: [], // 희망 장르 (여러 개)
  budgetMin: '', // 예산 최소 (만 원)
  budgetMax: '', // 예산 최대 (만 원)
  audience: '', // 예상 관객 수 (명)
  performanceMinutes: '', // 공연 시간 (분, 선택: 비우면 비워 둘 시간 전체로 저장)
  description: '', // 행사 설명 (AI 추천에 사용)
  notes: '', // 기타 조건 (선택, API details.otherConditions, 추천에 쓰지 않음. 예전 이름 '특이사항')
  details: EMPTY_DETAILS, // 현장 정보 (선택, API details의 나머지 9칸)
  stagePlan: null, // 무대 도면 (선택) { name, size, type }. 체험판은 파일 이름·크기만 저장
  visibility: 'PUBLIC', // 공개 범위
};

// 기타 조건 최대 글자 수 (API details.otherConditions와 같음)
export const NOTES_MAX = 500;

// 'HH:MM' → 하루 안의 분. 형식이 아니면 NaN
const toMin = (t) => {
  const [h, m] = String(t ?? '').split(':').map(Number);
  return Number.isInteger(h) && Number.isInteger(m) ? h * 60 + m : NaN;
};

// 종료가 다음 날인지: 종료 시각이 시작보다 이르면 자정을 넘긴 행사 (22:00~02:00)
export const endsNextDay = (startTime, endTime) => toMin(endTime) < toMin(startTime);

// 시간대 길이(분): '19:00'~'19:40' → 40, '22:00'~'02:00' → 240(다음 날까지). 시작과 같거나 형식이 아니면 0
// 시간대는 아티스트가 비워 둘 시간(이동·리허설·대기 포함), 공연 시간은 그 안에서 무대에 서는 길이 (행사 API §3.2)
// 날짜를 하나만 받으므로 시간대는 24시간보다 짧음 (API 상한 24시간 안)
export function spanMinutes(startTime, endTime) {
  const span = toMin(endTime) - toMin(startTime);
  if (!Number.isFinite(span) || span === 0) return 0;
  return span > 0 ? span : span + 24 * 60;
}

// 종료 날짜: 종료가 시작보다 이르면 다음 날, 아니면 시작 날짜와 같음 ('YYYY-MM-DD')
export function endDateFor(date, startTime, endTime) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(String(date ?? '')) || !endsNextDay(startTime, endTime)) return date;
  const [y, m, d] = date.split('-').map(Number);
  const next = new Date(Date.UTC(y, m - 1, d + 1));
  return next.toISOString().slice(0, 10);
}

// 필수 항목 [id, 화면 이름]. id는 입력칸 id(event-<id>)와 같게 맞춰 빠진 칸으로 바로 이동
// 공연 시간(분)은 화면에서는 선택이고, 비우면 시간대 길이로 저장합니다 (NSU-115, API에는 항상 값이 감)
export const REQUIRED_FIELDS = [
  ['name', '행사명'],
  ['eventType', '행사 종류'],
  ['date', '행사 날짜'],
  ['startTime', '비워 둘 시간 (시작)'],
  ['endTime', '비워 둘 시간 (종료)'],
  ['regionCode', '장소 주소'],
  ['venue', '장소 이름'],
  ['artistType', '희망 아티스트 형태'],
  ['genres', '희망 장르'],
  ['budgetMin', '예산 최소'],
  ['budgetMax', '예산 최대'],
  ['audience', '예상 관객 수'],
  ['description', '행사 설명'],
  ['visibility', '공개 범위'],
];

const isBlank = (value) =>
  Array.isArray(value) ? value.length === 0 : String(value ?? '').trim() === '';

// 숫자 칸: 빈칸이 아니고 0보다 큰 정수인지
const isPositiveInt = (value) => /^\d+$/.test(String(value).trim()) && Number(value) > 0;

// 저장하기 전에 고쳐야 할 항목 목록 [{ id, label }]
// 1) 빈 필수 항목 → '행사명' 2) 형식·규칙 오류 → '예산 최대 (최소보다 크거나 같게)'
// [임시 규칙] 예산 최소 ≤ 최대, 종료 > 시작 은 팀 확정 필요
export function eventProblems(event) {
  const problems = REQUIRED_FIELDS.filter(([id]) => isBlank(event[id])).map(([id, label]) => ({
    id,
    label,
  }));
  const blank = new Set(problems.map((p) => p.id));
  const add = (id, label) => {
    if (!blank.has(id)) problems.push({ id, label });
  };

  for (const [id, label, unit] of [
    ['budgetMin', '예산 최소', '만 원'],
    ['budgetMax', '예산 최대', '만 원'],
    ['audience', '예상 관객 수', '명'],
    ['performanceMinutes', '공연 시간', '분'],
  ]) {
    if (!isBlank(event[id]) && !isPositiveInt(event[id])) add(id, `${label} (1${unit} 이상 숫자)`);
  }
  if (
    isPositiveInt(event.budgetMin) &&
    isPositiveInt(event.budgetMax) &&
    Number(event.budgetMin) > Number(event.budgetMax)
  ) {
    add('budgetMax', '예산 최대 (최소보다 크거나 같게)');
  }
  // 시작과 종료가 같으면 시간대가 없음 (종료가 더 이르면 다음 날로 봄)
  if (event.startTime && event.endTime && event.endTime === event.startTime) {
    add('endTime', '비워 둘 시간 (종료를 시작과 다르게)');
  }
  // 장르는 1~3개 (행사 API §3.3)
  if (Array.isArray(event.genres) && event.genres.length > GENRE_LIMIT) {
    add('genres', `희망 장르 (${GENRE_LIMIT}개까지)`);
  }
  // 현장 정보 글 칸은 100자까지
  for (const [id, label] of DETAIL_TEXTS) {
    if (String(event.details?.[id] ?? '').trim().length > DETAIL_TEXT_MAX) {
      add(`details-${id}`, `${label} (${DETAIL_TEXT_MAX}자까지)`);
    }
  }
  // 공연 시간은 비워 둘 시간 안에 들어가야 함 (행사 API §3.2)
  const span = spanMinutes(event.startTime, event.endTime);
  if (span && isPositiveInt(event.performanceMinutes) && Number(event.performanceMinutes) > span) {
    add('performanceMinutes', `공연 시간 (비워 둘 시간 ${span}분 이하)`);
  }
  return problems;
}

// 저장용으로 정리: 앞뒤 공백 제거, 숫자 칸은 숫자로, 종료 날짜 계산
export function normalizeEvent(event) {
  const details = { ...EMPTY_DETAILS, ...event.details };
  for (const [id] of DETAIL_TEXTS) details[id] = String(details[id] ?? '').trim();
  return {
    ...event,
    endDate: endDateFor(event.date, event.startTime, event.endTime),
    details,
    name: event.name.trim(),
    address: event.address.trim(),
    region: regionLabel(event.regionCode) || event.region,
    venue: event.venue.trim(),
    description: event.description.trim(),
    notes: event.notes.trim(),
    budgetMin: Number(event.budgetMin),
    budgetMax: Number(event.budgetMax),
    audience: Number(event.audience),
    // 비우면 시간대 전체를 공연 시간으로 봄
    performanceMinutes: isBlank(event.performanceMinutes)
      ? spanMinutes(event.startTime, event.endTime)
      : Number(event.performanceMinutes),
  };
}

// 저장된 행사를 다시 폼에 넣을 때: 숫자를 입력칸용 문자열로
export function toFormValues(event) {
  return {
    ...EMPTY_EVENT,
    ...event,
    budgetMin: String(event.budgetMin ?? ''),
    budgetMax: String(event.budgetMax ?? ''),
    audience: String(event.audience ?? ''),
    performanceMinutes: String(event.performanceMinutes ?? ''),
    // 예전 체험 장르 이름은 지금 선택지 이름으로 (선택지에 없는 장르는 빼고 다시 고르게)
    genres: Array.isArray(event.genres) ? currentGenres(event.genres) : [],
    // 예전에 저장한 행사에는 아래 칸이 없음
    address: event.address ?? '',
    regionCode: event.regionCode ?? '',
    notes: event.notes ?? '',
    stagePlan: event.stagePlan ?? null,
    details: { ...EMPTY_DETAILS, ...event.details },
  };
}

// 날짜·시작 시간 순으로 정렬 (가까운 행사가 위로). 원본은 바꾸지 않음
export function sortEvents(events) {
  return [...events].sort((a, b) =>
    `${a.date} ${a.startTime}`.localeCompare(`${b.date} ${b.startTime}`),
  );
}

// 오늘보다 앞선 날짜의 행사인지 (지난 행사 표시용)
export const isPast = (event, today) => event.date < today;

// 예산 표시: 같으면 '50만 원', 다르면 '30~50만 원'. 서버 값이 만 원으로 나누어떨어지지 않으면 소수 한 자리까지
const manwon = (value) => (Number.isInteger(value) ? String(value) : String(Math.round(value * 10) / 10));
export function budgetText(event) {
  return event.budgetMin === event.budgetMax
    ? `${manwon(event.budgetMin)}만 원`
    : `${manwon(event.budgetMin)}~${manwon(event.budgetMax)}만 원`;
}

// 저장소에서 읽은 행사 목록이 올바른 형식인지 (형식이 틀리면 빈 목록 사용)
// 화면(events-tab.jsx)에서 쓰는 칸을 모두 확인해야, 예전 형식이나 손상된 값 때문에 탭이 깨지지 않음
const STRING_KEYS = [
  'id',
  'name',
  'eventType',
  'date',
  'startTime',
  'endTime',
  'venue',
  'region',
  'artistType',
  'description',
  'visibility',
];
const NUMBER_KEYS = ['budgetMin', 'budgetMax', 'audience', 'performanceMinutes'];
// NSU-115에서 더한 칸: 예전 행사에는 없을 수 있어 있으면 형식만 확인
const OPTIONAL_STRING_KEYS = ['address', 'regionCode', 'notes', 'endDate', 'status'];
// NSU-125에서 더한 현장 정보: 있으면 객체, 글 칸은 문자열, 제공 여부는 true·false·null
const isDetails = (v) =>
  v === undefined ||
  (v !== null &&
    typeof v === 'object' &&
    DETAIL_TEXTS.every(([id]) => v[id] === undefined || typeof v[id] === 'string') &&
    DETAIL_FLAGS.every(([id]) => v[id] === undefined || v[id] === null || typeof v[id] === 'boolean'));
const isStagePlan = (v) =>
  v == null || (typeof v === 'object' && typeof v.name === 'string' && Number.isFinite(v.size));

export function isEventList(value) {
  return (
    Array.isArray(value) &&
    value.every(
      (e) =>
        e &&
        STRING_KEYS.every((key) => typeof e[key] === 'string') &&
        NUMBER_KEYS.every((key) => Number.isFinite(e[key])) &&
        OPTIONAL_STRING_KEYS.every((key) => e[key] === undefined || typeof e[key] === 'string') &&
        isStagePlan(e.stagePlan) &&
        isDetails(e.details) &&
        Array.isArray(e.genres) &&
        e.genres.every((g) => typeof g === 'string'),
    )
  );
}
