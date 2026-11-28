import StagePhotos from '../../../components/common/stage-photos.jsx';
import { STAGE_PHOTOS } from '../../../components/common/stage-photo-list.js';
import GlassCd from './glass-cd.jsx';
import StageLights from './stage-lights.jsx';

// 홈 뒤쪽의 무대 장면: 흐린 공연 사진(공통 StagePhotos) + 무대 조명 + 기울어진 유리 CD (NSU-179).
// 구간이 바뀌면 배경 사진이 바뀌고 CD가 자리를 옮깁니다. CD 안 사진은 CD가 스스로 10초마다 바꿉니다.
export default function StageBackdrop({ active }) {
  return (
    <StagePhotos active={active}>
      <StageLights />
      <div className="stage-cd-slot">
        <GlassCd photos={STAGE_PHOTOS} />
      </div>
    </StagePhotos>
  );
}
