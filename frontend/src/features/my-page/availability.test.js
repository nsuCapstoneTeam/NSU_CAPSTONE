// 공연 가능 일정 계산 테스트 (실행: node --test src/features/my-page/availability.test.js)
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  addDays,
  dayTitle,
  findOverlap,
  freeRanges,
  monthCells,
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
