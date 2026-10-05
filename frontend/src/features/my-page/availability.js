// 공연 가능 일정(ART-029) 계산 도우미: 화면 없이 날짜·시간만 다루는 순수 함수 모음
// (실행 테스트: node --test src/features/my-page/availability.test.js)

// 일정 상태 4가지 (SSOT ART-029). label은 화면 표시용 한국어 이름
export const STATUSES = [
  { value: 'AVAILABLE', label: '가능' },
  { value: 'HOLD', label: '보류' },
  { value: 'BOOKED', label: '확정' },
  { value: 'UNAVAILABLE', label: '불가' },
];

export const STATUS_VALUES = STATUSES.map((s) => s.value);

// 예전에 저장한 일정에는 상태가 없으므로 '가능'으로 봅니다.
export const statusOf = (item) =>
  STATUS_VALUES.includes(item?.status) ? item.status : 'AVAILABLE';

export const statusLabel = (value) =>
  STATUSES.find((s) => s.value === value)?.label ?? '가능';

// 요일 이름 (일요일 시작)
export const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

// Date → 'YYYY-MM-DD' (내 컴퓨터 시간 기준)
export function toDateKey(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

// 'YYYY-MM-DD' → Date (그날 0시, 내 컴퓨터 시간 기준)
export function fromDateKey(key) {
  const [y, m, d] = key.split('-').map(Number);
  return new Date(y, m - 1, d);
}

// 날짜에 days일을 더한 'YYYY-MM-DD'
export function addDays(key, days) {
  const date = fromDateKey(key);
  date.setDate(date.getDate() + days);
  return toDateKey(date);
}

// '2026-10-15' → '10월 15일 (목)'
export function dayTitle(key) {
  const date = fromDateKey(key);
  return `${date.getMonth() + 1}월 ${date.getDate()}일 (${WEEKDAYS[date.getDay()]})`;
}

// 그 날짜가 속한 주의 7일 (일요일 ~ 토요일) 'YYYY-MM-DD' 목록
export function weekDays(key) {
  const offset = fromDateKey(key).getDay();
  return Array.from({ length: 7 }, (_, i) => addDays(key, i - offset));
}

// 달력 칸 목록: 해당 달을 덮는 일요일 시작 주 단위 칸(35칸 또는 42칸)
// 각 칸 { key: 'YYYY-MM-DD', day: 숫자, inMonth: 이번 달인지 }
export function monthCells(year, month) {
  const first = new Date(year, month, 1);
  const start = new Date(year, month, 1 - first.getDay());
  const lastDay = new Date(year, month + 1, 0).getDate();
  const weeks = Math.ceil((first.getDay() + lastDay) / 7);
  return Array.from({ length: weeks * 7 }, (_, i) => {
    const date = new Date(start.getFullYear(), start.getMonth(), start.getDate() + i);
    return {
      key: toDateKey(date),
      day: date.getDate(),
      inMonth: date.getMonth() === month,
    };
  });
}

// 'HH:MM' → 분
export const toMinutes = (time) => {
  const [h, m] = time.split(':').map(Number);
  return h * 60 + m;
};

// 분 → 'HH:MM' (24:00 허용)
export const fromMinutes = (minutes) =>
  `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;

// 같은 날 시간이 겹치는 다른 일정이 있는지 (ignoreId: 수정 중인 일정은 제외)
export function findOverlap(items, candidate, ignoreId = null) {
  return items.find(
    (s) =>
      s.id !== ignoreId &&
      s.date === candidate.date &&
      s.start < candidate.end &&
      candidate.start < s.end,
  );
}

// 날짜·시작 시간 순서로 정렬한 새 목록
export const sortSchedules = (items) =>
  [...items].sort((a, b) => (a.date + a.start).localeCompare(b.date + b.start));

// 그날 0~24시 중 일정이 하나도 없는 시간대 [{ start, end }] ('HH:MM')
export function freeRanges(dayItems) {
  const busy = dayItems
    .map((s) => [toMinutes(s.start), toMinutes(s.end)])
    .sort((a, b) => a[0] - b[0]);
  const free = [];
  let cursor = 0;
  for (const [start, end] of busy) {
    if (start > cursor) free.push([cursor, start]);
    cursor = Math.max(cursor, end);
  }
  if (cursor < 24 * 60) free.push([cursor, 24 * 60]);
  return free.map(([start, end]) => ({
    start: fromMinutes(start),
    end: fromMinutes(end),
  }));
}
