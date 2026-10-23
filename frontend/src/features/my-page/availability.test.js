// 공연 가능 일정 계산 테스트 (실행: node --test src/features/my-page/availability.test.js)
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  ALL_DAY,
  PICKABLE_STATUSES,
  TIME_OPTIONS,
  addDays,
  availableHours,
  buildWeekItems,
  clipToView,
  timeChoices,
  dayTitle,
  findOverlap,
  freeRanges,
  monthCells,
  shortRange,
  sortSchedules,
  statusOf,
  weekDays,
} from './availability.js';

describe('statusOf', () => {
  it('상태가 없던 예전 일정은 가능(AVAILABLE)으로 본다', () => {
    assert.equal(statusOf({ date: '2026-10-15' }), 'AVAILABLE');
    assert.equal(statusOf({ status: 'WRONG' }), 'AVAILABLE');
    assert.equal(statusOf({ status: 'HOLD' }), 'HOLD');
  });
});

describe('monthCells', () => {
  it('2026년 10월은 목요일 시작, 일요일 시작 달력 5주(35칸)', () => {
    const cells = monthCells(2026, 9);
    assert.equal(cells.length, 35);
    assert.equal(cells[0].key, '2026-09-27');
    assert.equal(cells[4].key, '2026-10-01');
    assert.equal(cells[4].inMonth, true);
    assert.equal(cells[0].inMonth, false);
  });
  it('2026년 8월은 6주(42칸)가 필요하다', () => {
    assert.equal(monthCells(2026, 7).length, 42);
  });
});

describe('addDays', () => {
  it('달과 해를 넘긴다', () => {
    assert.equal(addDays('2026-10-31', 1), '2026-11-01');
    assert.equal(addDays('2027-01-01', -1), '2026-12-31');
  });
});

describe('findOverlap', () => {
  const items = [{ id: 'a', date: '2026-10-15', start: '18:00', end: '20:00' }];
  it('같은 날 겹치면 찾는다', () => {
    assert.ok(findOverlap(items, { date: '2026-10-15', start: '19:00', end: '21:00' }));
  });
  it('끝과 시작이 맞닿기만 하면 겹치지 않는다', () => {
    assert.equal(findOverlap(items, { date: '2026-10-15', start: '20:00', end: '22:00' }), undefined);
  });
  it('수정 중인 자기 자신은 제외한다', () => {
    assert.equal(findOverlap(items, { date: '2026-10-15', start: '18:30', end: '19:30' }, 'a'), undefined);
  });
});

describe('freeRanges', () => {
  it('일정 사이의 빈 시간을 돌려준다', () => {
    const free = freeRanges([
      { start: '18:00', end: '20:00' },
      { start: '09:00', end: '12:00' },
    ]);
    assert.deepEqual(free, [
      { start: '00:00', end: '09:00' },
      { start: '12:00', end: '18:00' },
      { start: '20:00', end: '24:00' },
    ]);
  });
  it('일정이 없으면 하루 전체가 빈다', () => {
    assert.deepEqual(freeRanges([]), [{ start: '00:00', end: '24:00' }]);
  });
});

describe('sortSchedules', () => {
  it('날짜·시작 시간 순서로 정렬한다', () => {
    const sorted = sortSchedules([
      { date: '2026-10-16', start: '10:00' },
      { date: '2026-10-15', start: '18:00' },
      { date: '2026-10-15', start: '09:00' },
    ]);
    assert.deepEqual(sorted.map((s) => s.date + s.start), [
      '2026-10-1509:00',
      '2026-10-1518:00',
      '2026-10-1610:00',
    ]);
  });
});

describe('weekDays', () => {
  it('목요일이 속한 주의 일요일~토요일을 돌려준다', () => {
    assert.deepEqual(weekDays('2026-10-15'), [
      '2026-10-11', '2026-10-12', '2026-10-13', '2026-10-14',
      '2026-10-15', '2026-10-16', '2026-10-17',
    ]);
  });
  it('달이 바뀌는 주도 이어서 계산한다', () => {
    assert.equal(weekDays('2026-10-01')[0], '2026-09-27');
  });
});

