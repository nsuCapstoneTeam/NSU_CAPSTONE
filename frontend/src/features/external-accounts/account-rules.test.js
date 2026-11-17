// 외부 계정 화면 규칙 테스트 (실행: node --test src/features/external-accounts/account-rules.test.js)
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  MAX_WORKS,
  accountToReplace,
  displayNameOf,
  followUpFor,
  toggleWork,
  verificationText,
} from './account-rules.js';

const YOUTUBE = { externalAccountId: 'yt-1', provider: 'YOUTUBE', isControlVerified: true };
const SPOTIFY = { externalAccountId: 'sp-1', provider: 'SPOTIFY', isControlVerified: false };

describe('accountToReplace (교체 확인 대상)', () => {
  it('그 플랫폼 계정이 이미 있으면 그 계정을 돌려준다 (먼저 "바꿀까요?"를 묻는다)', () => {
    assert.equal(accountToReplace([YOUTUBE, SPOTIFY], 'YOUTUBE'), YOUTUBE);
    assert.equal(accountToReplace([YOUTUBE, SPOTIFY], 'SPOTIFY'), SPOTIFY);
  });
  it('없으면 null이다 (replaceExternalAccountId에 null을 보낸다)', () => {
    assert.equal(accountToReplace([SPOTIFY], 'YOUTUBE'), null);
    assert.equal(accountToReplace([], 'YOUTUBE'), null);
    assert.equal(accountToReplace(undefined, 'YOUTUBE'), null);
  });
});

describe('toggleWork (작업물 고르기)', () => {
  it('고르지 않은 영상을 누르면 맨 뒤에 붙는다 (고른 순서가 표시 순서)', () => {
    assert.deepEqual(toggleWork(['a'], 'b'), ['a', 'b']);
  });
  it('이미 고른 영상을 누르면 빠지고 나머지 순서는 그대로다', () => {
    assert.deepEqual(toggleWork(['a', 'b', 'c'], 'b'), ['a', 'c']);
  });
  it('10개를 넘기려는 선택은 무시한다', () => {
    const full = Array.from({ length: MAX_WORKS }, (_, i) => `v${i}`);
    assert.deepEqual(toggleWork(full, 'extra'), full);
    assert.deepEqual(toggleWork(full, 'v0'), full.slice(1));
  });
  it('원래 배열을 바꾸지 않는다', () => {
    const before = ['a'];
    toggleWork(before, 'b');
    assert.deepEqual(before, ['a']);
  });
});

describe('verificationText (확인 상태 문구)', () => {
  it('YouTube는 "채널 제어권 확인"이다', () => {
    assert.equal(verificationText(YOUTUBE), '채널 제어권 확인');
  });
  it('Spotify는 "Spotify 아티스트 프로필 연결됨"이다', () => {
    assert.equal(verificationText(SPOTIFY), 'Spotify 아티스트 프로필 연결됨');
  });
  it('"인증"이라고 쓰지 않는다', () => {
    assert.ok(!verificationText(YOUTUBE).includes('인증'));
    assert.ok(!verificationText(SPOTIFY).includes('인증'));
  });
});

describe('displayNameOf (이름)', () => {
  it('이름이 있으면 그대로 쓴다', () => {
    assert.equal(displayNameOf({ ...YOUTUBE, displayName: '밴드 이름' }), '밴드 이름');
  });
  it('이름을 받지 못했으면(null) 링크는 남기고 안내 문구를 쓴다', () => {
    assert.equal(displayNameOf({ ...YOUTUBE, displayName: null }), 'YouTube 채널');
    assert.equal(displayNameOf({ ...SPOTIFY, displayName: null }), 'Spotify 아티스트');
  });
});

describe('followUpFor (오류 뒤에 할 일)', () => {
  it('연결이 그사이 바뀌었으면 목록을 다시 읽고 처음부터 다시 묻는다', () => {
    assert.equal(followUpFor('EXTERNAL_ACCOUNT_CONNECTION_CHANGED'), 'reload');
  });
  it('이미 해제됐거나 없는 연결이면 목록을 다시 읽는다', () => {
    assert.equal(followUpFor('EXTERNAL_ACCOUNT_NOT_FOUND'), 'reload');
  });
  it('후보에 없는 영상이면 후보를 다시 읽는다', () => {
    assert.equal(followUpFor('EXTERNAL_ACCOUNT_WORK_NOT_CANDIDATE'), 'reload');
  });
  it('연결 요청이 끝나지 않은 채 오류가 나면 같은 값을 다시 보내지 않고 목록으로 결과를 확인한다', () => {
    assert.equal(followUpFor('NETWORK_ERROR', { afterConnectRequest: true }), 'reload');
    assert.equal(followUpFor('EXTERNAL_PROVIDER_UNAVAILABLE', { afterConnectRequest: true }), 'reload');
  });
  it('그 밖의 오류는 안내만 하고 다시 읽지 않는다', () => {
    assert.equal(followUpFor('YOUTUBE_CHANNEL_NOT_FOUND'), 'none');
    assert.equal(followUpFor('TOO_MANY_REQUESTS'), 'none');
  });
});
