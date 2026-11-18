// 외부 링크·썸네일 거르기 테스트 (실행: node --test src/features/external-accounts/safe-link.test.js)
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { safeAuthorizationUrl, safeLink, safeThumbnail } from './safe-link.js';

const CHANNEL = 'https://www.youtube.com/channel/UCxxxxxxxxxxxxxxxxxxxxxx';
const VIDEO = 'https://www.youtube.com/watch?v=dQw4w9WgXcQ';
const ARTIST = 'https://open.spotify.com/artist/1vCWHaC5f2uS3yhpwWbIA6';

describe('safeLink', () => {
  it('플랫폼마다 허용된 호스트의 https 주소는 그대로 쓴다', () => {
    assert.equal(safeLink('YOUTUBE', CHANNEL), CHANNEL);
    assert.equal(safeLink('YOUTUBE', VIDEO), VIDEO);
    assert.equal(safeLink('SPOTIFY', ARTIST), ARTIST);
  });

  it('http, javascript:, data: 주소는 거절한다', () => {
    assert.equal(safeLink('YOUTUBE', 'http://www.youtube.com/watch?v=dQw4w9WgXcQ'), null);
    assert.equal(safeLink('YOUTUBE', 'javascript:alert(1)'), null);
    assert.equal(safeLink('SPOTIFY', 'data:text/html,<script>alert(1)</script>'), null);
  });

  it('다른 호스트와 호스트를 흉내 낸 주소는 거절한다', () => {
    assert.equal(safeLink('YOUTUBE', 'https://evil.example/watch?v=dQw4w9WgXcQ'), null);
    assert.equal(safeLink('YOUTUBE', 'https://www.youtube.com.evil.example/watch'), null);
    assert.equal(safeLink('YOUTUBE', 'https://www.youtube.com@evil.example/watch'), null);
    assert.equal(safeLink('YOUTUBE', 'https://evil.example/?u=https://www.youtube.com/'), null);
  });

  it('플랫폼과 맞지 않는 호스트는 거절한다', () => {
    assert.equal(safeLink('SPOTIFY', CHANNEL), null);
    assert.equal(safeLink('YOUTUBE', ARTIST), null);
  });

  it('모르는 플랫폼, 문자열이 아닌 값, 빈 값은 거절한다', () => {
    assert.equal(safeLink('SOUNDCLOUD', 'https://soundcloud.com/a'), null);
    assert.equal(safeLink('YOUTUBE', null), null);
    assert.equal(safeLink('YOUTUBE', undefined), null);
    assert.equal(safeLink('YOUTUBE', 42), null);
    assert.equal(safeLink('YOUTUBE', ''), null);
  });
});

describe('safeAuthorizationUrl (동의 화면으로 이동하기 전 확인)', () => {
  it('Google 동의 화면 주소는 그대로 쓴다', () => {
    const url = 'https://accounts.google.com/o/oauth2/v2/auth?client_id=x&state=y';
    assert.equal(safeAuthorizationUrl(url), url);
  });
  it('다른 호스트, http, javascript:, 값이 없으면 null이다', () => {
    assert.equal(safeAuthorizationUrl('https://evil.example/o/oauth2/v2/auth'), null);
    assert.equal(safeAuthorizationUrl('https://accounts.google.com.evil.example/auth'), null);
    assert.equal(safeAuthorizationUrl('http://accounts.google.com/o/oauth2/v2/auth'), null);
    assert.equal(safeAuthorizationUrl('javascript:alert(1)'), null);
    assert.equal(safeAuthorizationUrl(undefined), null);
  });
});

describe('safeThumbnail', () => {
  it('플랫폼 이미지 호스트의 https 주소는 그대로 쓴다', () => {
    for (const url of [
      'https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg',
      'https://yt3.ggpht.com/abc=s88',
      'https://i.scdn.co/image/ab67616d',
    ]) {
      assert.equal(safeThumbnail(url), url);
    }
  });

  it('다른 호스트, http, 빈 값은 null이다', () => {
    assert.equal(safeThumbnail('https://evil.example/a.jpg'), null);
    assert.equal(safeThumbnail('http://i.ytimg.com/vi/a/hqdefault.jpg'), null);
    assert.equal(safeThumbnail('https://i.ytimg.com.evil.example/a.jpg'), null);
    assert.equal(safeThumbnail(null), null);
    assert.equal(safeThumbnail(undefined), null);
  });
});
