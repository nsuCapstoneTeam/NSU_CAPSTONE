import { regionLabel } from '../../my-page/event-place.js';
import { venuePoint } from '../event-search.js';
import { SQUASH, pathBounds, pieceName, toMapPoint, zoomBox } from './map-geometry.js';
import { useMapData } from './map-loaders.js';
import '../../my-page/components/place-preview.css';
import './region-map.css';

// 장소 작은 지도 (NSU-117): 행사의 시·군·구를 크게 잘라 2.5D로 보여 주고, 공연장 좌표에 핀을 찍습니다.
// 장소 보기와 행사 상세에서 '예시 그림' 대신 씁니다. 좌표가 없으면 핀 없이 시·군·구만 보여 줍니다.
// 이웃 시·군·구도 흐리게 함께 그려서 어디쯤인지 알 수 있게 합니다.
const ASPECT = 16 / 7; // 지도 상자 가로 : 세로
const DEPTH = 8; // 옆면 두께(화면 px 기준 어림)
const ASSUMED_WIDTH = 560; // 옆면 두께를 계산할 때 어림한 지도 상자 넓이(px)

export default function PlaceMiniMap({ event }) {
  const code = String(event.regionCode ?? '');
  const { data, error } = useMapData(code.slice(0, 2) || 'nation');
  const piece = data?.pieces.find((p) => p.code === code);
  const venue = event.venue || event.address;

  if (!data || !piece) {
    return (
      <div className="rm rm-mini rm-mini-wait">
        <p>
          {!data && !error
            ? '지도를 불러오는 중이에요.'
            : data
              ? '이 지역은 지도에 담지 못한 섬 지역이라 지도를 보여 주지 않아요.'
              : '지도를 불러오지 못했어요.'}
        </p>
      </div>
    );
  }

  const at = venuePoint(event);
  const pin = at ? toMapPoint(data.proj, at) : null;
  // 시·군·구(와 핀)가 넉넉히 들어오는 화면 범위 (납작하게 누른 뒤 좌표)
  const box = zoomBox(pathBounds(piece.d, SQUASH), ASPECT, pin && [pin[0], pin[1] * SQUASH]);
  // 확대한 만큼 옆면을 얇게 해서 화면에서는 늘 비슷한 두께로 보이게 함
  const unit = box.w / ASSUMED_WIDTH;
  const depth = DEPTH * unit;
  const steps = [1, 2, 3, 4].map((k) => (depth * k) / 4);
  const pct = ([x, y]) => ({
    left: `${((x - box.x) / box.w) * 100}%`,
    top: `${((y * SQUASH - box.y) / box.h) * 100}%`,
  });
  const area = regionLabel(code) || pieceName(code.slice(0, 2), code);

  return (
    <figure className="rm rm-mini">
      <svg
        viewBox={`${box.x} ${box.y} ${box.w} ${box.h}`}
        role="img"
        aria-label={at ? `${area} 지도, ${venue} 자리에 핀` : `${area} 지도 (공연장 위치는 아직 없음)`}
      >
        <defs>
          {data.pieces.map((p) => (
            <path
              key={p.code}
              id={`rm-mini-${p.code}`}
              d={p.d}
              fillRule="evenodd"
              vectorEffect="non-scaling-stroke"
            />
          ))}
        </defs>
        <g transform={`scale(1 ${SQUASH})`}>
          {steps.map((o) => (
            <g
              key={o}
              transform={`translate(0 ${o / SQUASH})`}
            >
              {data.pieces.map((p) => (
                <use
                  key={p.code}
                  href={`#rm-mini-${p.code}`}
                  className={`rm-side rm-tone-${p.tone ?? 0}${p.code === code ? ' is-here' : ' is-near'}`}
                />
              ))}
            </g>
          ))}
          {data.pieces.map((p) => (
            <use
              key={p.code}
              href={`#rm-mini-${p.code}`}
              className={`rm-top rm-tone-${p.tone ?? 0}${p.code === code ? ' is-here' : ' is-near'}`}
            />
          ))}
        </g>
      </svg>
      {!pin && (
        <span
          className="rm-mini-area"
          style={pct(piece.label)}
          aria-hidden="true"
        >
          {pieceName(code.slice(0, 2), code)}
        </span>
      )}
      {pin && (
        <span
          className="place-map-pin rm-mini-pin"
          style={pct(pin)}
          aria-hidden="true"
        >
          <svg
            width="30"
            height="38"
            viewBox="0 0 30 38"
          >
            <path d="M15 37s13-11.3 13-21.5A13 13 0 0 0 2 15.5C2 25.7 15 37 15 37Z" />
            <circle
              cx="15"
              cy="15"
              r="5"
            />
          </svg>
          <span className="place-map-label">{venue}</span>
        </span>
      )}
      <figcaption>지도 경계: 통계청 SGIS, vuski/admdongkor (CC BY 4.0)</figcaption>
    </figure>
  );
}
