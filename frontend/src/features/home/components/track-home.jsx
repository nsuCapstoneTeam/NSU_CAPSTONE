import Icon from '../../../components/common/icon.jsx';

// 홈 첫 구간: 서비스를 한 문장으로 소개하고, 매칭 체험과 소개 보기로 이어 줍니다.
export default function TrackHome({ sectionProps, navigate, onShowIntro }) {
  return (
    <section
      {...sectionProps}
      className="stage-track stage-track-home"
      aria-labelledby="home-track-home-title"
    >
      <div className="stage-panel stage-hero">
        <p className="eyebrow">YOUR NEXT STAGE</p>
        <h1 id="home-track-home-title">
          당신의 음악이
          <br />
          필요한 무대를
          <br />
          만나세요.
        </h1>
        <p className="stage-hero-lead">
          음악을 들려줄 아티스트와
          <br />
          그 음악이 필요한 행사 관계자가 만나는 곳.
        </p>
        <p className="stage-chip">행사 설명과 가까운 곡을 CLAP 음악 유사도로 추천해요</p>
        <div className="stage-actions">
          <button
            className="stage-button is-primary"
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
