// 회원가입 API 창구 (백엔드 PR #99·#101·#103 기준)
// 화면은 이 파일의 함수만 부르고, 실제 서버와 데모 중 어디로 갈지는 여기서 정합니다.
//   - server: Vite 개발 서버가 /api 요청을 백엔드(localhost:8080)로 넘김 (vite.config.js)
//   - demo  : 서버 없이 같은 규칙으로 흉내 (기본값)
// frontend/.env.local 에 VITE_AUTH_API=server 를 적으면 서버 모드가 됩니다.
import { isAdult } from '../signup-rules.js';

// 역할 → API 주소 조각 (AUTH-005: 가입 화면·API를 역할별로 분리)
const ROLE_PATH = { artist: 'artist', 'event-partner': 'event-partner' };

// 백엔드에 보낼 필수 약관 ID·버전 (팀 확정 필요: 지금은 백엔드 설정값과 같은 값을 임시로 적어 둠)
export const REQUIRED_TERM_VERSIONS = [
  { id: 'service', version: 'v1' },
  { id: 'privacy', version: 'v1' },
];

// 백엔드 오류 코드(ErrorCode) → 화면 안내 문구
export const ERROR_MESSAGES = {
  VALIDATION_ERROR: '입력한 값을 다시 확인해 주세요.',
  EMAIL_ALREADY_EXISTS: '이미 가입된 이메일이에요. 처음 가입한 방법으로 로그인해 주세요.',
  SIGNUP_SESSION_INVALID: '가입 진행 시간(30분)이 지났어요. 처음부터 다시 진행해 주세요.',
  EMAIL_VERIFICATION_REQUIRED: '이메일 인증을 먼저 완료해 주세요.',
  PHONE_VERIFICATION_REQUIRED: '휴대폰 인증을 먼저 완료해 주세요.',
  REQUIRED_TERMS_AGREEMENT_REQUIRED: '필수 약관에 동의해 주세요.',
  ADULT_CONFIRMATION_REQUIRED: '생년월일로 만 18세 이상인지 확인해 주세요.',
  VERIFICATION_CODE_INVALID: '인증번호가 맞지 않아요. 다시 확인해 주세요.',
  VERIFICATION_CODE_EXPIRED: '인증번호 유효시간(5분)이 지났어요. 인증번호를 다시 받아 주세요.',
  VERIFICATION_REQUEST_LIMIT_EXCEEDED: '인증번호는 60초 뒤에 다시 받을 수 있어요.',
  VERIFICATION_ATTEMPT_LIMIT_EXCEEDED: '확인 횟수(5회)를 넘었어요. 인증번호를 다시 받아 주세요.',
  VERIFICATION_DELIVERY_FAILED: '인증번호를 보내지 못했어요. 잠시 후 다시 시도해 주세요.',
  TERMS_AGREEMENT_INVALID: '약관 정보가 맞지 않아요. 새로고침 후 다시 시도해 주세요.',
  REQUIRED_TERMS_NOT_AGREED: '모든 필수 약관에 동의해 주세요.',
  ADULT_CONFIRMATION_EVIDENCE_INVALID: '생년월일을 다시 확인해 주세요.',
  ADULT_REQUIREMENT_NOT_MET: '만 18세 이상만 가입할 수 있어요.',
  INTERNAL_SERVER_ERROR: '서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요.',
  NETWORK_ERROR: '서버에 연결할 수 없어요. 백엔드가 켜져 있는지 확인해 주세요.',
  OAUTH_NOT_READY: '간편가입은 아직 준비 중이에요. 이메일로 가입해 주세요.',
  UNKNOWN_ERROR: '알 수 없는 문제가 생겼어요. 잠시 후 다시 시도해 주세요.',
};

// API 실패를 나타내는 오류. code로 어떤 실패인지 구분합니다.
export class AuthApiError extends Error {
  constructor(code, status = 0) {
    super(ERROR_MESSAGES[code] || ERROR_MESSAGES.UNKNOWN_ERROR);
    this.name = 'AuthApiError';
    this.code = code;
    this.status = status;
  }
}

// 어떤 오류든 화면에 보여 줄 한국어 문구로 바꿈
export const messageFor = (error) =>
  error instanceof AuthApiError ? error.message : ERROR_MESSAGES.UNKNOWN_ERROR;

