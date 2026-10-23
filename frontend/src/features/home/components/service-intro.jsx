import connectionImage from '../../../assets/artist-organizer-v1.png';
import './service-intro.css';

// 홈페이지: 서비스 소개 문구와 아티스트·행사 관계자 연결 일러스트
export default function ServiceIntro() {
  return (
    <section
      className="service-intro"
      aria-labelledby="service-intro-title"
    >
      {/* 왼쪽: 소개 문구 */}
      <div className="service-intro-copy">
        <p className="eyebrow">무엇을 하는 서비스인가요?</p>
        <h2 id="service-intro-title">
          CLAP SOUND에서
          <br />
          <span>무명 아티스트</span>와 <span>행사 관계자</span>가
          <br />
          쉽게 연락해요
        </h2>
        <p className="service-intro-challenge">아티스트는 무대에 설 기회를 넓히고</p>
        <p className="service-intro-challenge">행사 관계자는 행사에 맞는 음악을 찾아요</p>
      </div>
      {/* 오른쪽: 아티스트·행사 관계자 연결 일러스트 (첫 화면 이미지라 우선 로드) */}
      <img
        className="service-intro-image"
        src={connectionImage}
        alt="공연 무대 앞에서 마이크를 든 아티스트와 체크리스트를 든 행사 관계자가 주먹을 맞대며 함께 도전하는 모습"
        width="1536"
        height="1024"
        fetchPriority="high"
      />
    </section>
  );
}
