import Icon from '../../../components/common/icon.jsx';
import './sign-up-pages.css';

export default function AuthRequired({ navigate }) {
  return (
    <section className="auth-required" aria-labelledby="auth-required-title">
      <Icon name="user" size={38} />
      <p className="eyebrow">LOGIN REQUIRED</p>
      <h1 id="auth-required-title">로그인이 필요한 화면이에요.</h1>
      <p>마이페이지에서 프로필과 일정, 섭외 정보를 관리하려면 먼저 로그인해 주세요.</p>
      <div>
        <button className="primary" onClick={() => navigate('artist-login')}>로그인</button>
        <button className="secondary" onClick={() => navigate('signup-role')}>가입할래!</button>
      </div>
    </section>
  );
}
