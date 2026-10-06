// 회원가입 API 창구 테스트 (실행: node --test src/features/auth/api/auth-api.test.js)
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { AuthApiError, createAuthApi, messageFor } from './auth-api.js';

// 가짜 fetch: 보낸 요청을 기록하고, 정해 둔 응답을 돌려줌
function fakeFetch(status, body = '') {
  const calls = [];
  const fn = async (url, options) => {
    calls.push({ url, method: options.method, body: JSON.parse(options.body) });
    return { ok: status < 400, status, text: async () => (body ? JSON.stringify(body) : '') };
  };
  fn.calls = calls;
  return fn;
}

describe('서버 모드', () => {
  it('세션 생성은 역할별 주소로 이메일·휴대폰을 보낸다', async () => {
    const fetchImpl = fakeFetch(201, { signupSessionId: 'abc' });
    const api = createAuthApi({ mode: 'server', fetchImpl });
    const result = await api.createSession('artist', { email: 'a@b.com', phone: '01012345678' });
    assert.deepEqual(result, { signupSessionId: 'abc' });
    assert.deepEqual(fetchImpl.calls[0], {
      url: '/api/v1/auth/signup/artist/session',
      method: 'POST',
      body: { email: 'a@b.com', phone: '01012345678' },
    });
  });

  it('행사 관계자는 event-partner 주소를 쓰고, 약관 동의는 PUT으로 보낸다', async () => {
    const fetchImpl = fakeFetch(204);
    const api = createAuthApi({ mode: 'server', fetchImpl });
    assert.equal(await api.agreeTerms('event-partner', 's1'), null); // 204 = 본문 없음
    assert.equal(fetchImpl.calls[0].url, '/api/v1/auth/signup/event-partner/session/required-terms-agreement');
    assert.equal(fetchImpl.calls[0].method, 'PUT');
  });

  it('실패 응답의 code를 AuthApiError로 바꾸고 한국어 문구를 준다', async () => {
    const api = createAuthApi({
      mode: 'server',
      fetchImpl: fakeFetch(409, { code: 'EMAIL_ALREADY_EXISTS', message: '이미 가입된 이메일입니다.' }),
    });
    await assert.rejects(api.signUp('artist', 's1', 'abcd1234'), (error) => {
      assert.ok(error instanceof AuthApiError);
      assert.equal(error.code, 'EMAIL_ALREADY_EXISTS');
      assert.equal(error.status, 409);
      assert.match(messageFor(error), /이미 가입된 이메일/);
      return true;
    });
  });

  it('서버에 연결하지 못하면 NETWORK_ERROR', async () => {
    const api = createAuthApi({
      mode: 'server',
      fetchImpl: async () => {
        throw new TypeError('Failed to fetch');
      },
    });
    await assert.rejects(api.sendEmailCode('artist', 's1'), { code: 'NETWORK_ERROR' });
  });

  it('간편가입은 아직 서버 API가 없어 준비 중으로 안내한다', async () => {
    const api = createAuthApi({ mode: 'server', fetchImpl: fakeFetch(200) });
    await assert.rejects(api.startOAuth('google'), { code: 'OAUTH_NOT_READY' });
  });
});

describe('데모 모드', () => {
  const demo = () => createAuthApi({ mode: 'demo', delayMs: 0 });

  // 일반가입을 끝까지 진행하는 도우미
  async function signUpAll(api, email) {
    const { signupSessionId: id } = await api.createSession('artist', { email, phone: '01012345678' });
    await api.confirmEmailCode('artist', id, '123456');
    await api.confirmPhoneCode('artist', id, '123456');
    await api.agreeTerms('artist', id);
    await api.confirmAdult('artist', id, '2000-01-01');
    return api.signUp('artist', id, 'abcd1234');
  }

  it('모든 단계를 마치면 ARTIST·ACTIVE 계정이 만들어진다', async () => {
    const user = await signUpAll(demo(), 'new@example.com');
    assert.equal(user.role, 'ARTIST');
    assert.equal(user.status, 'ACTIVE');
  });

  it('taken@example.com 은 마지막 단계에서 중복으로 막힌다', async () => {
    await assert.rejects(signUpAll(demo(), 'taken@example.com'), { code: 'EMAIL_ALREADY_EXISTS' });
  });

  it('인증 없이 가입하면 백엔드처럼 빠진 조건을 알려 준다', async () => {
    const api = demo();
    const { signupSessionId: id } = await api.createSession('artist', { email: 'a@b.com', phone: '010' });
    await assert.rejects(api.signUp('artist', id, 'abcd1234'), { code: 'EMAIL_VERIFICATION_REQUIRED' });
  });

  it('인증번호가 숫자 6자리가 아니면 틀린 번호로 처리한다', async () => {
    const api = demo();
    const { signupSessionId: id } = await api.createSession('artist', { email: 'a@b.com', phone: '010' });
    await assert.rejects(api.confirmEmailCode('artist', id, '12ab'), { code: 'VERIFICATION_CODE_INVALID' });
  });

  it('미성년자는 만 18세 확인에서 막힌다', async () => {
    const api = demo();
    const { signupSessionId: id } = await api.createSession('artist', { email: 'a@b.com', phone: '010' });
    await assert.rejects(api.confirmAdult('artist', id, '2015-01-01'), { code: 'ADULT_REQUIREMENT_NOT_MET' });
  });

  it('간편가입: Google은 인증된 이메일, Kakao는 이메일 없음', async () => {
    const api = demo();
    assert.equal((await api.startOAuth('google')).emailVerified, true);
    assert.deepEqual(await api.startOAuth('kakao'), { provider: 'kakao', email: '', emailVerified: false });
  });
});
