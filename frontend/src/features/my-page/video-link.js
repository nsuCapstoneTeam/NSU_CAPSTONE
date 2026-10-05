// 공연 영상 링크(YouTube·Vimeo) 해석 도우미 (ART-027 외부 Portfolio)
// 영상 파일은 저장하지 않고, 주소에서 플랫폼과 영상 번호만 뽑아 저장합니다.
// (실행 테스트: node --test src/features/my-page/video-link.test.js)

const YOUTUBE_ID = /^[\w-]{11}$/;
const VIMEO_ID = /^\d{6,12}$/;
// 일부 공개(목록에 없는) Vimeo 영상의 확인 값 (vimeo.com/번호/해시 또는 ?h=해시)
const VIMEO_HASH = /^[0-9a-f]{6,20}$/;

export const PLATFORM_LABEL = { youtube: 'YouTube', vimeo: 'Vimeo' };

// 주소 → { platform, videoId } (Vimeo 일부 공개 영상은 hash 포함, 잘못된 주소면 null)
export function parseVideoUrl(input) {
  let url;
  try {
    url = new URL(input.trim());
  } catch {
    return null;
  }
  if (!['http:', 'https:'].includes(url.protocol)) return null;
  const host = url.hostname.replace(/^(www\.|m\.)/, '');
  const parts = url.pathname.split('/').filter(Boolean);

  let platform = null;
  let videoId = null;
  let hash = null;
  if (host === 'youtu.be') {
    platform = 'youtube';
    videoId = parts[0];
  } else if (host === 'youtube.com' || host === 'youtube-nocookie.com') {
    platform = 'youtube';
    videoId = ['shorts', 'embed', 'live'].includes(parts[0])
      ? parts[1]
      : url.searchParams.get('v');
  } else if (host === 'vimeo.com') {
    platform = 'vimeo';
    videoId = parts[0];
    hash = parts[1] || url.searchParams.get('h');
  } else if (host === 'player.vimeo.com' && parts[0] === 'video') {
    platform = 'vimeo';
    videoId = parts[1];
    hash = url.searchParams.get('h');
  }

  if (platform === 'youtube' && YOUTUBE_ID.test(videoId || '')) return { platform, videoId };
  if (platform === 'vimeo' && VIMEO_ID.test(videoId || '')) {
    return VIMEO_HASH.test(hash || '') ? { platform, videoId, hash } : { platform, videoId };
  }
  return null;
}

// 저장된 영상 → 화면 안 재생 주소 (형식이 틀린 값이면 null)
// YouTube는 쿠키를 덜 쓰는 youtube-nocookie 주소를 사용합니다.
// autoplay: true면 재생 버튼을 누른 뒤 바로 재생되도록 주소에 추가
export function embedUrl(video, { autoplay = false } = {}) {
  let url;
  if (video?.platform === 'youtube' && YOUTUBE_ID.test(video.videoId)) {
    url = new URL(`https://www.youtube-nocookie.com/embed/${video.videoId}`);
  } else if (video?.platform === 'vimeo' && VIMEO_ID.test(video.videoId)) {
    url = new URL(`https://player.vimeo.com/video/${video.videoId}`);
    if (VIMEO_HASH.test(video.hash || '')) url.searchParams.set('h', video.hash);
  } else {
    return null;
  }
  if (autoplay) url.searchParams.set('autoplay', '1');
  return url.toString();
}
