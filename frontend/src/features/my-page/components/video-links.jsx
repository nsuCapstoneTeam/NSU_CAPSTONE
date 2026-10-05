import { useState } from 'react';
import { PLATFORM_LABEL, embedUrl, parseVideoUrl } from '../video-link.js';
import './video-links.css';

// 공연 영상 링크 (ART-027 외부 Portfolio)
// 영상 파일은 올리지 않고 YouTube·Vimeo 주소만 프로필에 저장합니다.
// 외부 사이트 재생기는 '재생'을 누른 영상만 불러옵니다. (실제 영상 업로드는 팀 확정 필요)

const MAX_VIDEOS = 10;

// active가 false(다른 탭)면 재생기를 내려서 영상 재생을 멈춥니다.
export default function VideoLinks({ active, profile, setProfile, notify }) {
  const videos = Array.isArray(profile.videos) ? profile.videos : [];
  const [title, setTitle] = useState('');
  const [url, setUrl] = useState('');
  const [error, setError] = useState('');
  // 재생기를 연 영상 id
  const [playingId, setPlayingId] = useState(null);

  function saveVideos(next, message) {
    const saved = setProfile({ ...profile, videos: next });
    notify(saved ? message : '화면에는 반영됐지만 브라우저 저장에 실패했어요.');
  }

  function add(e) {
    e.preventDefault();
    const parsed = parseVideoUrl(url);
    if (!parsed) {
      setError('YouTube 또는 Vimeo 영상 주소를 넣어 주세요. (예: https://youtu.be/…)');
      return;
    }
    if (videos.some((v) => v.platform === parsed.platform && v.videoId === parsed.videoId)) {
      setError('이미 등록한 영상이에요.');
      return;
    }
    if (videos.length >= MAX_VIDEOS) {
      setError(`영상은 ${MAX_VIDEOS}개까지 등록할 수 있어요.`);
      return;
    }
    setError('');
    saveVideos(
      [{ id: crypto.randomUUID(), title: title.trim(), ...parsed }, ...videos],
      '공연 영상을 등록했어요.',
    );
    setTitle('');
    setUrl('');
  }

  function remove(video) {
    if (playingId === video.id) setPlayingId(null);
    saveVideos(
      videos.filter((v) => v.id !== video.id),
      `'${video.title}' 영상을 목록에서 뺐어요.`,
    );
  }

  return (
    <section className="panel video-links">
      <h2>
        공연 영상 <em>{videos.length}</em>
      </h2>
      <p>YouTube·Vimeo에 올린 공연 영상 주소를 등록하면 행사 관계자에게 보여 줄 수 있어요.</p>

      <form
        className="stacked-form"
        onSubmit={add}
        noValidate
      >
        <div className="form-pair">
          <label>
            영상 제목
            <input
              required
              maxLength={60}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="예: 2026 대학 축제 라이브"
            />
          </label>
          <label>
            영상 주소
            <input
              type="url"
              required
              value={url}
              onChange={(e) => {
                setUrl(e.target.value);
                setError('');
              }}
              placeholder="https://youtu.be/…"
              aria-invalid={Boolean(error)}
              aria-describedby={error ? 'video-url-error' : undefined}
            />
          </label>
        </div>
        {error && (
          <p
            id="video-url-error"
            className="video-error error"
            role="alert"
          >
            {error}
          </p>
        )}
        <button
          className="secondary"
          type="submit"
          disabled={!title.trim() || !url.trim()}
        >
          영상 등록
        </button>
      </form>

      <ul className="video-list">
        {videos.map((v) => {
          const src = embedUrl(v);
          if (!src) return null;
          return (
            <li key={v.id}>
              <div className="video-frame">
                {active && playingId === v.id ? (
                  <iframe
                    src={embedUrl(v, { autoplay: true })}
                    title={`${v.title} (${PLATFORM_LABEL[v.platform]})`}
                    allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
                    allowFullScreen
                    referrerPolicy="strict-origin-when-cross-origin"
                  />
                ) : (
                  <button
                    type="button"
                    className="video-play"
                    onClick={() => setPlayingId(v.id)}
                    aria-label={`${v.title} 재생 (${PLATFORM_LABEL[v.platform]} 재생기를 불러와요)`}
                  >
                    <span aria-hidden="true">▶</span>
                    재생
                  </button>
                )}
              </div>
              <div className="video-meta">
                <div>
                  <strong>{v.title}</strong>
                  <p>{PLATFORM_LABEL[v.platform]}</p>
                </div>
                <button
                  type="button"
                  className="text-button danger"
                  onClick={() => remove(v)}
                  aria-label={`${v.title} 영상 삭제`}
                >
                  삭제
                </button>
              </div>
            </li>
          );
        })}
      </ul>
      {!videos.length && <p className="muted">아직 등록한 공연 영상이 없어요.</p>}
    </section>
  );
}
