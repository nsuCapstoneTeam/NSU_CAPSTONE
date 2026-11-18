import test from 'node:test';
import assert from 'node:assert/strict';
import { DISTRICTS, PROVINCES } from '../../data/regions.js';
import { eventProblems, isEventList, toFormValues } from '../my-page/organizer-events.js';
import { DEMO_PUBLIC_EVENTS } from './event-search-data.js';
import {
  countMap,
  dateParts,
  districtName,
  eventsIn,
  provinceName,
  provinceOf,
  publicEvents,
  shortRegion,
  venuePoint,
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
  const counts = countMap(DEMO_PUBLIC_EVENTS);
  assert.ok(
    PROVINCES.every((p) => counts[p.code] > 0),
    PROVINCES.filter((p) => !(counts[p.code] > 0)).map((p) => p.short).join(', '),
  );
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

test('지도용 행사 수: 전국은 시·도별, 시·도를 고르면 시·군·구별', () => {
  const events = [
    { regionCode: '11440' },
    { regionCode: '11440' },
    { regionCode: '11110' },
    { regionCode: '26350' },
  ];
  assert.deepEqual(countMap(events), { 11: 3, 26: 1 });
  assert.deepEqual(countMap(events, '11'), { 11440: 2, 11110: 1 });
  assert.deepEqual(countMap(events, '50'), {});
});

test('시·군·구를 고르면 그 시·군·구 행사만', () => {
  const mapo = eventsIn(DEMO_PUBLIC_EVENTS, '11', '11440');
  assert.ok(mapo.length > 0);
  assert.ok(mapo.every((e) => e.regionCode === '11440'));
  assert.ok(eventsIn(DEMO_PUBLIC_EVENTS, '11').length > mapo.length);
  assert.equal(districtName('11440'), '마포구');
  assert.equal(districtName('99999'), '');
});

test('공연장 좌표: 숫자·문자열 좌표를 [경도, 위도]로, 없거나 한국 밖이면 null', () => {
  assert.deepEqual(venuePoint({ venueLat: 37.56, venueLng: 126.92 }), [126.92, 37.56]);
  assert.deepEqual(venuePoint({ venueLat: '37.56', venueLng: '126.92' }), [126.92, 37.56]);
  assert.equal(venuePoint({ venueLat: null, venueLng: null }), null);
  assert.equal(venuePoint({}), null);
  assert.equal(venuePoint({ venueLat: '', venueLng: '' }), null);
  assert.equal(venuePoint({ venueLat: 'abc', venueLng: 126.92 }), null);
  assert.equal(venuePoint({ venueLat: 126.92, venueLng: 37.56 }), null); // 위도·경도를 바꿔 넣은 경우
  assert.equal(venuePoint({ venueLat: 40.7, venueLng: -74 }), null);
  const withPoint = DEMO_PUBLIC_EVENTS.filter((e) => venuePoint(e));
  assert.equal(withPoint.length, DEMO_PUBLIC_EVENTS.length - 1);
});