// 글자를 JSON으로 바꾸되, 비어 있거나 JSON이 아니면 null
function parseJson(text) {
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

// 실패 응답 → 오류 코드
// 백엔드가 보낸 code가 있으면 그대로 쓰고, 없으면 상태 번호로 추측
//   5xx + code 없음: 백엔드가 꺼져 있어 Vite proxy·게이트웨이가 대신 답한 경우가 대부분 → NETWORK_ERROR
//   그 밖에 code 없음: UNKNOWN_ERROR
export function errorCodeOf(data, status) {
  if (typeof data?.code === 'string' && data.code) return data.code;
  return status >= 500 ? 'NETWORK_ERROR' : 'UNKNOWN_ERROR';
}

// ── 서버 모드: fetch로 실제 백엔드 호출 ─────────────────────────
function createServerApi(fetchImpl) {
  // JSON 요청을 보내고, 실패하면 AuthApiError를 던짐
  async function request(path, body, method = 'POST') {
    let response;
    try {
      response = await fetchImpl(path, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
    } catch {
      // 서버가 꺼져 있거나 인터넷이 끊긴 경우
      throw new AuthApiError('NETWORK_ERROR');
    }
    // 202·204 응답은 본문이 비어 있으므로 글자로 먼저 읽고, JSON일 때만 바꿈
    // (Spring 기본 오류 화면·프록시 오류처럼 JSON이 아닌 응답이 와도 SyntaxError가 나지 않게)
    const data = parseJson(await response.text().catch(() => ''));
    if (!response.ok) throw new AuthApiError(errorCodeOf(data, response.status), response.status);
    return data;
  }

  const base = (role) => `/api/v1/auth/signup/${ROLE_PATH[role]}`;

  return {
    createSession: (role, { email, phone }) => request(`${base(role)}/session`, { email, phone }),
    sendEmailCode: (role, signupSessionId) =>
      request(`${base(role)}/session/email-verification/send`, { signupSessionId }),
    confirmEmailCode: (role, signupSessionId, code) =>
      request(`${base(role)}/session/email-verification/confirm`, { signupSessionId, code }),
    sendPhoneCode: (role, signupSessionId) =>
      request(`${base(role)}/session/phone-verification/send`, { signupSessionId }),
    confirmPhoneCode: (role, signupSessionId, code) =>
      request(`${base(role)}/session/phone-verification/confirm`, { signupSessionId, code }),
    agreeTerms: (role, signupSessionId) =>
      request(
        `${base(role)}/session/required-terms-agreement`,
        { signupSessionId, agreements: REQUIRED_TERM_VERSIONS },
        'PUT',
      ),
    confirmAdult: (role, signupSessionId, birthDate) =>
      request(`${base(role)}/session/adult-confirmation`, { signupSessionId, birthDate }),
    signUp: (role, signupSessionId, password) => request(base(role), { signupSessionId, password }),
    // 간편가입 API(NSU-75)는 아직 없음
    startOAuth: async () => {
      throw new AuthApiError('OAUTH_NOT_READY');
    },
  };
}

// ── 데모 모드: 서버 없이 백엔드와 같은 규칙으로 흉내 ──────────────
// taken@example.com 은 이미 가입된 이메일로 처리합니다. 인증번호는 숫자 6자리면 통과합니다.
export const DEMO_TAKEN_EMAIL = 'taken@example.com';

function createDemoApi(wait) {
  const sessions = new Map();

  // 세션을 찾고, 없으면 백엔드처럼 SIGNUP_SESSION_INVALID
  async function session(id) {
    await wait();
    const found = sessions.get(id);
    if (!found) throw new AuthApiError('SIGNUP_SESSION_INVALID', 400);
    return found;
  }
  const checkCode = (code) => {
    if (!/^\d{6}$/.test(code || '')) throw new AuthApiError('VERIFICATION_CODE_INVALID', 400);
  };

  return {
    async createSession(role, { email, phone, emailVerified = false }) {
      await wait();
      const id = `demo-${sessions.size + 1}`;
      sessions.set(id, { role, email, phone, emailVerified, phoneVerified: false });
      return { signupSessionId: id };
    },
    sendEmailCode: async (role, id) => void (await session(id)),
    async confirmEmailCode(role, id, code) {
      const s = await session(id);
      checkCode(code);
      s.emailVerified = true;
      return null;
    },
    sendPhoneCode: async (role, id) => void (await session(id)),
    async confirmPhoneCode(role, id, code) {
      const s = await session(id);
      checkCode(code);
      s.phoneVerified = true;
      return null;
    },
    async agreeTerms(role, id) {
      (await session(id)).termsAgreed = true;
      return null;
    },
    async confirmAdult(role, id, birthDate) {
      const s = await session(id);
      if (!isAdult(birthDate)) throw new AuthApiError('ADULT_REQUIREMENT_NOT_MET', 400);
      s.adultConfirmed = true;
      return null;
    },
    // 백엔드 signup과 같은 순서로 조건 확인 → 중복 이메일 → 완료
    async signUp(role, id) {
      const s = await session(id);
      if (!s.emailVerified) throw new AuthApiError('EMAIL_VERIFICATION_REQUIRED', 400);
      if (!s.phoneVerified) throw new AuthApiError('PHONE_VERIFICATION_REQUIRED', 400);
      if (!s.termsAgreed) throw new AuthApiError('REQUIRED_TERMS_AGREEMENT_REQUIRED', 400);
      if (!s.adultConfirmed) throw new AuthApiError('ADULT_CONFIRMATION_REQUIRED', 400);
      if (s.email.trim().toLowerCase() === DEMO_TAKEN_EMAIL) {
        throw new AuthApiError('EMAIL_ALREADY_EXISTS', 409);
      }
      sessions.delete(id);
      return {
        userId: `demo-user-${Date.now()}`,
        email: s.email,
        role: role === 'artist' ? 'ARTIST' : 'EVENT_PARTNER',
        status: 'ACTIVE',
      };
    },
    // 간편가입 흉내: Google·Naver는 인증된 이메일, Kakao는 이메일 없음
    async startOAuth(provider) {
      await wait();
      if (provider === 'kakao') return { provider, email: '', emailVerified: false };
      return { provider, email: `demo.${provider}@example.com`, emailVerified: true };
    },
  };
}

// mode·fetch·대기 시간을 바꿔 끼울 수 있게 만든 공장 함수 (테스트에서 사용)
export function createAuthApi({ mode = 'demo', fetchImpl, delayMs = 400 } = {}) {
  if (mode === 'server') return createServerApi(fetchImpl || ((...args) => fetch(...args)));
  const wait = () => new Promise((resolve) => setTimeout(resolve, delayMs));
  return createDemoApi(wait);
}

// 화면에서 쓰는 기본 창구 (.env의 VITE_AUTH_API 값으로 모드 결정)
export const AUTH_API_MODE = import.meta.env?.VITE_AUTH_API === 'server' ? 'server' : 'demo';
export const authApi = createAuthApi({ mode: AUTH_API_MODE });
