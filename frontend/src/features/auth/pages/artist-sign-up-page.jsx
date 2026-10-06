import SignUpFlow from './sign-up-flow.jsx';

// 아티스트 회원가입 화면 (Linear NSU-38, 요구사항 AUTH-005·006·008·157)
// 새 인증 정책(SSOT v1.5)에 따라 계정 만들기만 하고, 활동명·장르·지역 같은 프로필은 마이페이지에서 채웁니다.
// 실제 화면은 역할 공통 가입 화면(SignUpFlow)이 그립니다.
export default function ArtistSignUp({ profile, setProfile, navigate, notify }) {
  // 가입 성공: 이메일·휴대폰은 저장하지 않고(SEC-120) 역할과 임시 이름만 체험 프로필에 반영
  function complete(user) {
    setProfile({
      ...profile,
      role: 'artist',
      name: '새 아티스트',
      verification: user.status === 'ACTIVE' ? '기본 가입 완료' : '가입 확인 중',
    });
    notify('가입이 완료됐어요. 마이페이지에서 활동명과 장르를 채워 주세요.');
    navigate('signup-complete');
  }

  return (
    <SignUpFlow
      role="artist"
      navigate={navigate}
      notify={notify}
      onComplete={complete}
    />
  );
}
