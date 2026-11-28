import './glass-cd.css';

// 홈 무대의 기울어진 유리 CD (NSU-179, Figma 'B · 3D 유리 CD')
// - CSS 3D(perspective + rotate)로 기울이고, 테두리를 여러 겹 뒤로 밀어 두께를 만듭니다.
// - 면: 무대 사진(돎) 위에 반투명 유리, 무지갯빛, 빛 반사를 겹칩니다. 빛은 제자리라 CD만 도는 것처럼 보입니다.
// - 사진 4장이 40초에 한 바퀴: 8초 천천히 돌다가 2초 빠르게 돌며 다음 사진으로 겹쳐 바뀜 (CSS 애니메이션만 사용)
// - 움직임 줄이기 설정이면 회전과 교체를 멈추고 첫 사진만 보여 줍니다.
// 장식이므로 화면 낭독기에서는 숨깁니다(부모 StagePhotos가 aria-hidden).
const EDGE_DEPTHS = [-12, -10, -8, -6, -4, -2];

export default function GlassCd({ photos }) {
  return (
    <div className="glass-cd-stage">
      <span className="glass-cd-shadow" />
      <span className="glass-cd-caustic" />
      <div className="glass-cd">
        {EDGE_DEPTHS.map((z) => (
          <span
            key={z}
            className="glass-cd-edge"
            style={{ '--z': `${z}px` }}
          />
        ))}
        <div className="glass-cd-face">
          <div className="glass-cd-spin">
            {photos.map((src, index) => (
              <span
                key={src}
                className="glass-cd-photo"
                style={{ backgroundImage: `url(${src})`, '--delay': `${-((photos.length - index) % photos.length) * 10}s` }}
              />
            ))}
            <span className="glass-cd-label" />
            <span className="glass-cd-hub" />
          </div>
          <span className="glass-cd-body" />
          <span className="glass-cd-sheen" />
          <span className="glass-cd-glint" />
          <span className="glass-cd-rim" />
        </div>
      </div>
    </div>
  );
}
