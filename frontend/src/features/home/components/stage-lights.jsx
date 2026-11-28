// 홈 첫 구간 위쪽의 무대 조명 5개 (NSU-179, Figma 'B · 3D 유리 CD'의 무대 조명)
// 조명기구에서 아래로 퍼지는 빛줄기. 위치는 1440px 화면 기준 비율(%)로 정합니다.
// top: 조명기구 쪽 좁은 끝 [왼쪽, 오른쪽], bottom: 바닥 쪽 넓은 끝 [왼쪽, 오른쪽]
// 움직이지 않는 장식이라 움직임 줄이기 설정과 관계없이 그대로 보입니다.
const px = (v) => `${((v / 1440) * 100).toFixed(2)}%`;
const LIGHTS = [
  { color: 'sky', top: [128, 160], bottom: [18, 476] },
  { color: 'white', top: [430, 462], bottom: [82, 538] },
  { color: 'pink', top: [762, 794], bottom: [722, 1175] },
  { color: 'sky', top: [1108, 1139], bottom: [743, 1197] },
  { color: 'white', top: [1324, 1356], bottom: [1196, 1654] },
];

export default function StageLights() {
  return (
    <div className="stage-lights">
      {LIGHTS.map((light, index) => (
        <div
          key={index}
          className={`stage-light is-${light.color}`}
          style={{
            '--lamp': px((light.top[0] + light.top[1]) / 2),
            '--t1': px(light.top[0]),
            '--t2': px(light.top[1]),
            '--b1': px(light.bottom[0]),
            '--b2': px(light.bottom[1]),
          }}
        >
          <span className="stage-light-beam" />
          <span className="stage-light-lamp" />
        </div>
      ))}
    </div>
  );
}
