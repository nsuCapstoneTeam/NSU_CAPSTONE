import Icon from '../../../components/common/icon.jsx';

// 마지막 구간: 역할을 골라 가입으로 이어 줍니다. 아티스트는 파랑, 행사 관계자는 코랄 (DESIGN.md 2장 역할 색)
export default function TrackStart({ sectionProps, navigate }) {
  return (
    <section
      {...sectionProps}
      className="stage-track stage-track-start"
      aria-labelledby="home-track-start-title"
    >
      <div className="stage-panel stage-start">
        <h2
          id="home-track-start-title"
          className="stage-start-title"
        >
          어떤 무대를
          <br />
          꿈꾸고 있나요?
        </h2>
        <div className="stage-roles">
          <article className="stage-role is-artist">
            <span className="stage-role-icon">
              <Icon
                name="mic"
                size={20}
              />
            </span>
            <h3>아티스트</h3>
            <p>
              나의 음악을 소개하고
              <br />
              함께할 새로운 무대를 찾아요.
            </p>
            <button
              className="stage-button is-artist"
              onClick={() => navigate('artist-signup')}
            >
              아티스트로 가입하기
            </button>
          </article>
          <article className="stage-role is-organizer">
            <span className="stage-role-icon">
              <Icon
                name="clipboard"
                size={20}
              />
            </span>
            <h3>행사 관계자</h3>
            <p>
              행사에 어울리는 아티스트를 만나고
              <br />
              기억에 남을 공연을 준비해요.
            </p>
            <button
              className="stage-button is-organizer"
              onClick={() => navigate('organizer-signup')}
            >
              행사 관계자로 가입하기
            </button>
          </article>
        </div>
        <p className="stage-start-login">
          이미 계정이 있나요?{' '}
          <button
            className="text-button"
            onClick={() => navigate('artist-login')}
          >
            로그인하기
          </button>
        </p>
      </div>
    </section>
  );
}
