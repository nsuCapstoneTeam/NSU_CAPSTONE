import test from 'node:test';
import assert from 'node:assert/strict';
import {
  EMPTY_EVENT,
  NOTES_MAX,
  REQUIRED_FIELDS,
  budgetText,
  eventProblems,
  isEventList,
  isPast,
  normalizeEvent,
  sortEvents,
  spanMinutes,
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
  address: '서울 마포구 연남로 45',
  regionCode: '11440',
  venue: '대운동장 무대',
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

test('저장할 때 지역 이름을 코드로 만들고, 특이사항·주소의 공백을 지운다', () => {
  const saved = normalizeEvent({ ...FULL, notes: '  리허설 18:00  ', address: ' 서울 마포구 연남로 45 ' });
  assert.equal(saved.region, '서울특별시 마포구');
  assert.equal(saved.notes, '리허설 18:00');
  assert.equal(saved.address, '서울 마포구 연남로 45');
});

test('예전에 저장한 행사(지역 이름만 있음)는 폼에서 장소를 다시 고르게 한다', () => {
  const old = { ...normalizeEvent(FULL), id: 'old', region: '서울' };
  for (const key of ['address', 'regionCode', 'notes', 'stagePlan']) delete old[key];
  assert.equal(isEventList([old]), true);
  const form = toFormValues(old);
  assert.equal(form.regionCode, '');
  assert.equal(form.notes, '');
  assert.equal(form.stagePlan, null);
  assert.deepEqual(ids(eventProblems(form)), ['regionCode']);
  // 다시 고르지 않고 저장해도 예전 지역 이름은 남는다
  assert.equal(normalizeEvent({ ...form, regionCode: '' }).region, '서울');
});

test('특이사항은 500자까지 받는다', () => {
  assert.equal(NOTES_MAX, 500);
});

test('비워 둘 시간의 길이(분)', () => {
  assert.equal(spanMinutes('19:00', '19:40'), 40);
  assert.equal(spanMinutes('18:00', '21:00'), 180);
  assert.equal(spanMinutes('19:00', '19:00'), 0);
  assert.equal(spanMinutes('', '19:00'), 0);
});

test('공연 시간은 선택: 비우면 비워 둘 시간 전체로 저장한다', () => {
  assert.equal(REQUIRED_FIELDS.some(([id]) => id === 'performanceMinutes'), false);
  const blank = { ...FULL, performanceMinutes: '' };
  assert.deepEqual(eventProblems(blank), []);
  assert.equal(normalizeEvent(blank).performanceMinutes, 120);
  assert.equal(normalizeEvent(FULL).performanceMinutes, 40);
});

test('공연 시간은 비워 둘 시간보다 길 수 없다', () => {
  const problems = eventProblems({ ...FULL, performanceMinutes: '150' });
  assert.deepEqual(ids(problems), ['performanceMinutes']);
  assert.match(problems[0].label, /120분 이하/);
  assert.deepEqual(eventProblems({ ...FULL, performanceMinutes: '120' }), []);
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

test('저장소 형식 검사: 화면에서 쓰는 칸이 하나라도 없거나 형식이 틀리면 거부', () => {
  const saved = { ...normalizeEvent(FULL), id: 'e1' };
  assert.equal(isEventList([saved]), true);
  assert.equal(isEventList([]), true);
  assert.equal(isEventList([{ name: '이름만' }]), false);
  assert.equal(isEventList('x'), false);
  // 화면이 toLocaleString()을 부르는 숫자 칸이 빠진 예전 데이터
  const { audience, ...noAudience } = saved;
  assert.equal(isEventList([noAudience]), false);
  assert.equal(isEventList([{ ...saved, budgetMax: '50' }]), false);
  assert.equal(isEventList([{ ...saved, venue: undefined }]), false);
  assert.equal(isEventList([{ ...saved, genres: [1] }]), false);
  // 무대 도면은 없거나 { name, size } 모양
  assert.equal(isEventList([{ ...saved, stagePlan: { name: '도면.pdf', size: 1000, type: 'application/pdf' } }]), true);
  assert.equal(isEventList([{ ...saved, stagePlan: '도면.pdf' }]), false);
  assert.equal(isEventList([{ ...saved, notes: 3 }]), false);
});
