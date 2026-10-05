import { useEffect, useRef, useState } from 'react';
import Icon from '../../../components/common/icon.jsx';
import VideoLinks from './video-links.jsx';

// 마이페이지 '작업물·섭외' 탭 (아티스트): 공연 영상 링크, 작업물 미리 듣기, 섭외 요청 우편함
export default function WorksTab({ active, profile, setProfile, notify }) {
  // 미리 듣기용으로 선택한 오디오 파일 (서버에 올리지 않음)
  const [audioFile, setAudioFile] = useState(null);
  const audioRef = useRef(null);
  // 선택한 파일을 브라우저 임시 주소로 만들어 재생기에 연결하고, 바뀌거나 나가면 해제
  useEffect(() => {
    if (!audioFile) return;
    const url = URL.createObjectURL(audioFile);
    if (audioRef.current) audioRef.current.src = url;
    return () => URL.revokeObjectURL(url);
  }, [audioFile]);
  // 다른 탭으로 가면 미리 듣기를 일시 정지 (선택한 파일은 유지)
  useEffect(() => {
    if (!active) audioRef.current?.pause();
  }, [active]);
  return (
    <div className="works-stack">
      <VideoLinks
        active={active}
        profile={profile}
        setProfile={setProfile}
        notify={notify}
      />
      <div className="my-grid">
        {/* 아티스트: 내 음원 미리 듣기 (20MB 이하만 허용) */}
        <section className="panel">
          <h2>작업물 미리 듣기</h2>
          <p>
            내 오디오를 선택하여 재생할 수 있습니다. 서버 업로드·분석·파일
            보관은 아직 연결 전입니다.
          </p>
          <label className="upload-box">
            <Icon
              name="music"
              size={26}
            />
            <span>음악 파일 선택 · 최대 20MB</span>
            <input
              type="file"
              accept="audio/*,.mp3,.wav,.m4a,.ogg"
              onChange={(e) => {
                const f = e.target.files[0];
                if (!f) return;
                if (f.size > 20 * 1024 * 1024) {
                  notify('20MB 이하의 파일을 선택해 주세요.');
                  e.target.value = '';
                  return;
                }
                setAudioFile(f);
              }}
            />
          </label>
          {audioFile && (
            <>
              <p className="file-name">{audioFile.name}</p>
              <audio
                ref={audioRef}
                controls
                onError={() =>
                  notify('브라우저에서 재생할 수 없는 오디오 형식입니다.')
                }
              />
              <small>이 화면을 나가면 선택한 파일은 해제됩니다.</small>
            </>
          )}
        </section>
        {/* 섭외 요청 우편함 (아직 체험 데이터 없음) */}
        <section className="panel">
          <h2>섭외 요청 우편함</h2>
          <p>새로운 섭외 요청이 도착하면 행사명, 일정, 지역과 요청 상태를 이곳에서 확인합니다.</p>
          <div className="soft-box">아직 도착한 체험 섭외 요청이 없습니다.</div>
        </section>
      </div>
    </div>
  );
}
