import ServiceIntro from './components/service-intro.jsx';
import Icon from '../../components/common/icon.jsx';
import ClapHero from './components/clap-hero.jsx';

// 홈 화면: 서비스 소개 → CLAP 설명 → 이용 순서 → 하단 안내 타일
export default function Home({ navigate }) {
  return (
    <>
      <ServiceIntro />
      <ClapHero navigate={navigate} />
      {/* 이용 순서 3단계 */}
      <section className="journey">
        <div>
          <p className="eyebrow">HOW IT WORKS</p>
          <h2>
            찾는 데 쓰던 시간,
            <br />
            무대를 만드는 시간으로.
          </h2>
        </div>
        {/* [번호, 제목, 설명] */}
        {[
          [
            '01',
            '행사의 조건을 알려주세요',
            '일정, 지역, 예산과 원하는 음악 스타일을 입력해요.',
          ],
          [
            '02',
            '추천의 이유를 비교하세요',
            '필수 조건과 항목별 점수로 적합한 후보를 살펴봐요.',
          ],
          [
            '03',
            '다음 무대를 준비하세요',
            '관심 아티스트를 저장하고 모집·교류 게시판을 이용해요.',
          ],
        ].map(([n, t, d]) => (
          <article key={n}>
            <span className="step-number">{n}</span>
            <h3>{t}</h3>
            <p>{d}</p>
          </article>
        ))}
      </section>
      {/* 하단 안내: 검증 정보 · 게시판 */}
      <section className="home-bottom">
        <div className="info-tile">
          <Icon
            name="shield"
            size={28}
          />
          <h2>잘 맞는 것과 믿을 수 있는 것.</h2>
          <p>
            음악 적합도와 검증 정보는 따로 보여드립니다.
            <br />
            사업자 여부로 음악 점수가 올라가지 않아요.
          </p>
          <button
            className="text-button"
            onClick={() => navigate('signup-role')}
          >
            가입·검증 살펴보기{' '}
            <Icon
              name="arrow"
              size={17}
            />
          </button>
        </div>
        <div className="info-tile">
          <Icon
            name="board"
            size={28}
          />
          <h2>무대 밖에서도 연결되세요.</h2>
          <p>
            함께할 팀원을 찾거나 공연 경험을 나누세요.
            <br />
            자유로운 모집과 이야기가 시작됩니다.
          </p>
          <button
            className="text-button"
            onClick={() => navigate('board')}
          >
            자유게시판 둘러보기{' '}
            <Icon
              name="arrow"
              size={17}
            />
          </button>
        </div>
      </section>
    </>
  );
}

