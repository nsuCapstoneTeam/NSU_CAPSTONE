// 공연 영상 링크(YouTube·Vimeo) 해석 도우미 (ART-027 외부 Portfolio)
// 영상 파일은 저장하지 않고, 주소에서 플랫폼과 영상 번호만 뽑아 저장합니다.
// (실행 테스트: node --test src/features/my-page/video-link.test.js)

const YOUTUBE_ID = /^[\w-]{11}$/;
const VIMEO_ID = /^\d{6,12}$/;

export const PLATFORM_LABEL = { youtube: 'YouTube', vimeo: 'Vimeo' };

// 주소 → { platform, videoId } (지원하지 않거나 잘못된 주소면 null)
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
  } else if (host === 'player.vimeo.com' && parts[0] === 'video') {
    platform = 'vimeo';
    videoId = parts[1];
  }

  if (platform === 'youtube' && YOUTUBE_ID.test(videoId || '')) return { platform, videoId };
  if (platform === 'vimeo' && VIMEO_ID.test(videoId || '')) return { platform, videoId };
  return null;
}

// 저장된 영상 → 화면 안 재생 주소 (형식이 틀린 값이면 null)
// YouTube는 쿠키를 덜 쓰는 youtube-nocookie 주소를 사용합니다.
export function embedUrl(video) {
  if (video?.platform === 'youtube' && YOUTUBE_ID.test(video.videoId))
    return `https://www.youtube-nocookie.com/embed/${video.videoId}`;
  if (video?.platform === 'vimeo' && VIMEO_ID.test(video.videoId))
    return `https://player.vimeo.com/video/${video.videoId}`;
  return null;
}
