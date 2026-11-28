// 화면의 행사 ↔ 행사 API 본문 (docs/api/03_events.md §3.1·§3.5, NSU-125)
// 화면 상태는 지금까지처럼 한국어 이름·만 원 단위를 쓰고, 서버와 주고받을 때만 바꿉니다.
//   - 분류 값: 한국어 이름 ↔ 코드 ('대학 축제' ↔ 'UNIVERSITY_FESTIVAL'), 희망 형태 '상관없음' ↔ null
//   - 날짜: 화면 date ↔ startDate, endDate는 화면이 계산(자정을 넘기면 다음 날)
//   - 예산: 만 원 ↔ 원, 예상 관객: audience ↔ expectedAudience
//   - 현장 정보: details(9칸) + 기타 조건 notes ↔ details(10칸). 빈 글은 null
//   - 주소(address)와 무대 도면(stagePlan)은 API에 칸이 없어 보내지 않음 (팀 확정 필요: API에 칸 추가)
// (실행 테스트: node --test src/features/events/event-api-mapper.test.js)
import {
  ANY_ARTIST_TYPE,
  artistTypeCode,
  artistTypeName,
  eventTypeCode,
  eventTypeName,
  genreCode,
  genreName,
} from './event-codes.js';
import { DETAIL_FLAGS, DETAIL_TEXTS, EMPTY_DETAILS, endDateFor } from '../my-page/organizer-events.js';
import { regionLabel } from '../my-page/event-place.js';

const WON_PER_MANWON = 10000;
const textOrNull = (value) => {
  const text = String(value ?? '').trim();
  return text ? text : null;
};
const flag = (value) => (value === true || value === false ? value : null);

// 화면 행사(normalizeEvent를 거친 값) → 등록·수정 요청 본문
// 코드로 바꿀 수 없는 값은 null로 두어 서버가 VALIDATION_ERROR로 알려 주게 함
export function toRequestBody(event) {
  const details = { ...EMPTY_DETAILS, ...event.details };
  return {
    name: event.name.trim(),
    eventType: eventTypeCode(event.eventType),
    startDate: event.date,
    startTime: event.startTime,
    endDate: event.endDate || endDateFor(event.date, event.startTime, event.endTime),
    endTime: event.endTime,
    performanceMinutes: event.performanceMinutes,
    venue: event.venue.trim(),
    regionCode: event.regionCode,
    artistType: event.artistType === ANY_ARTIST_TYPE ? null : artistTypeCode(event.artistType),
    genres: event.genres.map(genreCode),
    budgetMin: Math.round(event.budgetMin * WON_PER_MANWON),
    budgetMax: Math.round(event.budgetMax * WON_PER_MANWON),
    expectedAudience: event.audience,
    description: event.description.trim(),
    visibility: event.visibility,
    details: {
      ...Object.fromEntries(DETAIL_TEXTS.map(([id]) => [id, textOrNull(details[id])])),
      ...Object.fromEntries(DETAIL_FLAGS.map(([id]) => [id, flag(details[id])])),
      otherConditions: textOrNull(event.notes),
    },
  };
}

// 행사 API 응답 → 화면 행사. 모르는 코드는 코드 그대로 보여 줌 (서버가 값을 더해도 깨지지 않게)
export function fromResponse(body) {
  const details = body.details ?? {};
  return {
    id: body.id,
    status: body.status ?? 'OPEN',
    name: body.name,
    eventType: eventTypeName(body.eventType) ?? String(body.eventType ?? ''),
    date: body.startDate,
    startTime: body.startTime,
    endDate: body.endDate,
    endTime: body.endTime,
    performanceMinutes: body.performanceMinutes,
    venue: body.venue,
    regionCode: body.regionCode,
    region: regionLabel(body.regionCode) || body.regionCode,
    address: '',
    artistType: body.artistType == null ? ANY_ARTIST_TYPE : (artistTypeName(body.artistType) ?? body.artistType),
    genres: (body.genres ?? []).map((code) => genreName(code) ?? code),
    budgetMin: body.budgetMin / WON_PER_MANWON,
    budgetMax: body.budgetMax / WON_PER_MANWON,
    audience: body.expectedAudience,
    description: body.description,
    visibility: body.visibility,
    notes: details.otherConditions ?? '',
    stagePlan: null,
    details: {
      ...Object.fromEntries(DETAIL_TEXTS.map(([id]) => [id, details[id] ?? ''])),
      ...Object.fromEntries(DETAIL_FLAGS.map(([id]) => [id, flag(details[id])])),
    },
  };
}
