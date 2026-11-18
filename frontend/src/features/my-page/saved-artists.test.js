import test from 'node:test';
import assert from 'node:assert/strict';
import { ARTISTS } from '../matching/matching.js';
import { buildSongResults } from '../matching/song-results.js';
import { isSavedArtistList, savedArtistCards } from './saved-artists.js';

// 추천 결과에만 있는 체험용 아티스트 (매칭 샘플 ARTISTS에 없어 song-artist-N id를 씀)
const resultOnly = buildSongResults(new Date(0)).items.find((i) => i.artist.id.startsWith('song-artist-')).artist;

test('추천 결과에만 있는 아티스트를 저장해도 새로고침 때 목록을 그대로 읽는다 (NSU-139)', () => {
  assert.equal(isSavedArtistList(['a1', resultOnly.id]), true);
  assert.equal(isSavedArtistList([]), true);
});

test('지금 데이터에 없는 id가 섞여 있어도 목록 전체를 버리지 않는다', () => {
  assert.equal(isSavedArtistList(['a1', '예전-아티스트']), true);
});

test('배열이 아니거나 id가 문자열이 아니면 저장 목록으로 읽지 않는다', () => {
  assert.equal(isSavedArtistList('a1'), false);
  assert.equal(isSavedArtistList({ 0: 'a1' }), false);
  assert.equal(isSavedArtistList(['a1', 3]), false);
  assert.equal(isSavedArtistList(['a1', '']), false);
  assert.equal(isSavedArtistList(['a1', null]), false);
});

test('저장 카드: 매칭 샘플과 추천 결과 아티스트를 모두 보여 주고, 모르는 id만 건너뛴다', () => {
  const sample = ARTISTS[0];
  const cards = savedArtistCards([sample.id, '예전-아티스트', resultOnly.id]);
  assert.deepEqual(
    cards.map((c) => c.id),
    [sample.id, resultOnly.id],
  );
  assert.equal(cards[0].name, sample.name);
  assert.equal(cards[1].name, resultOnly.name);
  assert.equal(cards[1].sub, '추천 결과 예시 아티스트');
});
