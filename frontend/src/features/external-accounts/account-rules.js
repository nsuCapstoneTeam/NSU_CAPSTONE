// 외부 계정 화면의 판단 규칙 (API 계약 docs/api/06_external_accounts.md)

export const MAX_WORKS = 10;

const PROVIDER_NAMES = { YOUTUBE: 'YouTube 채널', SPOTIFY: 'Spotify 아티스트' };

// 그 플랫폼에 이미 연결된 계정. 있으면 "기존 연결을 바꿀까요?"를 먼저 묻고 이 계정의 ID를 교체 승인으로 보낸다 (§3)
export function accountToReplace(items, provider) {
  return items?.find((item) => item.provider === provider) ?? null;
}

// 작업물 고르기: 누른 영상을 넣거나 뺀다. 고른 순서가 표시 순서이고 최대 MAX_WORKS개다 (§8)
export function toggleWork(selectedIds, workId) {
  if (selectedIds.includes(workId)) return selectedIds.filter((id) => id !== workId);
  return selectedIds.length >= MAX_WORKS ? selectedIds : [...selectedIds, workId];
}

// 확인 상태 문구 (§6.2): "인증", "소유권 인증 완료"라고 쓰지 않는다
export function verificationText(account) {
  return account.isControlVerified ? '채널 제어권 확인' : 'Spotify 아티스트 프로필 연결됨';
}

// 이름을 받지 못해도(null) 링크는 남으므로 플랫폼 이름으로 대신한다 (§6.4 불변식 5)
export function displayNameOf(account) {
  return account.displayName ?? PROVIDER_NAMES[account.provider] ?? '외부 계정';
}

// 오류가 난 뒤에 할 일: 'reload'(목록을 다시 읽음) | 'none'(안내만)
// afterConnectRequest: 연결 요청(POST)을 보낸 뒤의 오류. 같은 state는 다시 못 보내므로 목록으로 결과를 확인한다 (§5.4)
export function followUpFor(code, { afterConnectRequest = false } = {}) {
  if (afterConnectRequest && (code === 'NETWORK_ERROR' || code === 'EXTERNAL_PROVIDER_UNAVAILABLE')) {
    return 'reload';
  }
  const RELOAD = [
    'EXTERNAL_ACCOUNT_CONNECTION_CHANGED',
    'EXTERNAL_ACCOUNT_NOT_FOUND',
    'EXTERNAL_ACCOUNT_WORK_NOT_CANDIDATE',
  ];
  return RELOAD.includes(code) ? 'reload' : 'none';
}
