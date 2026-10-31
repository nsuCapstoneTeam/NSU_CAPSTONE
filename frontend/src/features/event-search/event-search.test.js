import test from 'node:test';
import assert from 'node:assert/strict';
import { DISTRICTS, PROVINCES } from '../../data/regions.js';
import { eventProblems, isEventList, toFormValues } from '../my-page/organizer-events.js';
import { DEMO_PUBLIC_EVENTS } from './event-search-data.js';
import {
  dateParts,
  eventsIn,
  provinceName,
  provinceOf,
  publicEvents,
  regionCounts,
  shortRegion,
} from './event-search.js';

test('예시 행사는 행사 등록 폼과 같은 형식이고 규칙에도 맞는다', () => {
  assert.ok(isEventList(DEMO_PUBLIC_EVENTS));
  assert.equal(new Set(DEMO_PUBLIC_EVENTS.map((e) => e.id)).size, DEMO_PUBLIC_EVENTS.length);
  for (const e of DEMO_PUBLIC_EVENTS) {
    assert.deepEqual(eventProblems(toFormValues(e)), [], e.name);
    assert.ok(
      DISTRICTS.some((d) => d.code === e.regionCode),
      `${e.name}: 지역 코드 ${e.regionCode}가 지역 목록에 없음`,
    );
  }
});

test('예시 행사는 16개 시·도에 모두 하나 이상 있다', () => {
  const counts = regionCounts(DEMO_PUBLIC_EVENTS);
  assert.equal(counts.length, PROVINCES.length);
  assert.ok(counts.every((r) => r.count > 0));
});

test('시·도별 행사 수는 많은 순, 같으면 시·도 목록 순서', () => {
  const events = [
    { regionCode: '41110' },
    { regionCode: '11440' },
    { regionCode: '41130' },
    { regionCode: '26350' },
  ];
  const counts = regionCounts(events);
  assert.deepEqual(
    counts.slice(0, 3).map((r) => [r.short, r.count]),
    [
      ['경기', 2],
      ['서울', 1],
      ['부산', 1],
    ],
  );
  assert.equal(counts.at(-1).count, 0);
});

test('공개 행사만 고른다', () => {
  const list = publicEvents([{ visibility: 'PUBLIC' }, { visibility: 'PRIVATE' }]);
  assert.equal(list.length, 1);
});

test('고른 시·도의 행사를 날짜·시간순으로 고르고, 비우면 전체', () => {
  const events = [
    { id: 'b', regionCode: '11440', date: '2026-11-08', startTime: '12:00' },
    { id: 'a', regionCode: '11110', date: '2026-11-01', startTime: '14:00' },
    { id: 'c', regionCode: '41110', date: '2026-11-01', startTime: '09:00' },
  ];
  assert.deepEqual(
    eventsIn(events, '11').map((e) => e.id),
    ['a', 'b'],
  );
  assert.deepEqual(
    eventsIn(events).map((e) => e.id),
    ['c', 'a', 'b'],
  );
  assert.deepEqual(eventsIn(events, '50'), []);
  // 원본 순서는 바꾸지 않음
  assert.equal(events[0].id, 'b');
});

test('날짜 칸과 지역 이름', () => {
  assert.deepEqual(dateParts({ date: '2026-11-01', startTime: '14:00', endTime: '15:00' }), {
    day: '11.01',
    when: '일 14:00~15:00',
  });
  assert.equal(shortRegion({ regionCode: '11440' }), '서울 마포구');
  assert.equal(shortRegion({ regionCode: '36110' }), '세종');
  assert.equal(shortRegion({ regionCode: '12210' }), '전남광주 동구');
  // 예전 행사처럼 코드가 없으면 저장된 이름
  assert.equal(shortRegion({ regionCode: '', region: '서울' }), '서울');
  assert.equal(provinceOf({ regionCode: '41110' }), '41');
  assert.equal(provinceName('41'), '경기도');
  assert.equal(provinceName('99'), '');
});
