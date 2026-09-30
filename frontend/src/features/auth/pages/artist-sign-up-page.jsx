import { useState } from 'react';
import './sign-up-pages.css';

export default function ArtistSignUp({ profile, setProfile, notify, navigate }) {
  const [form, setForm] = useState({
    loginId: '',
    password: '',
    passwordConfirm: '',
    realName: '',
    phone: '',
    email: '',
    stageName: '',
    genre: '',
    detailGenre: '',
    region: '',
    age: false,
    terms: false,
    privacy: false,
  });

  function update(name, value) {
    setForm((current) => ({ ...current, [name]: value }));
  }

  function submit(event) {
    event.preventDefault();
    if (form.password.length < 8) {
      notify('비밀번호는 8자 이상으로 입력해 주세요.');
      return;
    }
    if (form.password !== form.passwordConfirm) {
      notify('비밀번호가 서로 일치하지 않습니다.');
      return;
    }
    if (!/^010\d{8}$/.test(form.phone.replace(/\D/g, ''))) {
      notify('010으로 시작하는 휴대폰 번호 11자리를 입력해 주세요.');
      return;
    }
    setProfile({
      ...profile,
      name: form.stageName.trim(),
      role: 'artist',
      verification: '본인확인 대기',
    });
    notify('아티스트 체험 계정을 만들었습니다. 실제 서버에는 전송되지 않습니다.');
    navigate('signup-complete');
  }

  return (
    <section className="signup-page" aria-labelledby="artist-signup-title">
      <button className="text-button" onClick={() => navigate('signup-role')}>
        ← 가입 역할 다시 선택하기
      </button>
      <div className="signup-heading">
        <p className="eyebrow">ARTIST SIGN UP</p>
        <h1 id="artist-signup-title">아티스트로 가입할래!</h1>
        <p>나의 음악과 활동 정보를 소개하고 새로운 무대를 만나보세요.</p>
      </div>
      <form className="signup-form" onSubmit={submit}>
        <div className="signup-section">
          <span className="signup-step">01</span>
          <div>
            <h2>로그인 정보</h2>
            <p>아티스트 로그인에 사용할 정보를 입력해 주세요.</p>
          </div>
        </div>
        <label>
          아이디
          <input required autoComplete="username" value={form.loginId} onChange={(e) => update('loginId', e.target.value)} placeholder="영문과 숫자를 조합해 주세요" />
        </label>
        <div className="signup-two-columns">
          <label>
            비밀번호
            <input required type="password" autoComplete="new-password" value={form.password} onChange={(e) => update('password', e.target.value)} placeholder="8자 이상" />
          </label>
          <label>
            비밀번호 확인
            <input required type="password" autoComplete="new-password" value={form.passwordConfirm} onChange={(e) => update('passwordConfirm', e.target.value)} placeholder="한 번 더 입력해 주세요" />
          </label>
        </div>

        <div className="signup-section">
          <span className="signup-step">02</span>
          <div>
            <h2>본인확인 정보</h2>
            <p>안전한 활동과 추후 본인인증에 필요한 정보입니다.</p>
          </div>
        </div>
        <label>
          실명
          <input required autoComplete="name" value={form.realName} onChange={(e) => update('realName', e.target.value)} placeholder="실명을 입력해 주세요" />
        </label>
        <div className="signup-two-columns">
          <label>
            휴대폰 번호
            <input required type="tel" inputMode="tel" autoComplete="tel-national" value={form.phone} onChange={(e) => update('phone', e.target.value.replace(/[^0-9-]/g, ''))} placeholder="010-1234-5678" />
          </label>
          <label>
            이메일
            <input required type="email" autoComplete="email" value={form.email} onChange={(e) => update('email', e.target.value)} placeholder="name@example.com" />
          </label>
        </div>

        <div className="signup-section">
          <span className="signup-step">03</span>
          <div>
            <h2>아티스트 정보</h2>
            <p>추천과 프로필에 사용할 기본 정보입니다.</p>
          </div>
        </div>
        <label>
          활동명
          <input required maxLength={40} value={form.stageName} onChange={(e) => update('stageName', e.target.value)} placeholder="활동명을 입력해 주세요" />
        </label>
        <div className="signup-two-columns">
          <label>
            대표 장르
            <select required value={form.genre} onChange={(e) => update('genre', e.target.value)}>
              <option value="">장르 선택</option>
              <option>힙합</option><option>밴드</option><option>발라드</option><option>어쿠스틱</option><option>전자음악</option><option>기타</option>
            </select>
          </label>
          <label>
            세부 장르
            <input required value={form.detailGenre} onChange={(e) => update('detailGenre', e.target.value)} placeholder="예: 인디 록, 감성 힙합" />
          </label>
        </div>
        <label>
          주요 활동 지역
          <input required value={form.region} onChange={(e) => update('region', e.target.value)} placeholder="예: 서울·경기" />
        </label>

        <div className="signup-consent">
          <label><input required type="checkbox" checked={form.age} onChange={(e) => update('age', e.target.checked)} /> 만 18세 이상입니다.</label>
          <label><input required type="checkbox" checked={form.terms} onChange={(e) => update('terms', e.target.checked)} /> 서비스 이용약관에 동의합니다.</label>
          <label><input required type="checkbox" checked={form.privacy} onChange={(e) => update('privacy', e.target.checked)} /> 개인정보 처리 안내에 동의합니다.</label>
        </div>
        <div className="signup-next-step">
          <strong>가입 후 추가할 정보</strong>
          <p>YouTube·SoundCloud 계정, 대표 음원, 공연 가능 일정, 출연료와 이동 가능 지역을 마이페이지에서 등록합니다.</p>
          <p>사업자정보·저작권·공연 증빙은 선택적인 신뢰 정보이며 음악 매칭 점수에는 가산하지 않습니다.</p>
        </div>
        <button className="primary full signup-submit" type="submit">아티스트 가입 완료</button>
      </form>
    </section>
  );
}
