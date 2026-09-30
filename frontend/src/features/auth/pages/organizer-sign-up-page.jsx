import { useState } from 'react';
import './sign-up-pages.css';

export default function OrganizerSignUp({ profile, setProfile, notify, navigate }) {
  const [form, setForm] = useState({
    name: '',
    phone: '',
    email: '',
    organization: '',
    position: '',
    eventType: '',
    region: '',
    website: '',
    age: false,
    terms: false,
    privacy: false,
  });

  function update(name, value) {
    setForm((current) => ({ ...current, [name]: value }));
  }

  function submit(event) {
    event.preventDefault();
    if (!/^010\d{8}$/.test(form.phone.replace(/\D/g, ''))) {
      notify('010으로 시작하는 휴대폰 번호 11자리를 입력해 주세요.');
      return;
    }
    setProfile({
      ...profile,
      name: form.name.trim(),
      role: 'organizer',
      verification: '주최자 확인 대기',
    });
    notify('행사 관계자 체험 계정을 만들었습니다. 실제 서버에는 전송되지 않습니다.');
    navigate('signup-complete');
  }

  return (
    <section className="signup-page" aria-labelledby="organizer-signup-title">
      <button className="text-button" onClick={() => navigate('signup-role')}>
        ← 가입 역할 다시 선택하기
      </button>
      <div className="signup-heading">
        <p className="eyebrow">ORGANIZER SIGN UP</p>
        <h1 id="organizer-signup-title">행사 관계자로 가입할래!</h1>
        <p>행사를 소개하고 어울리는 아티스트를 추천받아 보세요.</p>
      </div>
      <form className="signup-form" onSubmit={submit}>
        <div className="signup-section">
          <span className="signup-step">01</span>
          <div>
            <h2>담당자 정보</h2>
            <p>로그인과 행사 연락에 사용할 기본 정보입니다.</p>
          </div>
        </div>
        <label>
          담당자 이름
          <input required maxLength={40} value={form.name} onChange={(e) => update('name', e.target.value)} placeholder="담당자 이름을 입력해 주세요" />
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
          <span className="signup-step">02</span>
          <div>
            <h2>주최 정보</h2>
            <p>주최자 확인과 추천 경험에 활용할 정보입니다.</p>
          </div>
        </div>
        <div className="signup-two-columns">
          <label>
            소속 또는 단체명
            <input required maxLength={60} value={form.organization} onChange={(e) => update('organization', e.target.value)} placeholder="학교·기업·기관·단체명" />
          </label>
          <label>
            담당 직책
            <input required maxLength={40} value={form.position} onChange={(e) => update('position', e.target.value)} placeholder="예: 축제준비위원" />
          </label>
        </div>
        <label>
          주로 준비하는 행사
          <select required value={form.eventType} onChange={(e) => update('eventType', e.target.value)}>
            <option value="">행사 유형 선택</option>
            <option>대학 축제</option><option>지역 축제</option><option>기업 행사</option><option>공연·콘서트</option><option>결혼식</option><option>기타</option>
          </select>
        </label>
        <div className="signup-two-columns">
          <label>
            주요 행사 지역
            <input required value={form.region} onChange={(e) => update('region', e.target.value)} placeholder="예: 서울·경기" />
          </label>
          <label>
            홈페이지 또는 공식 채널 <span className="optional">선택</span>
            <input type="url" value={form.website} onChange={(e) => update('website', e.target.value)} placeholder="https://" />
          </label>
        </div>

        <div className="signup-consent">
          <label><input required type="checkbox" checked={form.age} onChange={(e) => update('age', e.target.checked)} /> 만 18세 이상입니다.</label>
          <label><input required type="checkbox" checked={form.terms} onChange={(e) => update('terms', e.target.checked)} /> 서비스 이용약관에 동의합니다.</label>
          <label><input required type="checkbox" checked={form.privacy} onChange={(e) => update('privacy', e.target.checked)} /> 개인정보 처리 안내에 동의합니다.</label>
        </div>
        <div className="signup-next-step">
          <strong>가입 후 추가할 정보</strong>
          <p>구체적인 행사 날짜·지역·예산·원하는 분위기는 아티스트 추천을 요청할 때 입력합니다.</p>
          <p>소속·행사 개최 증빙과 사업자정보는 선택적인 주최자 신뢰 정보로 등록합니다.</p>
        </div>
        <button className="primary full signup-submit" type="submit">행사 관계자 가입 완료</button>
      </form>
    </section>
  );
}
