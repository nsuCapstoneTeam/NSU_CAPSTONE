import Icon from '../../../components/common/icon.jsx';
import './sign-up-pages.css';

// 로그인이 필요한 화면에 비로그인 상태로 들어왔을 때 보여 주는 안내.
// loginPage·title·description으로 화면마다 이동할 로그인 화면과 문구를 바꿀 수 있습니다.
export default function AuthRequired({
  navigate,
  loginPage = 'artist-login',
  title = '로그인이 필요한 화면이에요.',
  description = '마이페이지에서 프로필과 일정, 섭외 정보를 관리하려면 먼저 로그인해 주세요.',
}) {
  return (
    <section className="auth-required" aria-labelledby="auth-required-title">
      <Icon name="user" size={38} />
      <p className="eyebrow">LOGIN REQUIRED</p>
      <h1 id="auth-required-title">{title}</h1>
      <p>{description}</p>
      <div>
        <button className="primary" onClick={() => navigate(loginPage)}>로그인</button>
        <button className="secondary" onClick={() => navigate('signup-role')}>가입할래!</button>
      </div>
    </section>
  );
}
