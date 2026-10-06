// 회원가입 규칙 테스트 (실행: node --test src/features/auth/signup-rules.test.js)
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { canComplete, isAdult, isValidEmail, isValidPhone, missingItems, phoneDigits } from './signup-rules.js';

// 테스트 기준일을 고정 (실행하는 날에 따라 결과가 바뀌지 않게)
const TODAY = new Date(2026, 9, 5); // 2026-10-05 (월은 0부터 시작해서 9 = 10월)

// 모든 조건을 채운 일반가입 상태 (테스트마다 일부만 바꿔서 사용)
const doneEmail = {
  method: 'email',
  email: 'artist@example.com',
  emailVerified: true,
  password: 'abcd1234',
  passwordConfirm: 'abcd1234',
  phoneVerified: true,
  birthDate: '2000-01-01',
  terms: { service: true, privacy: true },
};

// 라벨만 뽑아서 비교하면 읽기 쉬움
const labels = (state) => missingItems(state, TODAY).map((m) => m.label);

describe('missingItems', () => {
  it('가입 방법을 고르기 전에는 그것만 묻는다', () => {
    assert.deepEqual(labels({}), ['가입 방법 선택']);
  });

  it('모두 채운 일반가입은 빠진 항목이 없다', () => {
    assert.deepEqual(labels(doneEmail), []);
    assert.equal(canComplete(doneEmail, TODAY), true);
  });

  it('이메일을 입력만 하고 인증하지 않으면 이메일 인증이 빠진다', () => {
    assert.deepEqual(labels({ ...doneEmail, emailVerified: false }), ['이메일 인증']);
  });

  it('비밀번호가 짧거나 확인이 다르면 알려 준다', () => {
    assert.deepEqual(labels({ ...doneEmail, password: 'abc', passwordConfirm: 'abc' }), ['비밀번호 (8자 이상)']);
    assert.deepEqual(labels({ ...doneEmail, passwordConfirm: 'different' }), ['비밀번호 확인 일치']);
  });

  it('간편가입은 비밀번호를 묻지 않지만 휴대폰 인증과 약관은 필수다', () => {
    const oauth = { method: 'google', email: 'g@example.com', emailVerified: true, terms: {} };
    assert.deepEqual(labels(oauth), ['휴대폰 인증', '생년월일', '이용약관 동의', '개인정보처리방침 동의']);
  });

  it('이메일이 없는 간편가입(Kakao)은 이메일부터 받는다', () => {
    const kakao = { ...doneEmail, method: 'kakao', email: '', emailVerified: false };
    assert.deepEqual(labels(kakao), ['이메일']);
  });
});

describe('canComplete', () => {
  it('이미 가입된 이메일이면 모두 채워도 끝낼 수 없다', () => {
    assert.equal(canComplete({ ...doneEmail, emailTaken: true }, TODAY), false);
  });
});

describe('isAdult (만 나이)', () => {
  it('18번째 생일 당일부터 가입할 수 있다', () => {
    assert.equal(isAdult('2008-10-05', TODAY), true);
    assert.equal(isAdult('2008-10-06', TODAY), false);
  });
  it('형식이 틀린 날짜는 통과하지 않는다', () => {
    assert.equal(isAdult('2008/10/05', TODAY), false);
    assert.equal(isAdult('', TODAY), false);
  });
  it('미성년자는 빠진 항목에 안내가 나온다', () => {
    assert.deepEqual(labels({ ...doneEmail, birthDate: '2010-01-01' }), ['만 18세 이상만 가입할 수 있어요']);
  });
});

describe('이메일·휴대폰 형식', () => {
  it('이메일은 아이디@도메인.끝 형태여야 한다', () => {
    assert.equal(isValidEmail(' artist@example.com '), true);
    assert.equal(isValidEmail('artist@example'), false);
    assert.equal(isValidEmail('artist example.com'), false);
  });
  it('휴대폰은 하이픈을 빼고 010 + 8자리', () => {
    assert.equal(phoneDigits('010-1234-5678'), '01012345678');
    assert.equal(isValidPhone('010-1234-5678'), true);
    assert.equal(isValidPhone('011-1234-5678'), false);
    assert.equal(isValidPhone('010-123-456'), false);
  });
});
