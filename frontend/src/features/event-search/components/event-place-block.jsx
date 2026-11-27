import Icon from '../../../components/common/icon.jsx';
import { regionLabel } from '../../my-page/event-place.js';
import PlaceMiniMap from '../map/place-mini-map.jsx';

// 장소 보기 (NSU-116): 장소 이름, 주소, 지역, 위치 지도
// 위치 지도(NSU-117): 예시 그림 대신 그 시·군·구 작은 지도에 공연장 핀 (좌표가 없으면 핀 없이)
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
      {/* 지도 아래 안내(핀이 대략적인 자리인지 등)는 지도가 실제로 그린 것에 맞춰 PlaceMiniMap이 붙임 */}
      <PlaceMiniMap event={event} />
    </div>
  );
}
