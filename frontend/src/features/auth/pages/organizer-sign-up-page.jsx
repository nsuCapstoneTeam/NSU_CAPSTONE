import { useEffect, useRef, useState } from 'react';
import {
  ChoiceGroup,
  CodeVerification,
  LabelText,
  RequiredNote,
  SignupMissingCard,
  SignupProgress,
} from '../components/signup-fields.jsx';
import { focusSignupField } from '../components/focus-signup-field.js';
import './sign-up-pages.css';
import './sign-up-design.css';

// 행사 관계자(EVENT_PARTNER) 회원가입 화면 (요구사항 AUTH-005·AUTH-006·AUTH-007)
// 담당자 정보 → 연락처 인증(휴대폰·이메일) → 주최 정보(선택) → 약관 동의
// - AUTH-007: 사업자등록증·소속 증명은 요구하지 않음 → 주최 정보는 모두 선택
// - 로그인은 휴대폰 번호 + 인증번호 방식이라 아이디·비밀번호를 받지 않음
// - 행사 날짜·장소·예산 등은 가입이 아니라 행사 등록(EVT-042)에서 입력
// 실제 인증·서버 저장은 하지 않고, 이 브라우저의 체험 프로필만 바꿉니다.

// [임시 선택지] 요구사항에 행사 유형 목록이 정해지지 않았습니다.
// 팀 확정(Slack #dev Human Confirm) 후 이 목록만 바꾸면 됩니다. (마이페이지와 같은 목록)
const EVENT_TYPES = ['대학 축제', '지역 축제', '기업 행사', '공연·콘서트', '결혼식', '기타'];

