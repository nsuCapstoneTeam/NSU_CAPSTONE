import Icon from '../../../components/common/icon.jsx';

// 소개 구간의 두 보조 기능. ready=false면 '준비 중' 표시 (DESIGN.md 1장: 연결 전 기능은 솔직하게 표시)
const SIDE_FEATURES = [
  {
    icon: 'user',
    title: '1:1 대화로 바로 연결',
    body: '마음에 드는 아티스트를 찾으면 중간 과정 없이 직접 일정과 공연 조건을 이야기해요.',
    ready: false,
  },
  {
    icon: 'clipboard',
    title: '서로 필요한 정보를 한눈에',
    body: '행사 관계자는 아티스트의 곡과 공연 가능 일정을, 아티스트는 행사 정보와 원하는 분위기를 미리 확인해요.',
    ready: true,
  },
];

// 둘째 구간: 서비스가 하는 일. 핵심 기능(AI 음악 매칭)을 크게, 나머지 둘은 옆에 작게 둡니다.
export default function TrackIntro({ sectionProps }) {
  return (
    <section
      {...sectionProps}
      className="stage-track stage-track-intro"
      aria-labelledby="home-track-intro-title"
    >
      <h2
        id="home-track-intro-title"
        className="stage-track-title"
      >
        CLAP SOUND에서 할 수 있는 일
      </h2>
      <div className="stage-intro-grid">
        <article className="stage-panel stage-feature is-main">
          <span className="stage-feature-icon">
            <Icon
              name="music"
              size={22}
            />
          </span>
          <h3>AI 음악 매칭</h3>
          <p>
            등록한 행사 설명을 AI가 해석하고, 음악과 글을 함께 이해하는 <b>CLAP</b>이 곡과의 음악 유사도를
            계산해요. 가장 가까운 곡 10곡부터 템포·리듬 정보와 추천 이유를 함께 보여 줘요.
          </p>
          <ul
            className="stage-tags"
            aria-label="추천 결과에 보이는 정보"
          >
            <li className="is-strong">음악 유사도로 순위</li>
            <li>템포 참고</li>
            <li>리듬 참고</li>
          </ul>
        </article>
        <div className="stage-intro-side">
          {SIDE_FEATURES.map((feature) => (
            <article
              key={feature.title}
              className="stage-panel stage-feature"
            >
              <span className="stage-feature-icon">
                <Icon
                  name={feature.icon}
                  size={20}
                />
              </span>
              <h3>
                {feature.title}
                {!feature.ready && <span className="stage-tag">준비 중</span>}
              </h3>
              <p>{feature.body}</p>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}
