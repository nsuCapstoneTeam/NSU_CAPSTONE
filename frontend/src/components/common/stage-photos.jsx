import { STAGE_PHOTOS } from './stage-photo-list.js';
import './stage-scope.css';

// 화면 뒤에 붙어 있는 무대 배경: 흐린 공연 사진 + 그라데이션.
// active: 보여 줄 사진 번호 (바뀌면 천천히 겹쳐 바뀜), children: 사진 위에 얹을 장식(CD 등)
// .stage-scope 영역 안에 두어야 색 변수가 적용됩니다. 장식이므로 화면 낭독기에서는 숨깁니다.
export default function StagePhotos({ active = 0, children }) {
  return (
    <div
      className="stage-backdrop"
      aria-hidden="true"
    >
      {STAGE_PHOTOS.map((src, index) => (
        <div
          key={src}
          className={`stage-photo${index === active ? ' is-on' : ''}`}
          style={{ backgroundImage: `url(${src})` }}
        />
      ))}
      <div className="stage-shade" />
      {children}
    </div>
  );
}
