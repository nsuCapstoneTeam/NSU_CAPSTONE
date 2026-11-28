// 로그인 세션 테스트 (실행: node --test src/features/auth/auth-session.test.js)
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  createAuthSession,
  displayName,
  isSession,
  normalizeRole,
  pageAfterLogin,
} from './auth-session.js';
import { AuthApiError } from './api/auth-api.js';
import { TOKEN_KEY, createTokenStore } from './api/auth-token.js';

// 가짜 sessionStorage
function memoryStorage() {
  const map = new Map();
  return {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => map.set(k, String(v)),
    removeItem: (k) => map.delete(k),
  };
}
// 정해진 응답을 돌려주거나 오류를 던지는 가짜 로그인 API
const fakeApi = (answer) => ({
  calls: [],
  async login(email, password, options) {
    this.calls.push({ email, password, options });
    if (answer instanceof Error) throw answer;
    return answer;
  },
});
const OK = { accessToken: 'jwt', tokenType: 'Bearer', expiresIn: 1800, userId: 'u1', role: 'EVENT_PARTNER', status: 'ACTIVE' };

function setup({ answer = OK, mode = 'server', storage = memoryStorage(), now = () => 0 } = {}) {
  const tokens = createTokenStore(() => storage, now);
  const api = fakeApi(answer);
  return { auth: createAuthSession({ api, tokens, mode }), api, tokens, storage };
}

describe('역할 이름 맞추기', () => {
  it('세 가지 표기를 화면 역할로 맞추고, 모르는 값은 null', () => {
    assert.equal(normalizeRole('ARTIST'), 'artist');
    assert.equal(normalizeRole('artist'), 'artist');
    assert.equal(normalizeRole('EVENT_PARTNER'), 'organizer');
    assert.equal(normalizeRole('event-partner'), 'organizer');
    assert.equal(normalizeRole('organizer'), 'organizer');
    assert.equal(normalizeRole('ADMIN'), null);
    assert.equal(normalizeRole('toString'), null);
    assert.equal(normalizeRole(undefined), null);
  });
});

describe('로그인 뒤 갈 화면', () => {
  it('돌아갈 화면이 없으면 마이페이지', () => {
    assert.equal(pageAfterLogin(null, 'artist'), 'mypage');
  });
  it('아티스트 매칭은 행사 관계자만, 아티스트는 마이페이지로', () => {
    assert.equal(pageAfterLogin('match', 'organizer'), 'match');
    assert.equal(pageAfterLogin('match', 'artist'), 'mypage');
  });
});

describe('표시 이름', () => {
  it('같은 역할의 프로필 이름이 있으면 그 이름, 없으면 이메일 앞부분', () => {
    assert.equal(displayName('kim@example.com', { name: '김밴드', role: 'artist' }, 'artist'), '김밴드');
    assert.equal(displayName('kim@example.com', { name: '김밴드', role: 'artist' }, 'organizer'), 'kim');
    assert.equal(displayName('kim@example.com', { name: '  ', role: 'artist' }, 'artist'), 'kim');
  });
});

describe('세션 모양', () => {
  it('null(로그아웃)과 {name, role}만 올바른 세션', () => {
    assert.equal(isSession(null), true);
    assert.equal(isSession({ name: 'a', role: 'artist', userId: 'u1' }), true);
    assert.equal(isSession({ name: 'a', role: 'EVENT_PARTNER' }), false);
    assert.equal(isSession({ role: 'artist' }), false);
    assert.equal(isSession('x'), false);
  });
});

describe('로그인', () => {
  it('성공하면 토큰을 저장하고 계정 역할로 세션을 만든다', async () => {
    const { auth, api, storage } = setup();
    const result = await auth.login('kim@example.com', 'password1', { roleHint: 'organizer' });
    assert.deepEqual(result, {
      session: { name: 'kim', role: 'organizer', userId: 'u1' },
      roleChanged: false,
    });
    assert.deepEqual(api.calls[0].options, { roleHint: 'organizer' });
    const saved = JSON.parse(storage.getItem(TOKEN_KEY));
    assert.equal(saved.accessToken, 'jwt');
    assert.equal(saved.password, undefined);
  });

  it('로그인한 화면과 계정 역할이 다르면 계정 역할로 들어가고 roleChanged', async () => {
    const { auth } = setup();
    const result = await auth.login('kim@example.com', 'password1', { roleHint: 'artist' });
    assert.equal(result.session.role, 'organizer');
    assert.equal(result.roleChanged, true);
  });

  it('실패하면 오류를 그대로 던지고 예전 토큰도 남기지 않는다', async () => {
    const { auth, tokens, storage } = setup({ answer: new AuthApiError('INVALID_LOGIN_CREDENTIALS', 401) });
    tokens.save(OK);
    await assert.rejects(auth.login('kim@example.com', 'wrong-pass'), { code: 'INVALID_LOGIN_CREDENTIALS' });
    assert.equal(storage.getItem(TOKEN_KEY), null);
  });

  it('모르는 역할이 오면 로그인하지 않는다', async () => {
    const { auth } = setup({ answer: { ...OK, role: 'ADMIN' } });
    await assert.rejects(auth.login('kim@example.com', 'password1'), { code: 'UNKNOWN_ERROR' });
  });

  it('서버 모드에서 토큰을 저장하지 못하면 로그인하지 않는다', async () => {
    const broken = { getItem: () => null, setItem: () => { throw new Error('full'); }, removeItem: () => {} };
    const { auth } = setup({ storage: broken });
    await assert.rejects(auth.login('kim@example.com', 'password1'), { code: 'SESSION_STORAGE_FAILED' });
  });

  it('체험 모드는 토큰 저장이 막혀도 로그인한다', async () => {
    const broken = { getItem: () => null, setItem: () => { throw new Error('full'); }, removeItem: () => {} };
    const { auth } = setup({ storage: broken, mode: 'demo' });
    const result = await auth.login('kim@example.com', 'password1');
    assert.equal(result.session.role, 'organizer');
  });
});

describe('저장된 세션 확인과 로그아웃', () => {
  const saved = { name: 'kim', role: 'organizer', userId: 'u1' };

  it('서버 모드: 토큰이 있으면 그대로, 없거나 만료되면 null', () => {
    let time = 0;
    const { auth, tokens } = setup({ now: () => time });
    assert.equal(auth.restore(saved), null);
    tokens.save(OK);
    assert.deepEqual(auth.restore(saved), saved);
    time = 1800 * 1000;
    assert.equal(auth.restore(saved), null);
  });

  it('체험 모드: 토큰이 없어도 저장된 세션을 쓴다', () => {
    const { auth } = setup({ mode: 'demo' });
    assert.deepEqual(auth.restore(saved), saved);
    assert.equal(auth.restore(null), null);
    assert.equal(auth.restore({ name: 'x', role: 'ADMIN' }), null);
  });

  it('로그아웃하면 토큰을 지운다', () => {
    const { auth, tokens, storage } = setup();
    tokens.save(OK);
    auth.logout();
    assert.equal(storage.getItem(TOKEN_KEY), null);
  });

  it('토큰 없이 시작하는 체험 세션은 체험 모드에서만', () => {
    assert.equal(setup({ mode: 'demo' }).auth.canStartWithoutLogin, true);
    assert.equal(setup({ mode: 'server' }).auth.canStartWithoutLogin, false);
  });
});
