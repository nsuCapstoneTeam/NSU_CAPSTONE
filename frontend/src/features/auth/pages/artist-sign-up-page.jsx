import { useEffect, useRef, useState } from 'react';
import Icon from '../../../components/common/icon.jsx';
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

// 아티스트 회원가입 화면 (Linear NSU-14 / NSU-38, 요구사항 AUTH-006·AUTH-008)
// 로그인 정보 → 본인 확인(실명 본인확인 + 휴대폰·이메일 인증) → 아티스트 정보 → 약관 동의
// 실제 인증·서버 저장은 하지 않고, 이 브라우저의 체험 프로필만 바꿉니다.
// 외부 음악 플랫폼(YouTube·SoundCloud) 연결은 가입 화면에서 하지 않고 마이페이지에서 진행합니다.

// [임시 선택지] 요구사항에 항목 이름만 있고 선택지는 정해지지 않았습니다.
// 팀 확정(Slack #dev Human Confirm) 후 이 목록만 바꾸면 됩니다.
const ARTIST_TYPES = ['솔로', '듀오', '밴드·그룹', 'DJ·프로듀서', '기타'];
const CONTACT_METHODS = ['이메일', '전화·문자', '인스타그램 DM'];
// 연락 수단별 입력칸 (선택한 수단만 표시, 표시된 칸은 필수)
const CONTACT_FIELDS = {
  이메일: { key: 'contactEmail', id: 'artist-signup-contact-email', label: '연락받을 이메일' },
  '전화·문자': { key: 'contactPhone', id: 'artist-signup-contact-phone', label: '연락받을 전화번호' },
  '인스타그램 DM': {
    key: 'contactInstagram',
    id: 'artist-signup-contact-instagram',
    label: '인스타그램 아이디',
  },
};
const CONTACT_PATTERNS = {
  contactEmail: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
  // 휴대폰·일반 전화 모두 허용: 0으로 시작하는 숫자 9~11자리
  contactPhone: /^0\d{8,10}$/,
  // 인스타그램 아이디 규칙: 영문·숫자·마침표·밑줄 30자 이내 (@는 있어도 없어도 됨)
  contactInstagram: /^@?[A-Za-z0-9._]{1,30}$/,
};
// 요구사항: 사업자 / 비사업자를 구분하고 사업자인 경우에만 사업자등록번호를 받음
const BUSINESS_TYPES = ['비사업자', '사업자'];

const IDENTITY_CHECK_MS = 800; // 체험용 본인확인 대기 시간