export default function OrganizerSignUp({ profile, setProfile, notify, navigate }) {
  // 가입 폼 입력값 (age·terms·privacy는 필수 동의 체크)
  const [form, setForm] = useState({
    name: '',
    phone: '',
    email: '',
    organization: '',
    eventType: '',
    region: '',
    website: '',
    age: false,
    terms: false,
    privacy: false,
  });
  // 인증 완료 여부 (휴대폰 번호는 로그인에도 사용)
  const [phoneVerified, setPhoneVerified] = useState(false);
  const [emailVerified, setEmailVerified] = useState(false);
  // 가입 버튼을 누른 뒤부터 빠진 칸을 붉게 표시
  const [showMissing, setShowMissing] = useState(false);
  // 빠진 항목 안내 카드 열림 여부
  const [missingOpen, setMissingOpen] = useState(false);
  // 가입 실패 시 포커스를 옮길 안내 카드 제목, 누를 때마다 1씩 올려서 다시 포커스
  const missingTitle = useRef(null);
  const [summaryFocusCount, setSummaryFocusCount] = useState(0);

  useEffect(() => {
    if (summaryFocusCount) missingTitle.current?.focus({ preventScroll: true });
  }, [summaryFocusCount]);

  // 홈페이지는 선택 항목이지만, 입력했다면 주소 형식이어야 함
  const websiteValid =
    !form.website.trim() || /^https?:\/\/[^\s.]+\.[^\s]+$/.test(form.website.trim());

  // 단계별 완료 여부 (상단 진행 표시에 사용)
  const managerComplete = Boolean(form.name.trim());
  const contactComplete = phoneVerified && emailVerified;
  // 주최 정보는 모두 선택이라, 홈페이지 형식만 맞으면 완료로 봄
  const hostInfoComplete = websiteValid;
  const consentComplete = form.age && form.terms && form.privacy;

  const currentStep = !managerComplete
    ? 1
    : !contactComplete
      ? 2
      : !hostInfoComplete
        ? 3
        : 4;
  const progressSteps = [
    { number: 1, label: '담당자 정보', complete: managerComplete },
    { number: 2, label: '연락처 인증', complete: contactComplete },
    { number: 3, label: '주최 정보 (선택)', complete: managerComplete && hostInfoComplete },
    { number: 4, label: '약관 동의', complete: consentComplete },
  ];

  // 아직 채우지 않은 필수 항목 목록 (id는 해당 입력칸으로 이동할 때 사용)
  const missing = [
    !form.name.trim() && { id: 'organizer-signup-name', label: '담당자 이름' },
    !phoneVerified && { id: 'organizer-signup-phone', label: '휴대폰 인증' },
    !emailVerified && { id: 'organizer-signup-email', label: '이메일 인증' },
    !websiteValid && {
      id: 'organizer-signup-website',
      label: '홈페이지 주소 형식 (https://로 시작)',
    },
    !form.age && { id: 'organizer-signup-age', label: '만 18세 이상 확인' },
    !form.terms && { id: 'organizer-signup-terms', label: '서비스 이용약관 동의' },
    !form.privacy && { id: 'organizer-signup-privacy', label: '개인정보 처리 안내 동의' },
  ].filter(Boolean);
  const missingIds = new Set(missing.map((item) => item.id));
  const invalid = (id) => showMissing && missingIds.has(id);

  // 입력칸 하나의 값을 바꿈
  function update(name, value) {
    setForm((current) => ({ ...current, [name]: value }));
  }

  // 행사 유형은 선택 항목이라, 같은 것을 다시 누르면 선택 해제
  function toggleEventType(type) {
    setForm((current) => ({
      ...current,
      eventType: current.eventType === type ? '' : type,
    }));
  }

  // 가입: 빠진 항목이 있으면 안내 카드를 열고(포커스는 카드 제목) 첫 번째 빠진 칸을 화면에 보여 줌
  //       모두 채웠으면 체험 프로필을 행사 관계자로 저장 → 가입 완료 화면
  function submit(event) {
    event.preventDefault();
    if (missing.length) {
      setShowMissing(true);
      setMissingOpen(true);
      setSummaryFocusCount((count) => count + 1);
      focusSignupField(missing[0].id, { focus: false });
      return;
    }
    // 휴대폰 번호·이메일은 저장하지 않음 (개인정보 최소화, SEC-120)
    setProfile({
      ...profile,
      name: form.name.trim(),
      role: 'organizer',
      verification: '기본 가입 완료 (체험)',
      organization: form.organization.trim(),
      eventType: form.eventType || profile.eventType,
      region: form.region.trim() || profile.region,
    });
    notify('행사 관계자 체험 계정을 만들었습니다. 실제 서버에는 전송되지 않습니다.');
    navigate('signup-complete');
  }

  return (
    <section
      className="signup-page signup-page--designed signup-page--organizer"
      aria-labelledby="organizer-signup-title"
    >
      <button className="text-button" onClick={() => navigate('signup-role')}>
        ← 뒤로 가기
      </button>
      <div className="signup-heading">
        {/* 로그인 화면의 행사 관계자 CD(코랄)와 이어지는 작은 CD 장식 */}
        <span className="signup-mini-cd" aria-hidden="true" />
        <p className="eyebrow">ORGANIZER SIGN UP</p>
        <h1 id="organizer-signup-title">행사 관계자로 가입할래!</h1>
        <p>행사를 소개하고 어울리는 아티스트를 추천받아 보세요.</p>
      </div>
      <form className="signup-form" onSubmit={submit} noValidate>
        <RequiredNote />
        <SignupProgress
          steps={progressSteps}
          current={currentStep}
          label="행사 관계자 회원가입 진행 단계"
        />

        {/* 01. 담당자 정보 */}
        <div className="signup-section">
          <span className="signup-step">01</span>
          <div>
            <h2>담당자 정보</h2>
            <p>아티스트에게 보이는 이름입니다. 마이페이지에서 바꿀 수 있어요.</p>
          </div>
        </div>
        <label>
          <LabelText required>담당자 이름</LabelText>
          <input
            id="organizer-signup-name"
            required
            maxLength={40}
            autoComplete="name"
            value={form.name}
            onChange={(e) => update('name', e.target.value)}
            placeholder="이름 또는 활동명을 입력해 주세요"
            aria-invalid={invalid('organizer-signup-name')}
          />
        </label>

        {/* 02. 연락처 인증: 휴대폰(로그인에 사용) + 이메일 (모두 필수, AUTH-006) */}
        <div className="signup-section">
          <span className="signup-step">02</span>
          <div>
            <h2>연락처 인증</h2>
            <p>휴대폰 번호는 로그인할 때 사용해요. 휴대폰과 이메일 인증을 모두 완료해 주세요.</p>
          </div>
        </div>
        <CodeVerification
          kind="phone"
          id="organizer-signup-phone"
          value={form.phone}
          onValueChange={(value) => update('phone', value)}
          verified={phoneVerified}
          setVerified={setPhoneVerified}
          invalid={invalid('organizer-signup-phone')}
          notify={notify}
        />
        <CodeVerification
          kind="email"
          id="organizer-signup-email"
          value={form.email}
          onValueChange={(value) => update('email', value)}
          verified={emailVerified}
          setVerified={setEmailVerified}
          invalid={invalid('organizer-signup-email')}
          notify={notify}
        />

        {/* 03. 주최 정보: 모두 선택 (AUTH-007: 소속 증명 없이 가입 가능) */}
        <div className="signup-section">
          <span className="signup-step">03</span>
          <div>
            <h2>
              주최 정보 <span className="optional">선택</span>
            </h2>
            <p>가입에 꼭 필요하지 않아요. 입력하면 마이페이지에 미리 채워져요.</p>
          </div>
        </div>
        <label>
          소속 또는 단체명
          <input
            id="organizer-signup-organization"
            maxLength={60}
            autoComplete="organization"
            value={form.organization}
            onChange={(e) => update('organization', e.target.value)}
            placeholder="예: OO대학교 축제준비위원회, 카페 OO"
          />
        </label>
        <ChoiceGroup
          legend="주로 준비하는 행사"
          hint="다시 누르면 해제돼요."
          name="event-type"
          idPrefix="organizer-signup-event-type"
          type="checkbox"
          options={EVENT_TYPES}
          isChecked={(option) => form.eventType === option}
          onChange={toggleEventType}
        />
        <div className="signup-two-columns">
          <label>
            주요 행사 지역
            <input
              id="organizer-signup-region"
              maxLength={40}
              value={form.region}
              onChange={(e) => update('region', e.target.value)}
              placeholder="예: 서울·경기"
            />
          </label>
          <label>
            홈페이지 또는 공식 채널
            <input
              id="organizer-signup-website"
              type="url"
              inputMode="url"
              autoComplete="url"
              value={form.website}
              onChange={(e) => update('website', e.target.value)}
              placeholder="https://"
              aria-describedby="organizer-website-hint"
              aria-invalid={invalid('organizer-signup-website')}
            />
            <small id="organizer-website-hint" className="signup-field-hint">
              입력한다면 https://로 시작하는 주소를 적어 주세요.
            </small>
          </label>
        </div>

        {/* 04. 약관 동의 */}
        <div className="signup-section">
          <span className="signup-step">04</span>
          <div>
            <h2>약관 동의</h2>
            <p>필수 항목에 모두 동의해야 가입할 수 있어요.</p>
          </div>
        </div>
        <div className="signup-consent">
          <label>
            <input
              id="organizer-signup-age"
              required
              type="checkbox"
              checked={form.age}
              onChange={(e) => update('age', e.target.checked)}
              aria-invalid={invalid('organizer-signup-age')}
            />{' '}
            [필수] 만 18세 이상입니다.
          </label>
          <label>
            <input
              id="organizer-signup-terms"
              required
              type="checkbox"
              checked={form.terms}
              onChange={(e) => update('terms', e.target.checked)}
              aria-invalid={invalid('organizer-signup-terms')}
            />{' '}
            [필수] 서비스 이용약관에 동의합니다.
          </label>
          {/* 약관 내용 보기 (정식 약관 확정 후 전문 연결) */}
          <details className="signup-terms">
            <summary>서비스 이용약관 보기</summary>
            <p>
              정식 이용약관은 확정 후 연결됩니다. 할래말래는 아티스트와 행사 관계자의
              매칭을 돕는 서비스이며, 매칭 이후의 계약·결제·정산은 당사자 간에
              진행됩니다. 허위 행사 등록, 스팸 메시지, 아티스트 괴롭힘은 이용 제한
              대상입니다.
            </p>
          </details>
          <label>
            <input
              id="organizer-signup-privacy"
              required
              type="checkbox"
              checked={form.privacy}
              onChange={(e) => update('privacy', e.target.checked)}
              aria-invalid={invalid('organizer-signup-privacy')}
            />{' '}
            [필수] 개인정보 처리 안내에 동의합니다.
          </label>
          <details className="signup-terms">
            <summary>개인정보 처리 안내 보기</summary>
            <p>
              정식 개인정보 처리방침은 확정 후 연결됩니다. 체험판에서는 입력한 휴대폰
              번호·이메일을 저장하거나 전송하지 않습니다.
            </p>
          </details>
        </div>

        {/* 가입 후 입력할 정보 안내 */}
        <div className="signup-next-step">
          <strong>가입 후 할 수 있는 일</strong>
          <p>행사 날짜·장소·예산·원하는 장르는 행사를 등록하거나 아티스트 추천을 요청할 때 입력합니다.</p>
          <p>사업자등록증이나 소속 증명 없이 바로 이용할 수 있어요.</p>
        </div>

        {/* 빠진 항목 안내 카드 (가입 버튼을 눌렀는데 빠진 항목이 있을 때만) */}
        {missingOpen && (
          <SignupMissingCard
            idPrefix="organizer-signup"
            missing={missing}
            titleRef={missingTitle}
            onClose={() => setMissingOpen(false)}
            onGo={focusSignupField}
            submitId="organizer-signup-submit"
          />
        )}

        <button
          id="organizer-signup-submit"
          className="primary full signup-submit"
          type="submit"
        >
          행사 관계자 가입 완료
        </button>
      </form>
    </section>
  );
}
