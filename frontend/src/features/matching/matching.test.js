// 매칭 로직 테스트 (실행: node --test src/features/matching/matching.test.js)
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  ARTISTS,
  DEFAULT_EVENT,
  matchArtists,
  scoreArtist,
  validateEvent,
  explainLead,
} from './matching.js';
describe('설명 가능한 조건 추천', () => {
  it('필수 조건을 통과한 최대 5명을 실제 평균으로 정렬한다', () => {
    const { results } = matchArtists(DEFAULT_EVENT);
    assert.equal(results.length, 5);
    assert.equal(results[0].id, 'a1');
    for (const a of results) {
      assert(a.checks.every((c) => c.pass));
      assert.equal(
        a.score,
        a.metrics.reduce((sum, m) => sum + m.score, 0) / a.metrics.length,
      );
    }
    assert(results.every((a, i) => i === 0 || a.score <= results[i - 1].score));
  });
  it('검증·권리·활동·일정·지역·예산 중 하나라도 미충족이면 제외한다', () => {
    for (const override of [
      { verified: false },
      { rights: false },
      { suspended: true },
      { availability: [] },
      { regions: ['부산'] },
      { fee: 51 },
    ]) {
      assert.equal(
        matchArtists(DEFAULT_EVENT, [{ ...ARTISTS[0], ...override }]).results
          .length,
        0,
      );
    }
    assert.equal(
      matchArtists({ ...DEFAULT_EVENT, start: '22:50' }).results.length,
      0,
    );
    assert.equal(
      matchArtists({ ...DEFAULT_EVENT, date: '2026-10-16' }).results.length,
      0,
    );
    assert.equal(
      matchArtists({ ...DEFAULT_EVENT, minutes: 90 }).results.length,
      0,
    );
  });
  it('무관 항목을 평균에서 제외하고 범위 밖 BPM을 감점한다', () => {
    const a = scoreArtist(ARTISTS[0], {
      ...DEFAULT_EVENT,
      tempo: '무관',
      rhythm: '무관',
    });
    assert.equal(a.metrics.length, 2);
    assert.equal(a.score, 100);
    const b = scoreArtist({ ...ARTISTS[0], bpm: 118 }, DEFAULT_EVENT);
    assert.equal(b.metrics.find((m) => m.key === 'bpm').score, 90);
  });
  it('동점은 출연료, ID 순이며 설명도 이를 명시한다', () => {
    const r = matchArtists(DEFAULT_EVENT, [
      { ...ARTISTS[0], id: 'z', fee: 49 },
      { ...ARTISTS[0], id: 'b', fee: 40 },
      { ...ARTISTS[0], id: 'a', fee: 40 },
    ]).results;
    assert.deepEqual(
      r.map((a) => a.id),
      ['a', 'b', 'z'],
    );
    assert.match(explainLead(r[0], r[1]), /고유 ID/);
    assert.match(explainLead(r[1], r[2]), /출연료/);
  });
  it('사업자 상태는 점수를 바꾸지 않는다', () =>
    assert.equal(
      scoreArtist({ ...ARTISTS[0], business: false }, DEFAULT_EVENT).score,
      scoreArtist({ ...ARTISTS[0], business: true }, DEFAULT_EVENT).score,
    ));
  it('빈 행사명, 잘못된 예산, 자정 초과를 거절한다', () => {
    assert(validateEvent({ ...DEFAULT_EVENT, title: ' ' }));
    assert(validateEvent({ ...DEFAULT_EVENT, budget: 'abc' }));
    assert(validateEvent({ ...DEFAULT_EVENT, start: '23:50' }));
    assert.equal(validateEvent(DEFAULT_EVENT), '');
  });
});
