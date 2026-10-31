import Icon from '../../../components/common/icon.jsx';
import PlaceMapPreview from '../../my-page/components/place-map-preview.jsx';
import { regionLabel } from '../../my-page/event-place.js';

// 장소 보기 (NSU-116): 장소 이름, 주소, 지역, 위치 미리 보기(예시 그림)
// 장소 패널과 행사 상세에서 같이 씁니다. 제목(h2·h3)은 쓰는 쪽에서 둡니다.
export default function EventPlaceBlock({ event }) {
  const region = regionLabel(event.regionCode) || event.region;
  return (
    <div className="es-place-block">
      <div>
        <p className="es-venue">{event.venue}</p>
        {event.address && <p className="es-address">{event.address}</p>}
      </div>
      {region && (
        <span className="place-region-chip">
          <Icon
            name="pin"
            size={14}
          />
          지역 <strong>{region}</strong>
        </span>
      )}
      <PlaceMapPreview label={event.venue || event.address} />
      <small className="es-map-note">지도는 예시 그림이에요. 실제 위치는 주소로 확인해 주세요.</small>
    </div>
  );
}
