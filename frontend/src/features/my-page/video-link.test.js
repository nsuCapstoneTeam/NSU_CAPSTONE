// 공연 영상 링크 해석 테스트 (실행: node --test src/features/my-page/video-link.test.js)
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { embedUrl, parseVideoUrl } from './video-link.js';

describe('parseVideoUrl', () => {
  it('YouTube 여러 형식의 주소를 읽는다', () => {
    const want = { platform: 'youtube', videoId: 'dQw4w9WgXcQ' };
    assert.deepEqual(parseVideoUrl('https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10s'), want);
    assert.deepEqual(parseVideoUrl('https://youtu.be/dQw4w9WgXcQ?si=abc'), want);
    assert.deepEqual(parseVideoUrl('https://m.youtube.com/shorts/dQw4w9WgXcQ'), want);
    assert.deepEqual(parseVideoUrl('  https://youtube.com/live/dQw4w9WgXcQ  '), want);
  });
  it('Vimeo 주소를 읽는다', () => {
    assert.deepEqual(parseVideoUrl('https://vimeo.com/76979871'), {
      platform: 'vimeo',
      videoId: '76979871',
    });
  });
  it('지원하지 않거나 잘못된 주소는 null', () => {
    assert.equal(parseVideoUrl('https://example.com/video.mp4'), null);
    assert.equal(parseVideoUrl('youtube.com/watch?v=dQw4w9WgXcQ'), null);
    assert.equal(parseVideoUrl('https://www.youtube.com/watch?v=short'), null);
    assert.equal(parseVideoUrl('javascript:alert(1)'), null);
  });
});

describe('embedUrl', () => {
  it('저장된 값으로 재생 주소를 만든다', () => {
    assert.equal(
      embedUrl({ platform: 'youtube', videoId: 'dQw4w9WgXcQ' }),
      'https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ',
    );
    assert.equal(
      embedUrl({ platform: 'vimeo', videoId: '76979871' }),
      'https://player.vimeo.com/video/76979871',
    );
  });
  it('형식이 틀린 값은 null (저장소가 바뀌어도 이상한 주소를 열지 않음)', () => {
    assert.equal(embedUrl({ platform: 'youtube', videoId: '"><script>' }), null);
    assert.equal(embedUrl({ platform: 'other', videoId: '123456' }), null);
  });
});
