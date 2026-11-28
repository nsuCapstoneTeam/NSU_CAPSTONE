import test from 'node:test';
import assert from 'node:assert/strict';
import { fromResponse, toRequestBody } from './event-api-mapper.js';
import { EMPTY_DETAILS, normalizeEvent } from '../my-page/organizer-events.js';
import { currentGenres, genreCode } from './event-codes.js';

// 화면에서 저장하려는 행사 (normalizeEvent를 거친 값)
const EVENT = normalizeEvent({
  name: ' 2026 가을 축제 ',
  eventType: '대학 축제',
  date: '2026-10-30',
  startTime: '22:00',
  endTime: '02:00',
  address: '서울 마포구 연남로 45',
  regionCode: '11440',
  region: '',
  venue: '대운동장 무대',
  artistType: '상관없음',
  genres: ['록', '힙합·랩'],
  budgetMin: '30',
  budgetMax: '50',
  audience: '500',
  performanceMinutes: '40',
  description: '야외 무대',
  notes: '주차 2대',
  details: { ...EMPTY_DETAILS, rehearsal: '30분', isMealProvided: true, isPaProvided: false },
  stagePlan: { name: 'plan.pdf', size: 10 },
  visibility: 'PRIVATE',
});

test('요청 본문: 코드로 바꾸고, 만 원을 원으로, 다음 날 종료 날짜를 넣는다', () => {
  const body = toRequestBody(EVENT);
  assert.equal(body.name, '2026 가을 축제');
  assert.equal(body.eventType, 'UNIVERSITY_FESTIVAL');
  assert.equal(body.startDate, '2026-10-30');
  assert.equal(body.endDate, '2026-10-31');
  assert.equal(body.artistType, null);
  assert.deepEqual(body.genres, ['ROCK', 'HIPHOP']);
  assert.equal(body.budgetMin, 300000);
  assert.equal(body.budgetMax, 500000);
  assert.equal(body.expectedAudience, 500);
  assert.equal(body.regionCode, '11440');
  assert.equal(body.visibility, 'PRIVATE');
  assert.equal(body.details.rehearsal, '30분');
  assert.equal(body.details.stageSize, null);
  assert.equal(body.details.isMealProvided, true);
  assert.equal(body.details.isPaProvided, false);
  assert.equal(body.details.isAccommodationProvided, null);
  assert.equal(body.details.otherConditions, '주차 2대');
});

test('요청 본문에는 주소와 무대 도면을 넣지 않는다 (API에 칸이 없음)', () => {
  const body = toRequestBody(EVENT);
  assert.equal('address' in body, false);
  assert.equal('stagePlan' in body, false);
  assert.equal(JSON.stringify(body).includes('연남로'), false);
});

test('응답을 화면 행사로: 한국어 이름, 만 원 단위, 상태', () => {
  const response = {
    id: 'evt-1',
    eventPartnerId: 'p-1',
    status: 'CANCELLED',
    createdAt: '2026-10-01T00:00:00Z',
    updatedAt: '2026-10-01T00:00:00Z',
    ...toRequestBody(EVENT),
  };
  const event = fromResponse(response);
  assert.equal(event.id, 'evt-1');
  assert.equal(event.status, 'CANCELLED');
  assert.equal(event.eventType, '대학 축제');
  assert.equal(event.artistType, '상관없음');
  assert.deepEqual(event.genres, ['록', '힙합·랩']);
  assert.equal(event.budgetMin, 30);
  assert.equal(event.audience, 500);
  assert.equal(event.date, '2026-10-30');
  assert.equal(event.endDate, '2026-10-31');
  assert.equal(event.address, '');
  assert.equal(event.stagePlan, null);
  assert.equal(event.notes, '주차 2대');
  assert.equal(event.details.stageSize, '');
  assert.equal(event.details.isMealProvided, true);
});

test('예전 체험 장르 이름도 코드로 바꾸고, 모르는 코드는 그대로 둔다', () => {
  assert.equal(genreCode('힙합'), 'HIPHOP');
  assert.equal(genreCode('DJ'), 'ELECTRONIC');
  assert.deepEqual(currentGenres(['힙합', '보컬', '재즈']), ['힙합·랩', '재즈']);
  const event = fromResponse({ ...toRequestBody(EVENT), id: 'e', status: 'OPEN', genres: ['NEW_GENRE'], eventType: 'NEW_TYPE' });
  assert.deepEqual(event.genres, ['NEW_GENRE']);
  assert.equal(event.eventType, 'NEW_TYPE');
});
