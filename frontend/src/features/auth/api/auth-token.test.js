// 로그인 토큰 보관함 테스트 (실행: node --test src/features/auth/api/auth-token.test.js)
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { TOKEN_KEY, createTokenStore, toAppRole } from './auth-token.js';

// 가짜 sessionStorage
function memoryStorage() {
  const map = new Map();
  return {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => map.set(k, String(v)),
    removeItem: (k) => map.delete(k),
    map,
  };
}
const LOGIN = { accessToken: 'jwt', expiresIn: 3600, userId: 'u1', role: 'ARTIST' };

describe('토큰 보관함', () => {
  it('저장하면 만료 시각을 계산해 두고, 비밀번호 같은 다른 값은 남기지 않는다', () => {
    const storage = memoryStorage();
    const store = createTokenStore(() => storage, () => 1000);
    assert.equal(store.save({ ...LOGIN, password: 'secret' }), true);
    assert.deepEqual(JSON.parse(storage.getItem(TOKEN_KEY)), {
      accessToken: 'jwt',
      expiresAt: 1000 + 3600 * 1000,
      userId: 'u1',
      role: 'ARTIST',
    });
  });

  it('만료 전에는 읽히고, 만료되면 null을 주고 지운다', () => {
    const storage = memoryStorage();
    let time = 0;
    const store = createTokenStore(() => storage, () => time);
    store.save(LOGIN);
    time = 3600 * 1000 - 1;
    assert.equal(store.read()?.accessToken, 'jwt');
    time = 3600 * 1000;
    assert.equal(store.read(), null);
    assert.equal(storage.getItem(TOKEN_KEY), null);
  });

  it('Authorization 헤더를 만들어 주고, 로그인 전에는 빈 객체', () => {
    const storage = memoryStorage();
    const store = createTokenStore(() => storage, () => 0);
    assert.deepEqual(store.authHeader(), {});
    store.save(LOGIN);
    assert.deepEqual(store.authHeader(), { Authorization: 'Bearer jwt' });
  });

  it('clear()로 로그아웃하면 토큰이 사라진다', () => {
    const storage = memoryStorage();
    const store = createTokenStore(() => storage, () => 0);
    store.save(LOGIN);
    store.clear();
    assert.equal(store.read(), null);
  });

  it('깨진 값이나 토큰 없는 응답은 저장·사용하지 않는다', () => {
    const storage = memoryStorage();
    const store = createTokenStore(() => storage, () => 0);
    assert.equal(store.save({ ...LOGIN, accessToken: '' }), false);
    storage.setItem(TOKEN_KEY, '{망가진 JSON');
    assert.equal(store.read(), null);
  });

  it('저장소를 쓸 수 없는 환경에서도 오류 없이 false·null', () => {
    const store = createTokenStore(() => {
      throw new Error('SecurityError');
    });
    assert.equal(store.save(LOGIN), false);
    assert.equal(store.read(), null);
    assert.deepEqual(store.authHeader(), {});
  });

  it('저장할 때 저장소를 한 번만 가져온다', () => {
    const storage = memoryStorage();
    let calls = 0;
    // 첫 호출만 저장소를 주고, 그 뒤로는 없음 → 두 번 부르면 결과가 어긋남
    const store = createTokenStore(() => (calls++ === 0 ? storage : null), () => 0);
    assert.equal(store.save(LOGIN), true);
    assert.equal(calls, 1);
    assert.ok(storage.getItem(TOKEN_KEY));
  });

  it('백엔드 역할을 화면 역할로 바꾼다', () => {
    assert.equal(toAppRole('ARTIST'), 'artist');
    assert.equal(toAppRole('EVENT_PARTNER'), 'organizer');
    assert.equal(toAppRole('ADMIN'), null);
  });
});
