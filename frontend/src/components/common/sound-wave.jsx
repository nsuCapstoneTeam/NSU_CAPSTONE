import './sound-wave.css';

// 소리 막대 7개 (로딩 표시, NSU-125). 막대 높이는 가운데를 비운 두 봉우리 모양
// 움직임 줄이기 설정이면 멈춘 모양으로 보입니다. 화면낭독기에는 숨기고, 상태는 버튼 글자로 알립니다.
const BARS = [0.36, 1, 0.52, 0.26, 0.52, 1, 0.36];

export default function SoundWave({ className = '' }) {
  return (
    <span className={`sound-wave ${className}`} aria-hidden="true">
      {BARS.map((h, i) => (
        <span key={i} style={{ '--h': h, '--i': i }} />
      ))}
    </span>
  );
}
