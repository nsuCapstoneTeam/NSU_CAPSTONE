import useStoredState from '../../hooks/use-stored-state.js';
import { SESSION_KEY, authSession, isSession } from './auth-session.js';

// 로그인 세션 훅 (NSU-94): App이 로그인 상태를 읽고 바꿀 때 씁니다.
// 반환값 { session, login, logout, startDemo, storageError }
//   session: 지금 로그인한 사람 {name, role, userId} 또는 null (서버 모드에서 토큰이 만료되면 null)
//   login(email, password, {roleHint, profile}): auth-session.login 결과 { session, roleChanged }
//   logout(): 토큰과 세션을 함께 지움
//   startDemo({name, role}): 토큰 없는 체험 세션 (체험 모드에서만. 서버 모드면 false)
//   cancelPendingLogin(): 로그인 화면을 벗어날 때. 늦게 온 로그인 응답을 버림
export default function useAuthSession() {
  const [stored, setStored, storageError] = useStoredState(SESSION_KEY, null, isSession);
  const session = authSession.restore(stored);

  async function login(email, password, options) {
    const result = await authSession.login(email, password, options);
    setStored(result.session);
    return result;
  }

  function logout() {
    authSession.logout();
    setStored(null);
  }

  function startDemo(next) {
    if (!authSession.canStartWithoutLogin) return false;
    authSession.logout();
    setStored({ name: next.name, role: next.role });
    return true;
  }

  return { session, login, logout, startDemo, cancelPendingLogin: authSession.cancelPendingLogin, storageError };
}
