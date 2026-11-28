import Icon from '../../../components/common/icon.jsx';

// 홈 첫 구간 (NSU-179, Figma '홈 · 디스플레이 제목 시안 → B · 3D 유리 CD')
// 카드 없이 큰 제목이 첫 화면의 주인공이고, 뒤쪽 유리 CD(glass-cd.jsx)와 살짝 겹칩니다.
// 한글 제목은 Gasoek One, CLAP SOUND는 Pacifico (DESIGN.md 3장 글꼴 예외)
export default function TrackHome({ sectionProps, navigate, onShowIntro }) {
  return (
    <section
      {...sectionProps}
      className="stage-track stage-track-home"
      aria-labelledby="home-track-home-title"
    >
      <div className="stage-hero">
        <h1
          id="home-track-home-title"
          className="stage-display"
        >
          <span className="stage-display-ko">
            당신의 <em>음악</em>이
            <br />
            필요한 순간,
          </span>
          <span
            className="stage-display-brand"
            lang="en"
          >
            CLAP SOUND
          </span>
        </h1>
        <p className="stage-hero-lead">
          음악을 들려줄 <b>아티스트</b>와
          <br />그 음악이 필요한 <b>행사 관계자</b>가 만나는 곳.
        </p>
        <div className="stage-actions">
          <button
            className="stage-button is-primary is-hero"
            onClick={() => navigate('match')}
          >
            아티스트 찾기
            <Icon
              name="arrow"
              size={16}
            />
          </button>
          <button
            className="stage-button"
            onClick={onShowIntro}
          >
            서비스 소개 보기
          </button>
        </div>
      </div>
    </section>
  );
}
