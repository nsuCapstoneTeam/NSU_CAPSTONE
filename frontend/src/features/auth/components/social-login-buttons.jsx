// 간편 로그인 (카카오·네이버·구글) 원형 버튼 묶음. 아직 연결 전이라 누르면 onSelect(표시 이름)만 호출합니다.
const PROVIDERS = [
  ['kakao', '카카오'],
  ['naver', '네이버'],
  ['google', '구글'],
];

// 구분선 '간편 로그인' + 원형 버튼 3개. onSelect(표시 이름): 버튼을 누르면 호출 (예: '카카오')
export default function SocialLoginButtons({ onSelect }) {
  return (
    <>
      <div className="cd-label-divider">
        <span>간편 로그인</span>
      </div>
      <div
        className="cd-social"
        role="group"
        aria-label="간편 로그인 방법"
      >
        {PROVIDERS.map(([key, name]) => (
          <button
            type="button"
            key={key}
            className={`cd-social-button ${key}`}
            aria-label={`${name}로 로그인`}
            title={`${name}로 로그인`}
            onClick={() => onSelect(name)}
          >
            <span aria-hidden="true">
              {key === 'kakao' ? (
                <svg viewBox="0 0 32 32">
                  <path
                    fill="currentColor"
                    d="M16 5C8.8 5 3 9.3 3 14.6c0 3.4 2.4 6.4 6 8.1L7.6 28l6-3.9c.8.1 1.6.2 2.4.2 7.2 0 13-4.3 13-9.7S23.2 5 16 5Z"
                  />
                </svg>
              ) : key === 'naver' ? (
                'N'
              ) : (
                'G'
              )}
            </span>
          </button>
        ))}
      </div>
    </>
  );
}
