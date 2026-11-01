// 추천 결과 테스트 (실행: node --test src/features/matching/song-results.test.js)
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  FIRST_PAGE,
  beatTracePath,
  bpmPosition,
  buildSongResults,
  compareWantedTempo,
  conditionDetail,
  findResultArtist,
  formatCreatedAt,
  formatDuration,
  songTrack,
} from './song-results.js';
import { ARTISTS } from './matching.js';

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

describe('BPM 위치와 재생 시간', () => {
  it('BPM 위치는 60~160 사이로 자른다', () => {
    assert.equal(bpmPosition(40), 0);
    assert.equal(bpmPosition(102), 42);
    assert.equal(bpmPosition(200), 100);
  });
  it('재생 시간은 분:초로 보여 준다', () => {
    assert.equal(formatDuration(72), '1:12');
    assert.equal(formatDuration(5.9), '0:05');
    assert.equal(formatDuration(-3), '0:00');
  });
});

describe('재생기에 넘길 곡 정보', () => {
  it('추천 곡은 BPM·박자를 넘긴다', () => {
    const item = buildSongResults().items[0];
    assert.deepEqual(songTrack(item), { key: item.music.id, duration: 72, bpm: 98, beats: item.music.beats });
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

describe('관심 저장용 아티스트 id', () => {
  it('매칭 샘플과 이름이 같은 아티스트는 같은 id를 쓴다', () => {
    const items = buildSongResults().items;
    const rhythm = items.find((i) => i.artist.name === '리듬크루');
    assert.equal(rhythm.artist.id, ARTISTS.find((a) => a.name === '리듬크루').id);
    assert.equal(new Set(items.map((i) => i.artist.id)).size, items.length);
  });
  it('저장한 id로 체험용 아티스트와 대표 곡을 찾는다', () => {
    const top = buildSongResults().items[0];
    assert.equal(findResultArtist(top.artist.id).songTitle, '새벽의 정원');
    assert.equal(findResultArtist('없는-id'), null);
  });
});

describe('리듬 그래프와 필수 조건 문구', () => {
  it('박이 있는 칸마다 선이 솟고, 마디 수만큼 반복한다', () => {
    const path = beatTracePath([1, 0, 0, 0], { cellWidth: 10, bars: 2, baseline: 50, amp: 20 });
    assert.equal((path.match(/ 30/g) || []).length, 2); // 50 - 20 = 30 (솟은 점)
    assert(path.startsWith('M 0 50'));
    assert(path.endsWith('L 80 50'));
  });
  it('필수 조건 둘째 줄을 행사 조건으로 채운다', () => {
    const event = { date: '2026-10-31', region: '서울', budget: 80 };
    assert.equal(conditionDetail('SCHEDULE', event), '10월 31일 (토) 공연 가능');
    assert.equal(conditionDetail('REGION', event), '서울 활동');
    assert.equal(conditionDetail('BUDGET', event), '최대 80만 원 안');
    assert.equal(conditionDetail('COPYRIGHT'), '작업물 권리 확인');
    assert.equal(conditionDetail('SCHEDULE'), '');
  });
});
