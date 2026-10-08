import test from 'node:test';
import assert from 'node:assert/strict';
import {
  EMPTY_EVENT,
  REQUIRED_FIELDS,
  budgetText,
  eventProblems,
  isEventList,
  isPast,
  normalizeEvent,
  sortEvents,
  toFormValues,
} from './organizer-events.js';

// 필수 항목을 모두 채운 행사
const FULL = {
  ...EMPTY_EVENT,
  name: '2026 가을 축제',
  eventType: '대학 축제',
  date: '2026-10-30',
  startTime: '18:00',
  endTime: '20:00',
  venue: '대운동장 무대',
  region: '서울',
  artistType: '밴드',
  genres: ['밴드'],
  budgetMin: '30',
  budgetMax: '50',
  audience: '500',
  performanceMinutes: '40',
  description: '야외 무대, 학생 대상',
  visibility: 'PUBLIC',
};
const ids = (problems) => problems.map((p) => p.id);

test('빈 행사는 필수 항목이 모두 빠졌다고 알려 준다 (공개 범위는 기본값이 있어 제외)', () => {
  assert.deepEqual(
    ids(eventProblems(EMPTY_EVENT)),
    REQUIRED_FIELDS.map(([id]) => id).filter((id) => id !== 'visibility'),
  );
});

test('모두 채우면 고칠 항목이 없다', () => {
  assert.deepEqual(eventProblems(FULL), []);
});

test('공백만 넣은 칸과 빈 장르 목록은 빈칸으로 본다', () => {
  assert.deepEqual(ids(eventProblems({ ...FULL, name: '   ', genres: [] })), ['name', 'genres']);
});

test('숫자 칸은 1 이상 정수만 허용한다', () => {
  const problems = eventProblems({ ...FULL, audience: '0', performanceMinutes: '1.5' });
  assert.deepEqual(ids(problems), ['audience', 'performanceMinutes']);
  assert.match(problems[0].label, /숫자/);
});

test('예산 최소가 최대보다 크면 예산 최대를 고치라고 한다 (같으면 통과)', () => {
  assert.deepEqual(ids(eventProblems({ ...FULL, budgetMin: '60', budgetMax: '50' })), ['budgetMax']);
  assert.deepEqual(eventProblems({ ...FULL, budgetMin: '50', budgetMax: '50' }), []);
});

test('종료 시간이 시작 시간과 같거나 빠르면 알려 준다', () => {
  assert.deepEqual(ids(eventProblems({ ...FULL, endTime: '18:00' })), ['endTime']);
  assert.deepEqual(ids(eventProblems({ ...FULL, endTime: '17:30' })), ['endTime']);
});

test('빈칸이면 형식 오류를 중복으로 알리지 않는다', () => {
  assert.equal(ids(eventProblems({ ...FULL, budgetMax: '' })).filter((id) => id === 'budgetMax').length, 1);
});

test('저장용 정리: 공백 제거, 숫자 변환 / 폼용 변환은 다시 문자열로', () => {
  const saved = normalizeEvent({ ...FULL, name: '  축제  ' });
  assert.equal(saved.name, '축제');
  assert.equal(saved.budgetMin, 30);
  const form = toFormValues({ ...saved, id: 'e1' });
  assert.equal(form.budgetMin, '30');
  assert.equal(form.id, 'e1');
});

test('날짜·시작 시간 순으로 정렬하고 원본은 그대로 둔다', () => {
  const list = [
    { id: 'b', date: '2026-11-01', startTime: '10:00' },
    { id: 'c', date: '2026-10-30', startTime: '19:00' },
    { id: 'a', date: '2026-10-30', startTime: '18:00' },
  ];
  assert.deepEqual(sortEvents(list).map((e) => e.id), ['a', 'c', 'b']);
  assert.equal(list[0].id, 'b');
});

test('지난 행사 판정과 예산 표시', () => {
  assert.equal(isPast({ date: '2026-10-07' }, '2026-10-08'), true);
  assert.equal(isPast({ date: '2026-10-08' }, '2026-10-08'), false);
  assert.equal(budgetText({ budgetMin: 30, budgetMax: 50 }), '30~50만 원');
  assert.equal(budgetText({ budgetMin: 50, budgetMax: 50 }), '50만 원');
});

test('저장소 형식 검사', () => {
  assert.equal(isEventList([{ ...normalizeEvent(FULL), id: 'e1' }]), true);
  assert.equal(isEventList([{ name: '이름만' }]), false);
  assert.equal(isEventList('x'), false);
});
