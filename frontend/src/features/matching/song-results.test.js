// 참고 곡으로 찾기 결과 테스트 (실행: node --test src/features/matching/song-results.test.js)
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  FIRST_PAGE,
  REFERENCE_SAMPLE,
  bpmPosition,
  buildSongResults,
  compareBeats,
  compareTempo,
  compareWantedTempo,
  formatCreatedAt,
  referenceTrack,
  songTrack,
} from './song-results.js';

describe('체험용 결과 (계약 모양)', () => {
  const results = buildSongResults(new Date(2026, 9, 24, 15, 20));

  it('음악 유사도 순서로 순위가 1부터 이어진다', () => {
    const { items } = results;
    assert(items.length > FIRST_PAGE);
    items.forEach((item, i) => {
      assert.equal(item.rank, i + 1);
      assert(Number.isInteger(item.musicSimilarity));
      if (i > 0) assert(item.musicSimilarity <= items[i - 1].musicSimilarity);
    });
  });
  it('곡 id가 겹치지 않고, 길이는 60~80초, 박자는 16칸이다', () => {
    const ids = new Set(results.items.map((i) => i.music.id));
    assert.equal(ids.size, results.items.length);
    for (const { music } of results.items) {
      assert(music.seconds >= 60 && music.seconds <= 80);
      assert.equal(music.beats.length, 16);
    }
  });
  it('1위는 Figma 시안과 같은 하늘색 밴드 · 새벽의 정원 86이다', () => {
    const [top] = results.items;
    assert.equal(top.artist.name, '하늘색 밴드');
    assert.equal(top.music.title, '새벽의 정원');
    assert.equal(top.musicSimilarity, 86);
  });
  it('처음 받은 결과는 조건 바뀜 없이 뺀 곡 수만 있다', () => {
    assert.equal(results.isEventChanged, false);
    assert.equal(results.excludedCount, 2);
    assert.equal(formatCreatedAt(results.createdAt), '10월 24일 오후 3:20');
  });
});

describe('템포 비교', () => {
  it('차이에 따라 거의 같음 / 조금 / 많이', () => {
    assert.deepEqual(compareTempo(102, 98), { title: '참고 곡보다 조금 느려요', diff: 4 });
    assert.equal(compareTempo(102, 104).title, '참고 곡과 빠르기가 거의 같아요');
    assert.equal(compareTempo(102, 124).title, '참고 곡보다 많이 빨라요');
  });
  it('BPM 위치는 60~160 사이로 자른다', () => {
    assert.equal(bpmPosition(40), 0);
    assert.equal(bpmPosition(102), 42);
    assert.equal(bpmPosition(200), 100);
  });
});

describe('박자 비교', () => {
  it('참고 곡의 박 중 이 곡과 겹치는 수를 센다', () => {
    const { total, same, cells } = compareBeats(REFERENCE_SAMPLE.beats, buildSongResults().items[0].music.beats);
    assert.equal(total, 6);
    assert.equal(same, 6);
    assert.equal(cells.length, 16);
    assert.equal(cells[15], 'song');
  });
  it('겹치지 않는 박은 어느 곡의 박인지 표시한다', () => {
    const r = compareBeats([1, 1, 0, 0], [1, 0, 1, 0]);
    assert.deepEqual(r.cells, ['both', 'reference', 'song', '']);
    assert.equal(r.same, 1);
  });
});

describe('재생기에 넘길 곡 정보', () => {
  it('추천 곡은 BPM·박자, 참고 곡은 파일 주소를 넘긴다', () => {
    const item = buildSongResults().items[0];
    assert.deepEqual(songTrack(item), { key: item.music.id, duration: 72, bpm: 98, beats: item.music.beats });
    assert.deepEqual(referenceTrack({ duration: 72, url: 'blob:x' }), {
      key: 'reference',
      duration: 72,
      url: 'blob:x',
    });
  });
});

describe('행사 설명으로 찾기: 원하는 템포와 비교', () => {
  it('원하는 구간보다 느림 / 빠름 / 맞음', () => {
    assert.equal(compareWantedTempo('FAST', 98).title, '원하는 빠르기(빠름)보다 느려요');
    assert.equal(compareWantedTempo('SLOW', 98).title, '원하는 빠르기(느림)보다 빨라요');
    assert.equal(compareWantedTempo('MEDIUM', 98).title, '원하는 빠르기(보통)와 맞아요');
    assert.equal(compareWantedTempo('MEDIUM', 119).title, '원하는 빠르기(보통)와 맞아요');
  });
  it('무관이면 비교하지 않는다', () => {
    assert.equal(compareWantedTempo('무관', 98), null);
  });
});
