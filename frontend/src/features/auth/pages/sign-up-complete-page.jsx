import Icon from '../../../components/common/icon.jsx';
import { AUTH_API_MODE } from '../api/auth-api.js';
import './sign-up-pages.css';

// 역할별 '가입 후 할 일' (가입은 계정만 만들고, 프로필은 마이페이지에서 채움 — AUTH-160)
const NEXT_STEPS = {
  artist: ['프로필 탭에서 활동명·장르·활동 지역 채우기', '공연 일정 탭에서 공연 가능한 날짜 등록하기', '작업물·섭외 탭에서 공연 영상 링크 올리기'],
  organizer: ['프로필 탭에서 담당자 이름·소속 채우기', '아티스트 매칭에서 행사 조건 입력해 보기'],
};

// 가입 완료 안내. onStart를 누르면 로그인 상태가 되어 마이페이지(프로필 탭)로 이동합니다.
export default function SignUpComplete({ profile, onStart, navigate }) {
  const isArtist = profile.role === 'artist';
  const roleName = isArtist ? '아티스트' : '행사 관계자';
  const steps = NEXT_STEPS[isArtist ? 'artist' : 'organizer'];
  return (
    <section className="signup-complete" aria-labelledby="signup-complete-title">
      <div className="signup-complete-icon" aria-hidden="true">
        <Icon name="check" size={34} />
      </div>
      <p className="eyebrow">WELCOME TO 할래말래</p>
      <h1 id="signup-complete-title">가입이 완료됐어요!</h1>
      <p>
        {roleName} 계정을 만들었어요.
        <br />
        마이페이지에서 프로필을 채우면 준비 끝이에요.
      </p>

      {/* 다음에 할 일 */}
      <ol className="signup-complete-steps">
        {steps.map((step) => (
          <li key={step}>{step}</li>
        ))}
      </ol>

      <div className="signup-complete-actions">
        <button className="primary" onClick={onStart}>
          프로필 채우러 가기
        </button>
        <button
          className="secondary"
          onClick={() => navigate(isArtist ? 'artist-login' : 'organizer-login')}
        >
          로그인 화면으로
        </button>
      </div>
      <small>
        {AUTH_API_MODE === 'demo'
          ? '체험 모드라 실제 회원 계정은 만들어지지 않았어요.'
          : '로그인 기능이 연결되기 전까지는 이 브라우저의 체험 상태로 이어져요.'}
      </small>
    </section>
  );
}