export default function ArtistSignUp({ profile, setProfile, notify, navigate }) {
  // 가입 폼 입력값 (age·terms·privacy는 필수 동의 체크)
  const [form, setForm] = useState({
    loginId: '',
    password: '',
    passwordConfirm: '',
    realName: '',
    phone: '',
    email: '',
    stageName: '',
    artistType: '',
    genre: '',
    detailGenre: '',
    region: '',
    contactMethods: [],
    // 연락 수단별 연락처 (선택한 수단만 입력)
    contactEmail: '',
    contactPhone: '',
    contactInstagram: '',
    businessType: '',
    businessNumber: '',
    age: false,
    terms: false,
    privacy: false,
  });
  // 인증 상태: 실명 본인확인('idle' | 'checking' | 'done'), 휴대폰·이메일 인증 완료 여부
  const [identityStatus, setIdentityStatus] = useState('idle');
  const [phoneVerified, setPhoneVerified] = useState(false);
  const [emailVerified, setEmailVerified] = useState(false);
  // 비밀번호 / 비밀번호 확인 입력칸의 글자 보이기 여부
  const [showPassword, setShowPassword] = useState(false);
  const [showPasswordConfirm, setShowPasswordConfirm] = useState(false);
  // 가입 버튼을 누른 뒤부터 빠진 칸을 붉게 표시
  const [showMissing, setShowMissing] = useState(false);
  // 빠진 항목 안내 카드 열림 여부 (가입 버튼을 눌렀는데 빠진 항목이 있을 때 열림)
  const [missingOpen, setMissingOpen] = useState(false);
  const identityTimer = useRef(null);
  // 가입 실패 시 키보드·화면낭독기 사용자가 바로 안내 카드를 읽도록 포커스를 옮길 제목
  const missingTitle = useRef(null);
  // 가입 버튼을 누를 때마다 1씩 올려서, 카드가 이미 열려 있어도 다시 포커스를 옮김
  const [summaryFocusCount, setSummaryFocusCount] = useState(0);

  useEffect(() => () => window.clearTimeout(identityTimer.current), []);

  // 안내 카드가 화면에 그려진 뒤 제목으로 포커스 이동 (UI/UX Pro Max: focus-management)
  useEffect(() => {
    if (summaryFocusCount) missingTitle.current?.focus({ preventScroll: true });
  }, [summaryFocusCount]);

  const identityVerified = identityStatus === 'done';
  const isBusiness = form.businessType === '사업자';
  const businessNumberValid = /^\d{10}$/.test(form.businessNumber.replace(/\D/g, ''));

  // 단계별 완료 여부 (상단 진행 표시에 사용)
  const passwordsMatch = Boolean(
    form.passwordConfirm && form.password === form.passwordConfirm,
  );
  const loginInfoComplete = Boolean(
    form.loginId.trim() && form.password.length >= 8 && passwordsMatch,
  );
  const identityComplete =
    Boolean(form.realName.trim()) && identityVerified && phoneVerified && emailVerified;
  // 선택한 연락 수단의 연락처가 모두 올바르게 입력됐는지
  const contactValue = (key) =>
    key === 'contactPhone' ? form[key].replace(/\D/g, '') : form[key].trim();
  const contactProblems = form.contactMethods
    .map((method) => CONTACT_FIELDS[method])
    .filter((field) => !CONTACT_PATTERNS[field.key].test(contactValue(field.key)));
  const contactDetailsValid = contactProblems.length === 0;
  const artistInfoComplete = Boolean(
    form.stageName.trim() &&
      form.artistType &&
      form.genre &&
      form.region.trim() &&
      form.contactMethods.length > 0 &&
      contactDetailsValid &&
      form.businessType &&
      (!isBusiness || businessNumberValid),
  );
  const consentComplete = form.age && form.terms && form.privacy;

  // 현재 진행 중인 단계: 앞 단계가 끝나야 다음 단계로 넘어감
  const currentStep = !loginInfoComplete
    ? 1
    : !identityComplete
      ? 2
      : !artistInfoComplete
        ? 3
        : 4;
  const progressSteps = [
    { number: 1, label: '로그인 정보', complete: loginInfoComplete },
    { number: 2, label: '본인 확인', complete: identityComplete },
    { number: 3, label: '아티스트 정보', complete: artistInfoComplete },
    { number: 4, label: '약관 동의', complete: consentComplete },
  ];

  // 아직 채우지 않은 필수 항목 목록 (id는 해당 입력칸으로 이동할 때 사용)
  const missing = [
    !form.loginId.trim() && { id: 'artist-signup-id', label: '아이디' },
    form.password.length < 8 && { id: 'artist-signup-password', label: '비밀번호 (8자 이상)' },
    form.password.length >= 8 &&
      !passwordsMatch && { id: 'artist-signup-password-confirm', label: '비밀번호 확인' },
    !form.realName.trim() && { id: 'artist-signup-real-name', label: '실명' },
    !identityVerified && { id: 'artist-signup-identity', label: '실명 본인확인' },
    !phoneVerified && { id: 'artist-signup-phone', label: '휴대폰 인증' },
    !emailVerified && { id: 'artist-signup-email', label: '이메일 인증' },
    !form.stageName.trim() && { id: 'artist-signup-stage-name', label: '활동명' },
    !form.artistType && { id: 'artist-signup-type-0', label: '아티스트 형태' },
    !form.genre && { id: 'artist-signup-genre', label: '대표 장르' },
    !form.region.trim() && { id: 'artist-signup-region', label: '주요 활동 지역' },
    form.contactMethods.length === 0 && {
      id: 'artist-signup-contact-0',
      label: '연락 가능 수단',
    },
    ...contactProblems.map((field) => ({ id: field.id, label: field.label })),
    !form.businessType && { id: 'artist-signup-business-0', label: '사업자 유형' },
    isBusiness &&
      !businessNumberValid && {
        id: 'artist-signup-business-number',
        label: '사업자등록번호 (숫자 10자리)',
      },
    !form.age && { id: 'artist-signup-age', label: '만 18세 이상 확인' },
    !form.terms && { id: 'artist-signup-terms', label: '서비스 이용약관 동의' },
    !form.privacy && { id: 'artist-signup-privacy', label: '개인정보 처리 안내 동의' },
  ].filter(Boolean);
  const missingIds = new Set(missing.map((item) => item.id));
  // 가입 버튼을 누른 뒤에만 빠진 칸을 붉게 표시
  const invalid = (id) => showMissing && missingIds.has(id);

  // 입력칸 하나의 값을 바꿈
  function update(name, value) {
    setForm((current) => ({ ...current, [name]: value }));
  }

  // 연락 가능 수단은 여러 개 선택 가능
  // 이메일·전화를 고르면 위에서 인증한 이메일·휴대폰 번호를 미리 채워 줌 (바꿀 수 있음)
  function toggleContact(method) {
    setForm((current) => {
      const selected = current.contactMethods.includes(method);
      const next = {
        ...current,
        contactMethods: selected
          ? current.contactMethods.filter((item) => item !== method)
          : [...current.contactMethods, method],
      };
      if (!selected && method === '이메일' && !current.contactEmail) {
        next.contactEmail = current.email;
      }
      if (!selected && method === '전화·문자' && !current.contactPhone) {
        next.contactPhone = current.phone;
      }
      return next;
    });
  }

  // 사업자 유형을 비사업자로 바꾸면 입력했던 사업자등록번호를 지움
  function selectBusinessType(type) {
    setForm((current) => ({
      ...current,
      businessType: type,
      businessNumber: type === '사업자' ? current.businessNumber : '',
    }));
  }

  // 실명 본인확인 (체험용): 실제 서비스에서는 본인확인 기관 인증 창으로 연결
  function startIdentityCheck() {
    if (!form.realName.trim()) {
      notify('실명을 먼저 입력해 주세요.');
      document.getElementById('artist-signup-real-name')?.focus();
      return;
    }
    setIdentityStatus('checking');
    identityTimer.current = window.setTimeout(() => {
      setIdentityStatus('done');
      notify('체험용 본인확인이 완료되었습니다. 실제 본인확인은 진행되지 않았습니다.');
    }, IDENTITY_CHECK_MS);
  }

  // 빠진 항목으로 이동 (안내 목록의 버튼에서 사용)
  const focusField = focusSignupField;

  // 가입: 빠진 항목이 있으면 안내 카드를 열고(포커스는 카드 제목) 첫 번째 빠진 칸을 화면에 보여 줌
  //       모두 채웠으면 체험 프로필을 아티스트로 저장 → 가입 완료 화면
  function submit(event) {
    event.preventDefault();
    if (missing.length) {
      setShowMissing(true);
      setMissingOpen(true);
      setSummaryFocusCount((count) => count + 1);
      focusField(missing[0].id, { focus: false });
      return;
    }
    // 실명·휴대폰·이메일·연락처·사업자등록번호·비밀번호는 저장하지 않음 (개인정보 최소화, SEC-120)
    // 연락처 원문은 백엔드 연결 후 서버에 저장 (체험판은 어떤 연락 수단을 골랐는지만 저장)
    setProfile({
      ...profile,
      name: form.stageName.trim(),
      role: 'artist',
      verification: '본인확인 완료 (체험)',
      artistType: form.artistType,
      genre: form.genre,
      region: form.region.trim(),
      contactMethods: form.contactMethods,
      business: isBusiness,
    });
    notify('아티스트 체험 계정을 만들었습니다. 실제 서버에는 전송되지 않습니다.');
    navigate('signup-complete');
  }

  return (
    <section
      className="signup-page signup-page--designed signup-page--artist"
      aria-labelledby="artist-signup-title"
    >
      <button className="text-button" onClick={() => navigate('signup-role')}>
        ← 뒤로 가기
      </button>
      <div className="signup-heading">
        {/* 로그인 화면의 CD와 이어지는 작은 CD 장식 */}
        <span className="signup-mini-cd" aria-hidden="true" />
        <p className="eyebrow">ARTIST SIGN UP</p>
        <h1 id="artist-signup-title">아티스트로 가입할래!</h1>
        <p>나의 음악과 활동 정보를 소개하고 새로운 무대를 만나보세요.</p>
      </div>
      <form className="signup-form" onSubmit={submit} noValidate>
        {/* 상단 진행 단계 표시 (현재 단계 강조, 완료 단계는 ✓) */}
        <RequiredNote />
        <SignupProgress
          steps={progressSteps}
          current={currentStep}
          label="아티스트 회원가입 진행 단계"
        />

        {/* 01. 로그인 정보 */}
        <div className="signup-section">
          <span className="signup-step">01</span>
          <div>
            <h2>로그인 정보</h2>
            <p>아티스트 로그인에 사용할 정보를 입력해 주세요.</p>
          </div>
        </div>
        <label>
          <LabelText required>아이디</LabelText>
          <span className="signup-id-row">
            <input
              id="artist-signup-id"
              required
              autoComplete="username"
              value={form.loginId}
              onChange={(e) => update('loginId', e.target.value)}
              placeholder="로그인에 사용할 아이디"
              aria-describedby="artist-login-id-hint"
              aria-invalid={invalid('artist-signup-id')}
            />
            {/* 중복 확인은 백엔드 연결 전이라 비활성화 */}
            <button
              type="button"
              className="signup-duplicate-button"
              disabled
              title="백엔드 연결 후 사용할 수 있습니다."
            >
              중복 확인
            </button>
          </span>
          <small id="artist-login-id-hint" className="signup-field-hint">
            {/* 아이디 글자 수·조합 규칙은 팀 확정 전이라 검증하지 않음. 확정되면 missing 목록에 조건 추가 */}
            로그인할 때 사용할 아이디예요.
          </small>
        </label>
        <div className="signup-two-columns">
          <label>
            <LabelText required>비밀번호</LabelText>
            <span className="signup-password-field">
              <input
                id="artist-signup-password"
                required
                type={showPassword ? 'text' : 'password'}
                minLength={8}
                autoComplete="new-password"
                value={form.password}
                onChange={(e) => update('password', e.target.value)}
                placeholder="8자 이상"
                aria-describedby="artist-password-hint"
                aria-invalid={invalid('artist-signup-password')}
              />
              {/* 비밀번호 보기/숨기기 전환 */}
              <button
                type="button"
                aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'}
                aria-pressed={showPassword}
                title={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'}
                onClick={() => setShowPassword((current) => !current)}
              >
                <Icon name={showPassword ? 'eye-off' : 'eye'} size={19} />
              </button>
            </span>
            <small
              id="artist-password-hint"
              className="signup-field-hint signup-password-hint"
            >
              {/* 특수문자 등 비밀번호 규칙은 팀 확정 전. 지금은 8자 이상만 확인 */}
              8자 이상 입력해 주세요.
            </small>
          </label>
          <label>
            <LabelText required>비밀번호 확인</LabelText>
            <span className="signup-password-field">
              <input
                id="artist-signup-password-confirm"
                required
                type={showPasswordConfirm ? 'text' : 'password'}
                autoComplete="new-password"
                value={form.passwordConfirm}
                onChange={(e) => update('passwordConfirm', e.target.value)}
                placeholder="한 번 더 입력해 주세요"
                aria-invalid={invalid('artist-signup-password-confirm')}
              />
              <button
                type="button"
                aria-label={
                  showPasswordConfirm ? '비밀번호 확인 숨기기' : '비밀번호 확인 보기'
                }
                aria-pressed={showPasswordConfirm}
                title={
                  showPasswordConfirm ? '비밀번호 확인 숨기기' : '비밀번호 확인 보기'
                }
                onClick={() => setShowPasswordConfirm((current) => !current)}
              >
                <Icon name={showPasswordConfirm ? 'eye-off' : 'eye'} size={19} />
              </button>
            </span>
            {/* 두 비밀번호가 같으면 일치 안내 */}
            <small className="signup-password-match" aria-live="polite">
              {passwordsMatch ? '✓ 비밀번호가 일치합니다.' : ''}
            </small>
          </label>
        </div>

        {/* 02. 본인 확인: 실명 본인확인 + 휴대폰 인증 + 이메일 인증 (모두 필수) */}
        <div className="signup-section">
          <span className="signup-step">02</span>
          <div>
            <h2>본인 확인</h2>
            <p>실명 본인확인과 휴대폰·이메일 인증을 모두 완료해 주세요.</p>
          </div>
        </div>

        {/* 실명 본인확인: 실명을 바꾸면 다시 확인해야 함 */}
        <fieldset className="signup-verification">
          <legend>실명 본인확인</legend>
          <div className="signup-verification-card">
            <label>
              <LabelText required>실명</LabelText>
              <span className="signup-verification-row">
                <input
                  id="artist-signup-real-name"
                  required
                  autoComplete="name"
                  value={form.realName}
                  onChange={(e) => {
                    update('realName', e.target.value);
                    window.clearTimeout(identityTimer.current);
                    setIdentityStatus('idle');
                  }}
                  placeholder="실명을 입력해 주세요"
                  aria-invalid={invalid('artist-signup-real-name')}
                />
                <button
                  id="artist-signup-identity"
                  type="button"
                  onClick={startIdentityCheck}
                  disabled={identityStatus !== 'idle'}
                  aria-invalid={invalid('artist-signup-identity')}
                >
                  {identityStatus === 'done'
                    ? '본인확인 완료'
                    : identityStatus === 'checking'
                      ? '확인 중…'
                      : '본인확인하기'}
                </button>
              </span>
            </label>
            <p className={identityVerified ? 'is-complete' : ''} role="status">
              {identityVerified
                ? '체험용 본인확인이 완료되었습니다.'
                : '실제 서비스에서는 본인확인 기관의 인증 창으로 연결됩니다. 주민등록번호는 받지 않습니다.'}
            </p>
          </div>
        </fieldset>

        {/* 휴대폰 인증 */}
        <CodeVerification
          kind="phone"
          id="artist-signup-phone"
          value={form.phone}
          onValueChange={(value) => update('phone', value)}
          verified={phoneVerified}
          setVerified={setPhoneVerified}
          invalid={invalid('artist-signup-phone')}
          notify={notify}
        />

        {/* 이메일 인증 */}
        <CodeVerification
          kind="email"
          id="artist-signup-email"
          value={form.email}
          onValueChange={(value) => update('email', value)}
          verified={emailVerified}
          setVerified={setEmailVerified}
          invalid={invalid('artist-signup-email')}
          notify={notify}
        />

        {/* 03. 아티스트 정보 */}
        <div className="signup-section">
          <span className="signup-step">03</span>
          <div>
            <h2>아티스트 정보</h2>
            <p>프로필에서 재설정 가능합니다.</p>
          </div>
        </div>
        <label>
          <LabelText required>활동명</LabelText>
          <input
            id="artist-signup-stage-name"
            required
            maxLength={40}
            value={form.stageName}
            onChange={(e) => update('stageName', e.target.value)}
            placeholder="활동명을 입력해 주세요"
            aria-invalid={invalid('artist-signup-stage-name')}
          />
        </label>

        {/* 아티스트 형태: 하나만 선택 */}
        <ChoiceGroup
          legend="아티스트 형태"
          required
          name="artist-type"
          idPrefix="artist-signup-type"
          type="radio"
          options={ARTIST_TYPES}
          isChecked={(option) => form.artistType === option}
          onChange={(option) => update('artistType', option)}
          invalid={invalid('artist-signup-type-0')}
        />

        <div className="signup-two-columns">
          <label>
            <LabelText required>대표 장르</LabelText>
            <select
              id="artist-signup-genre"
              required
              value={form.genre}
              onChange={(e) => update('genre', e.target.value)}
              aria-invalid={invalid('artist-signup-genre')}
            >
              <option value="">장르 선택</option>
              <option>힙합</option><option>밴드</option><option>발라드</option><option>어쿠스틱</option><option>전자음악</option><option>기타</option>
            </select>
          </label>
          <label>
            세부 장르
            <input
              id="artist-signup-detail-genre"
              maxLength={40}
              value={form.detailGenre}
              onChange={(e) => update('detailGenre', e.target.value)}
              placeholder="선택 · 예: 인디 록, 감성 힙합"
            />
          </label>
        </div>
        <label>
          <LabelText required>주요 활동 지역</LabelText>
          <input
            id="artist-signup-region"
            required
            value={form.region}
            onChange={(e) => update('region', e.target.value)}
            placeholder="예: 서울·경기"
            aria-invalid={invalid('artist-signup-region')}
          />
        </label>

        {/* 연락 가능 수단: 여러 개 선택 가능, 1개 이상 필수 */}
        <ChoiceGroup
          legend="연락 가능 수단"
          required
          hint="여러 개 선택할 수 있어요."
          name="contact-methods"
          idPrefix="artist-signup-contact"
          type="checkbox"
          options={CONTACT_METHODS}
          isChecked={(option) => form.contactMethods.includes(option)}
          onChange={toggleContact}
          invalid={invalid('artist-signup-contact-0')}
        />
        {/* 선택한 연락 수단의 연락처 입력칸 (선택한 것만 표시, 표시된 칸은 필수) */}
        {form.contactMethods.length > 0 && (
          <div className="signup-contact-details">
            {CONTACT_METHODS.filter((method) => form.contactMethods.includes(method)).map(
              (method) => {
                const field = CONTACT_FIELDS[method];
                const isEmail = field.key === 'contactEmail';
                const isPhone = field.key === 'contactPhone';
                return (
                  <label key={field.key}>
                    <LabelText required>{field.label}</LabelText>
                    <input
                      id={field.id}
                      required
                      type={isEmail ? 'email' : isPhone ? 'tel' : 'text'}
                      inputMode={isEmail ? 'email' : isPhone ? 'tel' : 'text'}
                      autoComplete={isEmail ? 'email' : isPhone ? 'tel-national' : 'off'}
                      maxLength={isEmail ? 80 : 31}
                      value={form[field.key]}
                      onChange={(e) =>
                        update(
                          field.key,
                          isPhone ? e.target.value.replace(/[^0-9-]/g, '') : e.target.value,
                        )
                      }
                      placeholder={
                        isEmail ? 'name@example.com' : isPhone ? '010-1234-5678' : '@instagram_id'
                      }
                      aria-invalid={invalid(field.id)}
                    />
                  </label>
                );
              },
            )}
            <small className="signup-field-hint">
              행사 관계자와 매칭된 뒤 연락받을 정보예요. 이메일·전화는 위에서 인증한 정보가 미리 채워져요.
            </small>
          </div>
        )}

        {/* 사업자 유형: 사업자일 때만 사업자등록번호 입력칸 표시 */}
        <ChoiceGroup
          legend="사업자 유형"
          required
          name="business-type"
          idPrefix="artist-signup-business"
          type="radio"
          options={BUSINESS_TYPES}
          isChecked={(option) => form.businessType === option}
          onChange={selectBusinessType}
          invalid={invalid('artist-signup-business-0')}
        />
        {isBusiness && (
          <label className="signup-business-number">
            <LabelText required>사업자등록번호</LabelText>
            <input
              id="artist-signup-business-number"
              required
              inputMode="numeric"
              maxLength={12}
              value={form.businessNumber}
              onChange={(e) =>
                update('businessNumber', e.target.value.replace(/[^0-9-]/g, ''))
              }
              placeholder="000-00-00000"
              aria-describedby="artist-business-number-hint"
              aria-invalid={invalid('artist-signup-business-number')}
            />
            <small id="artist-business-number-hint" className="signup-field-hint">
              숫자 10자리입니다. 사업자 정보 검증은 가입 후 선택적인 신뢰 정보로 진행합니다.
            </small>
          </label>
        )}

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
              id="artist-signup-age"
              required
              type="checkbox"
              checked={form.age}
              onChange={(e) => update('age', e.target.checked)}
              aria-invalid={invalid('artist-signup-age')}
            />{' '}
            [필수] 만 18세 이상입니다.
          </label>
          <label>
            <input
              id="artist-signup-terms"
              required
              type="checkbox"
              checked={form.terms}
              onChange={(e) => update('terms', e.target.checked)}
              aria-invalid={invalid('artist-signup-terms')}
            />{' '}
            [필수] 서비스 이용약관에 동의합니다.
          </label>
          {/* 약관 내용 보기 (정식 약관 확정 후 전문 연결) */}
          <details className="signup-terms">
            <summary>서비스 이용약관 보기</summary>
            <p>
              정식 이용약관은 확정 후 연결됩니다. 할래말래는 아티스트와 행사 관계자의
              매칭을 돕는 서비스이며, 매칭 이후의 계약·결제·정산은 당사자 간에
              진행됩니다.
            </p>
          </details>
          <label>
            <input
              id="artist-signup-privacy"
              required
              type="checkbox"
              checked={form.privacy}
              onChange={(e) => update('privacy', e.target.checked)}
              aria-invalid={invalid('artist-signup-privacy')}
            />{' '}
            [필수] 개인정보 처리 안내에 동의합니다.
          </label>
          <details className="signup-terms">
            <summary>개인정보 처리 안내 보기</summary>
            <p>
              정식 개인정보 처리방침은 확정 후 연결됩니다. 체험판에서는 입력한 실명·
              휴대폰 번호·이메일·연락처·사업자등록번호·비밀번호를 저장하거나 전송하지 않습니다.
            </p>
          </details>
        </div>

        {/* 가입 후 입력할 정보 안내 */}
        <div className="signup-next-step">
          <strong>가입 후 추가할 정보</strong>
          <p>YouTube·SoundCloud 계정, 대표 음원, 공연 가능 일정, 출연료와 이동 가능 지역을 마이페이지에서 등록합니다.</p>
          <p>사업자 정보 검증·저작권·공연 증빙은 선택적인 신뢰 정보이며 음악 매칭 점수에는 가산하지 않습니다.</p>
        </div>

        {/* 빠진 항목 안내 카드 (가입 버튼을 눌렀는데 빠진 항목이 있을 때만) */}
        {missingOpen && (
          <SignupMissingCard
            idPrefix="artist-signup"
            missing={missing}
            titleRef={missingTitle}
            onClose={() => setMissingOpen(false)}
            onGo={focusField}
            submitId="artist-signup-submit"
          />
        )}

        <button
          id="artist-signup-submit"
          className="primary full signup-submit"
          type="submit"
        >
          아티스트 가입 완료
        </button>
      </form>
    </section>
  );
}
