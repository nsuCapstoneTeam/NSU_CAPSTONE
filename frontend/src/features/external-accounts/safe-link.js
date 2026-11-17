// 외부 플랫폼 링크·썸네일 주소를 화면에 쓰기 전에 거르는 규칙 (외부 링크 ADR 원칙 4, API 06 §6.4 불변식 7)
// 서버가 이미 거르지만, 화면은 서버 응답도 믿지 않는 입력으로 보고 한 번 더 거릅니다.

const LINK_HOSTS = {
  YOUTUBE: new Set(['www.youtube.com']),
  SPOTIFY: new Set(['open.spotify.com']),
};
const THUMBNAIL_HOSTS = new Set(['i.ytimg.com', 'yt3.ggpht.com', 'i.scdn.co']);
const AUTHORIZATION_HOSTS = new Set(['accounts.google.com']);

// https이고 호스트가 허용 목록에 있으면 원래 문자열을, 아니면 null
function httpsUrlOnHosts(url, hosts) {
  if (typeof url !== 'string' || !hosts) return null;
  let parsed;
  try {
    parsed = new URL(url);
  } catch {
    return null;
  }
  // 호스트는 주소를 읽은 결과(hostname)로 비교하므로 "www.youtube.com@evil"처럼 흉내 낸 주소도 걸러진다
  if (parsed.protocol !== 'https:') return null;
  return hosts.has(parsed.hostname) ? url : null;
}

// 계정·영상 페이지 주소: 그 플랫폼의 허용 호스트일 때만
export function safeLink(provider, url) {
  return httpsUrlOnHosts(url, Object.hasOwn(LINK_HOSTS, provider) ? LINK_HOSTS[provider] : null);
}

// 이미지 주소: 플랫폼 이미지 호스트일 때만
export function safeThumbnail(url) {
  return httpsUrlOnHosts(url, THUMBNAIL_HOSTS);
}

// 연결 시작 응답의 동의 화면 주소: Google 계정 호스트일 때만 이동한다
export function safeAuthorizationUrl(url) {
  return httpsUrlOnHosts(url, AUTHORIZATION_HOSTS);
}
