// 행사 찾기 도우미 (NSU-116): 공개 행사를 시·도별로 세고, 고른 시·도의 행사를 날짜순으로 고릅니다.
// 화면 없이 값만 다루는 순수 함수 (실행 테스트: node --test src/features/event-search/event-search.test.js)
import { PROVINCES } from '../../data/regions.js';
import { WEEKDAYS, fromDateKey } from '../my-page/availability.js';
import { regionLabel } from '../my-page/event-place.js';
import { sortEvents } from '../my-page/organizer-events.js';

// 행사의 시·도 코드 2자리: '11440' → '11'
export const provinceOf = (event) => String(event.regionCode ?? '').slice(0, 2);

// 시·도 이름: '11' → '서울특별시', 모르는 코드면 ''
export const provinceName = (code) => PROVINCES.find((p) => p.code === code)?.name ?? '';

// 아티스트에게 보여 줄 행사: 공개 행사만 (비공개 행사 규칙은 docs/api/03 §4, 팀 확정 필요)
export const publicEvents = (events) => events.filter((e) => e.visibility === 'PUBLIC');

// 시·도별 행사 수 [{ code, name, short, count }]. 많은 순, 같으면 시·도 목록 순서
export function regionCounts(events) {
  const counts = new Map();
  for (const e of events) counts.set(provinceOf(e), (counts.get(provinceOf(e)) ?? 0) + 1);
  return PROVINCES.map((p) => ({ ...p, count: counts.get(p.code) ?? 0 })).sort(
    (a, b) => b.count - a.count,
  );
}

// 고른 시·도의 행사를 가까운 날짜순으로. provinceCode가 ''이면 전체
export function eventsIn(events, provinceCode = '') {
  const list = provinceCode ? events.filter((e) => provinceOf(e) === provinceCode) : events;
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
