// 회원가입 규칙 (AUTH-006·008·157, 백엔드 가입 API #99·#101·#103 기준)
// 화면(React)과 상관없이 "지금 상태에서 무엇이 빠졌는지"만 계산하는 순수 함수 모음입니다.
// 같은 입력이면 항상 같은 결과를 돌려주므로 브라우저 없이 테스트할 수 있습니다.
// (실행 테스트: node --test src/features/auth/signup-rules.test.js)

// 가입 방법: 이메일 일반가입 + 간편가입 3종
export const SIGNUP_METHODS = ['email', 'google', 'kakao', 'naver'];

// 비밀번호 최소 길이 (백엔드도 8자 이상: @Size(min = 8))
export const PASSWORD_MIN = 8;

// 인증번호 규칙 (백엔드 auth.properties와 같은 값)
export const OTP_TTL_SECONDS = 5 * 60; // 인증번호 유효시간 5분
export const RESEND_COOLDOWN_SECONDS = 60; // 다시 받기는 60초 뒤부터
export const MAX_ATTEMPTS = 5; // 인증번호 확인은 5번까지

// 가입 가능 나이 (AUTH-006)
export const ADULT_AGE = 18;

// 필수 약관 [저장 키, 표시 이름]
// (백엔드에 보낼 약관 ID·버전은 팀 확정 필요: auth-api.js에서 관리)
export const REQUIRED_TERMS = [
  ['service', '이용약관 동의'],
  ['privacy', '개인정보처리방침 동의'],
];

// 생년월일('YYYY-MM-DD')이 오늘(today) 기준 만 18세 이상인지
// 생일이 지나야 한 살을 더하는 '만 나이'로 계산합니다. today를 받는 이유: 테스트에서 날짜를 고정하기 위해
export function isAdult(birthDate, today = new Date()) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(birthDate || '');
  if (!match) return false;
  const [, y, m, d] = match.map(Number);
  let age = today.getFullYear() - y;
  const beforeBirthday =
    today.getMonth() + 1 < m || (today.getMonth() + 1 === m && today.getDate() < d);
  if (beforeBirthday) age -= 1;
  return age >= ADULT_AGE;
}

// 이메일 형식 (아이디@도메인.끝)
export const isValidEmail = (email) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test((email || '').trim());

// 휴대폰 번호에서 숫자만 남김 ('010-1234-5678' → '01012345678')
export const phoneDigits = (phone) => (phone || '').replace(/\D/g, '');

// 010으로 시작하는 11자리 휴대폰 번호인지 (하이픈은 있어도 됨)
export const isValidPhone = (phone) => /^010\d{8}$/.test(phoneDigits(phone));

// 간편가입(Google·Kakao·Naver)인지
export const isOAuth = (method) => ['google', 'kakao', 'naver'].includes(method);

// 빠진 필수 항목 목록 [{ id, label }]
// id는 화면에서 그 입력칸의 id이기도 해서, 목록을 누르면 해당 칸으로 이동할 수 있습니다.
// state 예: { method, email, emailVerified, password, passwordConfirm, phoneVerified, birthDate, terms: { service, privacy } }
export function missingItems(state, today = new Date()) {
  // 가입 방법을 고르기 전에는 다른 항목을 묻지 않음
  if (!SIGNUP_METHODS.includes(state.method)) {
    return [{ id: 'signup-method', label: '가입 방법 선택' }];
  }

  const missing = [];

  // 이메일: 일반가입·간편가입 모두 필수 (간편가입은 인증된 이메일이면 이미 인증 완료)
  if (!state.email?.trim()) missing.push({ id: 'signup-email', label: '이메일' });
  else if (!state.emailVerified) missing.push({ id: 'signup-email', label: '이메일 인증' });

  // 비밀번호: 일반가입만 (간편가입 계정은 비밀번호 없음, AUTH-157)
  if (state.method === 'email') {
    if ((state.password || '').length < PASSWORD_MIN) {
      missing.push({ id: 'signup-password', label: `비밀번호 (${PASSWORD_MIN}자 이상)` });
    } else if (state.password !== state.passwordConfirm) {
      missing.push({ id: 'signup-password-confirm', label: '비밀번호 확인 일치' });
    }
  }

  // 휴대폰 인증: 모든 가입에서 필수
  if (!state.phoneVerified) missing.push({ id: 'signup-phone', label: '휴대폰 인증' });

  // 만 18세 이상: 생년월일로 확인 (백엔드도 생년월일로 다시 확인)
  if (!state.birthDate) missing.push({ id: 'signup-birth', label: '생년월일' });
  else if (!isAdult(state.birthDate, today)) {
    missing.push({ id: 'signup-birth', label: `만 ${ADULT_AGE}세 이상만 가입할 수 있어요` });
  }

  // 필수 약관
  for (const [key, label] of REQUIRED_TERMS) {
    if (!state.terms?.[key]) missing.push({ id: `signup-terms-${key}`, label });
  }

  return missing;
}

// 가입을 끝낼 수 있는지: 빠진 항목이 없고, 이미 가입된 이메일이 아니어야 함 (AUTH-158)
export const canComplete = (state, today = new Date()) =>
  missingItems(state, today).length === 0 && !state.emailTaken;
