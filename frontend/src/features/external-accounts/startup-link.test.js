// 콜백 값 처리 테스트 (실행: node --test src/features/external-accounts/startup-link.test.js)
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { createStartupLink, readLinkCallback } from './startup-link.js';

// 브라우저의 location·history를 흉내 낸 가짜. replaceState 호출을 기록한다
function fakeWindow(url) {
  const u = new URL(url, 'https://app.example');
  const calls = [];
  return {
    location: { pathname: u.pathname, search: u.search, hash: u.hash },
    history: { state: { page: 'mypage' }, replaceState: (...args) => calls.push(args) },
    calls,
  };
}

describe('readLinkCallback', () => {
  it('code와 state가 오면 연결 요청에 쓸 값을 돌려준다', () => {
    assert.deepEqual(
      readLinkCallback('?tab=works&linkProvider=YOUTUBE&linkState=S1&linkCode=C1'),
      { kind: 'connect', provider: 'YOUTUBE', state: 'S1', code: 'C1', tab: 'works' },
    );
  });

  it('사용자가 거절하면 거절 안내용 값을 돌려준다', () => {
    assert.deepEqual(
      readLinkCallback('?tab=works&linkProvider=YOUTUBE&linkError=EXTERNAL_ACCOUNT_AUTHORIZATION_DENIED'),
      { kind: 'error', error: 'EXTERNAL_ACCOUNT_AUTHORIZATION_DENIED', tab: 'works' },
    );
  });

  it('모르는 오류 값은 실패로 바꿔 돌려준다 (주소로 온 값을 화면에 그대로 쓰지 않는다)', () => {
    assert.deepEqual(readLinkCallback('?tab=works&linkError=<script>'), {
      kind: 'error',
      error: 'EXTERNAL_ACCOUNT_AUTHORIZATION_FAILED',
      tab: 'works',
    });
  });

  it('code나 state 중 하나만 있으면 연결 요청을 만들지 않고 실패로 본다', () => {
    assert.equal(readLinkCallback('?tab=works&linkProvider=YOUTUBE&linkState=S1').kind, 'error');
    assert.equal(readLinkCallback('?tab=works&linkProvider=YOUTUBE&linkCode=C1').kind, 'error');
  });

  it('YouTube가 아닌 플랫폼 값은 연결 요청을 만들지 않는다', () => {
    assert.equal(readLinkCallback('?tab=works&linkProvider=SPOTIFY&linkState=S1&linkCode=C1').kind, 'error');
  });

  it('링크 값이 없으면 null이다', () => {
    assert.equal(readLinkCallback('?tab=works'), null);
    assert.equal(readLinkCallback(''), null);
  });
});

describe('createStartupLink', () => {
  const URL_WITH_CODE = '/mypage?tab=works&linkProvider=YOUTUBE&linkState=S1&linkCode=C1#top';

  it('만들 때 주소에서 code·state를 지운다 (해시는 남긴다)', () => {
    const win = fakeWindow(URL_WITH_CODE);
    createStartupLink(win);
    assert.equal(win.calls.length, 1);
    assert.deepEqual(win.calls[0], [{ page: 'mypage' }, '', '/mypage#top']);
  });

  it('claim은 값을 한 번만 준다 (StrictMode가 효과를 두 번 돌려도 연결 요청은 1번)', () => {
    const link = createStartupLink(fakeWindow(URL_WITH_CODE));
    assert.equal(link.claim().code, 'C1');
    assert.equal(link.claim(), null);
  });

  it('peek은 몇 번을 불러도 같은 값을 준다 (처음 열 메뉴를 정하는 데 쓴다)', () => {
    const link = createStartupLink(fakeWindow(URL_WITH_CODE));
    assert.equal(link.peek().tab, 'works');
    assert.equal(link.peek().tab, 'works');
  });

  it('링크 값이 없으면 주소를 건드리지 않는다', () => {
    const win = fakeWindow('/mypage?tab=works');
    const link = createStartupLink(win);
    assert.equal(win.calls.length, 0);
    assert.equal(link.peek(), null);
    assert.equal(link.claim(), null);
  });

  it('마이페이지 주소가 아니면 값을 꺼내지 않고 주소도 건드리지 않는다', () => {
    const win = fakeWindow('/events?linkProvider=YOUTUBE&linkState=S1&linkCode=C1');
    const link = createStartupLink(win);
    assert.equal(win.calls.length, 0);
    assert.equal(link.claim(), null);
  });
});
