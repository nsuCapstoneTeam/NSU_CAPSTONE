// 위치 미리 보기 (NSU-115): 지도 서비스 연결 전이라 실제 지도가 아닌 예시 그림에 핀과 장소 이름을 보여 줍니다.
// 실제 지도는 지도 서비스 앱 키와 주소 → 좌표 변환이 필요합니다 (팀 확정 필요).
// label: 핀 옆에 보일 이름 (장소 이름이 없으면 주소)

// 길: 바깥 선(회색) 위에 흰 선을 겹쳐 그림
const ROADS = [
  ['M0 150 C200 140 330 175 470 160 S720 120 886 132', 9],
  ['M330 30 C350 90 360 150 372 220', 8],
  ['M620 30 C600 100 560 160 540 220', 6],
  ['M0 80 C150 86 300 66 460 92 S700 100 886 76', 5],
  ['M210 220 C250 170 300 120 330 30', 4],
  ['M720 220 C730 160 760 100 800 30', 4],
];

export default function PlaceMapPreview({ label }) {
  return (
    <figure className="place-map">
      <svg
        viewBox="0 0 886 220"
        preserveAspectRatio="xMidYMid slice"
        role="img"
        aria-label={`위치 미리 보기 예시 그림: ${label}`}
      >
        <rect
          width="886"
          height="220"
          className="place-map-ground"
        />
        <path
          className="place-map-water"
          d="M0 18 C180 40 300 0 470 22 S760 40 886 14 L886 0 L0 0 Z"
        />
        <g className="place-map-park">
          <path d="M90 120 C140 90 210 100 230 140 C250 185 170 205 120 190 C80 178 60 150 90 120 Z" />
          <path d="M600 150 C640 120 720 130 735 165 C750 205 660 215 620 200 C595 190 585 168 600 150 Z" />
          <path d="M500 70 C520 58 548 62 552 80 C556 98 530 106 512 100 C498 95 492 80 500 70 Z" />
        </g>
        {ROADS.map(([d, w]) => (
          <g key={d}>
            <path
              className="place-map-road-edge"
              d={d}
              strokeWidth={w + 3}
            />
            <path
              className="place-map-road"
              d={d}
              strokeWidth={w}
            />
          </g>
        ))}
      </svg>
      {/* 핀과 이름표는 SVG 밖에 두어 지도 비율이 바뀌어도 가운데에 그대로 있게 함 */}
      <span
        className="place-map-pin"
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
        <span className="place-map-label">{label}</span>
      </span>
      <figcaption>지도 예시 그림</figcaption>
    </figure>
  );
}
