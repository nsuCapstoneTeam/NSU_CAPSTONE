// 참고 곡 파일 검사 테스트 (실행: node --test src/features/matching/reference-song.test.js)
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { checkDuration, checkFileBasics, extensionOf, formatDuration, formatSize } from './reference-song.js';

const MB = 1024 * 1024;

describe('참고 곡 형식·크기 검사', () => {
  it('MP3와 M4A는 대소문자와 상관없이 통과한다', () => {
    assert.equal(checkFileBasics({ name: 'wedding_ref.mp3', size: 3 * MB }), null);
    assert.equal(checkFileBasics({ name: 'LIVE.M4A', size: 3 * MB }), null);
  });
  it('WAV는 바꿔서 올리라고 안내한다', () => {
    const r = checkFileBasics({ name: 'song.wav', size: MB });
    assert.equal(r.kind, 'format');
    assert.match(r.fix, /MP3나 M4A/);
  });
  it('다른 형식과 확장자 없는 파일은 형식 오류다', () => {
    assert.equal(checkFileBasics({ name: 'song.flac', size: MB }).kind, 'format');
    assert.equal(checkFileBasics({ name: 'song', size: MB }).kind, 'format');
  });
  it('10MB까지는 통과하고 넘으면 지금 크기를 알려 준다', () => {
    assert.equal(checkFileBasics({ name: 'a.mp3', size: 10 * MB }), null);
    const r = checkFileBasics({ name: 'a.mp3', size: 11 * MB });
    assert.equal(r.kind, 'size');
    assert.match(r.title, /11MB/);
  });
});

describe('참고 곡 길이 검사', () => {
  it('60초~80초는 통과한다 (초 단위 소수점은 버림)', () => {
    assert.equal(checkDuration(60), null);
    assert.equal(checkDuration(72.4), null);
    assert.equal(checkDuration(80.9), null);
  });
  it('짧거나 길면 지금 길이를 알려 준다', () => {
    assert.match(checkDuration(59.9).title, /짧아요 \(지금 0:59\)/);
    assert.match(checkDuration(81).title, /길어요 \(지금 1:21\)/);
  });
  it('길이를 읽지 못하면 재생할 수 없는 파일로 본다', () => {
    assert.equal(checkDuration(NaN).kind, 'broken');
    assert.equal(checkDuration(Infinity).kind, 'broken');
  });
});

describe('표시 형식', () => {
  it('확장자·시간·크기', () => {
    assert.equal(extensionOf('a.b.MP3'), 'mp3');
    assert.equal(extensionOf('.hidden'), '');
    assert.equal(formatDuration(72), '1:12');
    assert.equal(formatDuration(5.9), '0:05');
    assert.equal(formatSize(2.8 * MB), '2.8MB');
    assert.equal(formatSize(10 * MB), '10MB');
    assert.equal(formatSize(500 * 1024), '500KB');
  });
});
