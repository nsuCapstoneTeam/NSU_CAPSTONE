import Icon from '../../../components/common/icon.jsx';
import './sign-up-pages.css';

// 가입 완료 안내. onStart를 누르면 로그인 상태로 체험을 시작합니다.
export default function SignUpComplete({ profile, onStart, navigate }) {
  const roleName = profile.role === 'artist' ? '아티스트' : '행사 관계자';
  return (
    <section className="signup-complete" aria-labelledby="signup-complete-title">
      <div className="signup-complete-icon" aria-hidden="true">
        <Icon name="check" size={34} />
      </div>
      <p className="eyebrow">WELCOME TO 할래말래</p>
      <h1 id="signup-complete-title">가입이 완료됐어요!</h1>
      <p>
        <strong>{profile.name}</strong>님의 {roleName} 체험 프로필을 만들었습니다.
        <br />이제 역할에 맞는 마이페이지를 확인할 수 있어요.
      </p>
      <div className="signup-complete-actions">
        <button className="primary" onClick={onStart}>바로 체험 시작</button>
        <button className="secondary" onClick={() => navigate('artist-login')}>로그인 화면으로</button>
      </div>
      <small>현재는 프런트엔드 체험판이며 실제 회원 계정은 생성되지 않습니다.</small>
    </section>
  );
}
