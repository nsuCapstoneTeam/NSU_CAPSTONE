import SoundWave from './sound-wave.jsx';
import './page-loading.css';

// 화면 코드를 불러오는 동안 보여 주는 표시 (NSU-182)
// 화면별로 나눠 불러오기 때문에 처음 여는 화면은 잠깐 코드를 받아 옵니다.
export default function PageLoading() {
  return (
    <div
      className="page-loading"
      role="status"
    >
      <SoundWave />
      <span>화면을 불러오는 중…</span>
    </div>
  );
}
