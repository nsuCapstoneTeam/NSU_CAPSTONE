import StagePhotos from '../../../components/common/stage-photos.jsx';
import { STAGE_PHOTOS } from '../../../components/common/stage-photo-list.js';
import StageCd from './stage-cd.jsx';

// 홈 뒤쪽의 무대 장면: 흐린 공연 사진(공통 StagePhotos) + 동심원 + 도는 CD.
// 구간이 바뀌면 사진이 바뀌고 CD가 자리를 옮깁니다.
export default function StageBackdrop({ active }) {
  return (
    <StagePhotos active={active}>
      <div className="stage-cd-slot">
        <div className="stage-rings" />
        <StageCd photo={STAGE_PHOTOS[active]} />
      </div>
    </StagePhotos>
  );
}
