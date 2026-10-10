import test from 'node:test';
import assert from 'node:assert/strict';
import { DEFAULT_EVENT } from './matching.js';
import { eventOptionLabel, eventToSearch } from './event-to-search.js';

// 마이페이지 '내 행사'에 저장된 모양의 행사
const EVENT = {
  id: 'e1',
  name: '2026 가을 대학 축제',
  eventType: '대학 축제',
  date: '2026-10-22',
  startTime: '19:00',
  endTime: '21:00',
  venue: '대운동장',
  region: '경기',
  artistType: '밴드',
  genres: ['밴드'],
  budgetMin: 30,
  budgetMax: 60,
  audience: 500,
  performanceMinutes: 40,
  description: '야외 무대 밴드 공연',
  visibility: 'PUBLIC',
};

test('행사 정보를 매칭 조건으로 옮긴다 (예산은 최대값)', () => {
  const { search, skipped } = eventToSearch(EVENT, DEFAULT_EVENT);
  assert.equal(search.title, '2026 가을 대학 축제');
  assert.equal(search.description, '야외 무대 밴드 공연');
  assert.equal(search.date, '2026-10-22');
  assert.equal(search.start, '19:00');
  assert.equal(search.minutes, 40);
  assert.equal(search.budget, 60);
  assert.equal(search.region, '경기');
  assert.equal(search.type, '대학 축제');
  assert.equal(search.genre, '밴드');
  assert.deepEqual(skipped, []);
});

test('행사에 없는 조건(분위기·템포·리듬)은 기존 값을 유지한다', () => {
  const { search } = eventToSearch(EVENT, DEFAULT_EVENT);
  assert.equal(search.mood, DEFAULT_EVENT.mood);
  assert.equal(search.tempo, DEFAULT_EVENT.tempo);
  assert.equal(search.rhythm, DEFAULT_EVENT.rhythm);
});

test('매칭 선택지에 없는 행사 종류는 기존 값을 두고 안내한다', () => {
  const { search, skipped } = eventToSearch({ ...EVENT, eventType: '지역 축제' }, DEFAULT_EVENT);
  assert.equal(search.type, DEFAULT_EVENT.type);
  assert.equal(skipped.length, 1);
  assert.match(skipped[0], /지역 축제/);
});

test('장르가 여러 개면 선택지에 있는 첫 번째를 쓰고 알려 준다', () => {
  const { search, skipped } = eventToSearch({ ...EVENT, genres: ['재즈', '보컬'] }, DEFAULT_EVENT);
  assert.equal(search.genre, '재즈');
  assert.match(skipped[0], /재즈/);
});

test('원본 행사와 기존 조건은 바꾸지 않는다', () => {
  const before = JSON.stringify(DEFAULT_EVENT);
  eventToSearch(EVENT, DEFAULT_EVENT);
  assert.equal(JSON.stringify(DEFAULT_EVENT), before);
});

test('선택 상자 이름: 날짜를 붙이고 지난 행사를 표시한다', () => {
  assert.equal(eventOptionLabel(EVENT, '2026-10-10'), '2026 가을 대학 축제 · 10월 22일');
  assert.equal(eventOptionLabel(EVENT, '2026-10-23'), '2026 가을 대학 축제 · 10월 22일 (지난 행사)');
});
