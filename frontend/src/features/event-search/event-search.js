// 행사 찾기 도우미 (NSU-116, 지도 NSU-117): 공개 행사를 시·도·시·군·구별로 세고, 고른 지역의 행사를 날짜순으로 고릅니다.
// 화면 없이 값만 다루는 순수 함수 (실행 테스트: node --test src/features/event-search/event-search.test.js)
import { DISTRICTS, PROVINCES } from '../../data/regions.js';
import { WEEKDAYS, fromDateKey } from '../my-page/availability.js';
import { regionLabel } from '../my-page/event-place.js';
import { sortEvents } from '../my-page/organizer-events.js';

// 행사의 시·도 코드 2자리: '11440' → '11'
export const provinceOf = (event) => String(event.regionCode ?? '').slice(0, 2);

// 시·도 이름: '11' → '서울특별시', 모르는 코드면 ''
export const provinceName = (code) => PROVINCES.find((p) => p.code === code)?.name ?? '';

// 시·군·구 이름: '11440' → '마포구', 모르는 코드면 ''
export const districtName = (code) => DISTRICTS.find((d) => d.code === code)?.name ?? '';

// 아티스트에게 보여 줄 행사: 공개 행사만 (비공개 행사 규칙은 docs/api/03 §4, 팀 확정 필요)
export const publicEvents = (events) => events.filter((e) => e.visibility === 'PUBLIC');

// 지도에 쓸 행사 수 { 코드: 수 }. provinceCode가 ''이면 시·도별, 있으면 그 시·도의 시·군·구별
export function countMap(events, provinceCode = '') {
  const counts = {};
  for (const e of events) {
    if (provinceCode && provinceOf(e) !== provinceCode) continue;
    const code = provinceCode ? String(e.regionCode) : provinceOf(e);
    counts[code] = (counts[code] ?? 0) + 1;
  }
  return counts;
}

// 고른 지역의 행사를 가까운 날짜순으로. provinceCode가 ''이면 전체, districtCode가 있으면 그 시·군·구만
export function eventsIn(events, provinceCode = '', districtCode = '') {
  let list = events;
  if (districtCode) list = events.filter((e) => String(e.regionCode) === districtCode);
  else if (provinceCode) list = events.filter((e) => provinceOf(e) === provinceCode);
  return sortEvents(list);
}

// 목록 왼쪽 날짜 칸: { day: '11.01', when: '일 14:00~15:00' }
export function dateParts(event) {
  const date = fromDateKey(event.date);
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  return { day: `${mm}.${dd}`, when: `${WEEKDAYS[date.getDay()]} ${event.startTime}~${event.endTime}` };
}

// 지역 짧게: '서울특별시 마포구' → '서울 마포구', 세종은 '세종'
export function shortRegion(event) {
  const label = regionLabel(event.regionCode) || event.region || '';
  const province = PROVINCES.find((p) => p.code === provinceOf(event));
  if (!province) return label;
  if (label === province.name) return province.short;
  return label.replace(province.name, province.short);
}

// 공연장 좌표 [경도, 위도]. 없거나 숫자가 아니거나 한국 밖이면 null (지도에 핀을 찍지 않음)
// 행사의 venueLat·venueLng를 읽습니다. 카카오 로컬 API처럼 좌표를 문자열로 주는 경우도 받습니다.
// (API 필드 이름은 팀 확정 필요. 바뀌면 이 함수만 고치면 됩니다)
export function venuePoint(event) {
  const lat = Number(event?.venueLat);
  const lng = Number(event?.venueLng);
  if (event?.venueLat == null || event?.venueLng == null) return null;
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  if (lat < 33 || lat > 39 || lng < 124 || lng > 132) return null;
  return [lng, lat];
}