describe('dayTitle', () => {
  it('월·일·요일로 표시한다', () => {
    assert.equal(dayTitle('2026-10-15'), '10월 15일 (목)');
  });
});

describe('PICKABLE_STATUSES', () => {
  it('확정은 직접 고를 수 없다', () => {
    assert.deepEqual(PICKABLE_STATUSES.map((s) => s.value), ['AVAILABLE', 'HOLD', 'UNAVAILABLE']);
  });
});

describe('TIME_OPTIONS', () => {
  it('00:00부터 24:00까지 30분 간격이다', () => {
    assert.equal(TIME_OPTIONS.length, 49);
    assert.equal(TIME_OPTIONS[0], '00:00');
    assert.equal(TIME_OPTIONS[1], '00:30');
    assert.equal(TIME_OPTIONS.at(-1), '24:00');
  });
});

describe('availableHours', () => {
  it('가능 상태만 더한다 (상태 없는 예전 일정은 가능)', () => {
    const hours = availableHours([
      { start: '14:00', end: '18:00', status: 'AVAILABLE' },
      { start: '19:00', end: '20:30' },
      { start: '10:00', end: '12:00', status: 'HOLD' },
      { start: '17:00', end: '19:00', status: 'BOOKED' },
    ]);
    assert.equal(hours, 5.5);
  });
  it('일정이 없으면 0', () => {
    assert.equal(availableHours([]), 0);
  });
});

describe('shortRange', () => {
  it('정시는 시만, 30분은 그대로 표시한다', () => {
    assert.equal(shortRange('13:00', '18:00'), '13–18시');
    assert.equal(shortRange('13:30', '18:00'), '13:30–18시');
  });
  it('하루 종일은 종일로 표시한다', () => {
    assert.equal(shortRange(ALL_DAY.start, ALL_DAY.end), '종일');
  });
});

describe('buildWeekItems', () => {
  const week = weekDays('2026-10-18');
  it('고른 요일마다 같은 시간 일정을 만든다', () => {
    const items = buildWeekItems(week, {
      days: [5, 4],
      start: '19:00',
      end: '22:00',
      allDay: false,
      status: 'AVAILABLE',
      title: ' 홍대 버스킹 ',
    });
    assert.deepEqual(items, [
      { date: '2026-10-22', start: '19:00', end: '22:00', status: 'AVAILABLE', title: '홍대 버스킹' },
      { date: '2026-10-23', start: '19:00', end: '22:00', status: 'AVAILABLE', title: '홍대 버스킹' },
    ]);
  });
  it('All Day면 00:00–24:00으로 만들고, 일정이름이 비면 넣지 않는다', () => {
    const [item] = buildWeekItems(week, {
      days: [1],
      start: '19:00',
      end: '22:00',
      allDay: true,
      status: 'UNAVAILABLE',
      title: '',
    });
    assert.deepEqual(item, {
      date: '2026-10-19',
      start: '00:00',
      end: '24:00',
      status: 'UNAVAILABLE',
      allDay: true,
    });
  });
});

describe('clipToView', () => {
  it('오전 8시 ~ 밤 12시 안으로 자른다', () => {
    assert.deepEqual(clipToView('19:00', '22:00'), [1140, 1320]);
    assert.deepEqual(clipToView('00:00', '24:00'), [480, 1440]);
    assert.deepEqual(clipToView('06:00', '10:00'), [480, 600]);
  });
  it('보이는 시간 밖에만 있으면 null', () => {
    assert.equal(clipToView('05:00', '08:00'), null);
  });
});

describe('timeChoices', () => {
  it('시작은 08:00~23:30, 끝은 08:30~24:00', () => {
    const start = timeChoices('start', '19:00');
    const end = timeChoices('end', '22:00');
    assert.equal(start[0], '08:00');
    assert.equal(start.at(-1), '23:30');
    assert.equal(end[0], '08:30');
    assert.equal(end.at(-1), '24:00');
  });
  it('범위 밖 값을 고치는 중이면 그 값도 넣는다', () => {
    assert.equal(timeChoices('start', '06:00')[0], '06:00');
  });
});
