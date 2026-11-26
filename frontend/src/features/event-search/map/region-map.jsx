import { useEffect, useId, useRef, useState } from 'react';
import {
  SQUASH,
  dropDelays,
  nameSize,
  nextPiece,
  pieceName,
  pinSize,
  placePins,
  spreadMarkers,
  toMapPoint,
} from './map-geometry.js';
import { DISTRICTS, PROVINCES } from '../../../data/regions.js';
import { preloadMap, useMapData } from './map-loaders.js';
import './region-map.css';

// 지역 지도 (NSU-117): Figma '3D 지도' 시안을 SVG로 옮긴 2.5D 지도. three.js 없이 그립니다.
// 위아래를 납작하게 누른 평면 조각을 몇 겹 아래로 겹쳐 옆면을 만들고, 고르거나 마우스를 올린 조각은 위로 띄웁니다.
// level: 'nation'(시·도 조각) 또는 시·도 코드(시·군·구 조각)
// counts: { 조각 코드: 행사 수 }, selected: 고른 조각 코드, onSelect(code): 조각을 누름
// markers: 행사 장소 번호 핀 [{ id, n, at: [경도, 위도], code, title, when, venue }] (시·도 지도에서 목록과 함께 움직임)
//   hotMarker: 목록에서 마우스를 올린 행사, pickedMarker: 장소를 보고 있는 행사
//   onMarkerEnter(id | ''): 핀에 마우스를 올리거나 뗌, onMarker(id): 핀을 누름
const PAD = 14;
const LIFT = 10; // 떠오르는 높이(px)
const STEP = 2; // 옆면 한 겹 간격(px)
// 지도 상자 최대 높이(px). 넓이는 비율에 맞춰 줄임
const MAX_HEIGHT = { nation: 640, province: 520 };

export default function RegionMap({ level, counts, selected = '', onSelect, ...markerProps }) {
  const { data, error, retry } = useMapData(level);
  const nation = level === 'nation';

  return (
    <div className={`rm ${nation ? 'rm-nation' : 'rm-province'}`}>
      {data ? (
        <MapStage
          key={level}
          level={level}
          data={data}
          counts={counts}
          selected={selected}
          onSelect={onSelect}
          {...markerProps}
        />
      ) : (
        <div
          className="rm-wait"
          role={error ? 'alert' : 'status'}
        >
          {error ? (
            <>
              <p>지도를 불러오지 못했어요. 목록은 그대로 쓸 수 있고, 아래에서 지역을 골라도 돼요.</p>
              <button
                type="button"
                className="secondary"
                onClick={retry}
              >
                지도 다시 불러오기
              </button>
              <RegionButtons
                level={level}
                counts={counts}
                selected={selected}
                onSelect={onSelect}
              />
            </>
          ) : (
            <p>지도를 불러오는 중이에요.</p>
          )}
        </div>
      )}
    </div>
  );
}

// 지도를 못 불러왔을 때 대신 쓰는 지역 버튼 (행사가 있는 지역만)
function RegionButtons({ level, counts, selected, onSelect }) {
  const nation = level === 'nation';
  const codes = (nation ? PROVINCES : DISTRICTS.filter((d) => d.code.startsWith(level)))
    .map((r) => r.code)
    .filter((code) => (counts[code] ?? 0) > 0);
  if (!codes.length) return null;
  return (
    <div
      className="rm-fallback"
      role="group"
      aria-label={nation ? '시·도 고르기' : '시·군·구 고르기'}
    >
      {codes.map((code) => (
        <button
          key={code}
          type="button"
          className={`rm-away-chip${selected === code ? ' is-on' : ''}`}
          aria-pressed={selected === code}
          aria-label={`${pieceName(level, code)} 행사 ${counts[code]}건`}
          onClick={() => onSelect(code)}
        >
          {pieceName(level, code)} <b>{counts[code]}</b>
        </button>
      ))}
    </div>
  );
}

function MapStage({
  level,
  data,
  counts,
  selected,
  onSelect,
  markers = [],
  hotMarker = '',
  pickedMarker = '',
  onMarkerEnter = () => {},
  onMarker = () => {},
}) {
  const nation = level === 'nation';
  const depth = nation ? 10 : 8;
  const uid = useId().replace(/[^\w-]/g, '');
  const refs = useRef(new Map());
  // 마우스를 올린 조각, 키보드로 옮겨 간 조각
  const [hover, setHover] = useState('');
  const [focus, setFocus] = useState('');
  // 키보드 이동의 기준 조각 (지도 안에서 Tab으로 들어오는 곳은 이 조각 하나)
  const [cursor, setCursor] = useState('');
  // 지도 상자의 실제 넓이(px): 핀이 겹치는지 계산할 때 씀
  const stage = useRef(null);
  const [stageWidth, setStageWidth] = useState(0);
  useEffect(() => {
    const el = stage.current;
    if (!el) return undefined;
    const observer = new ResizeObserver(([entry]) => setStageWidth(entry.contentRect.width));
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  const { pieces, away = [] } = data;
  const W = data.width + PAD * 2;
  const H = data.height * SQUASH + depth + LIFT + PAD * 2;
  const delays = dropDelays(pieces);
  const has = (code) => pieces.some((p) => p.code === code);
  const firstCode = [...pieces].sort((a, b) => a.label[1] - b.label[1] || a.label[0] - b.label[0])[0]?.code;
  const tabCode = (has(cursor) && cursor) || (has(selected) && selected) || firstCode;
  const active = hover || focus;
  // 떠오른 조각: 고른 조각, 마우스로 가리킨 조각, 키보드 포커스가 있는 조각, 목록에서 가리킨 행사의 시·군·구
  // (마우스가 지도 위에 있어도 키보드 포커스 조각은 따로 떠오르고 파란 테두리가 보이게)
  const markerCode = markers.find((m) => m.id === (hotMarker || pickedMarker))?.code ?? '';
  const lifted = [selected, hover, focus, markerCode].filter((c, i, list) => c && has(c) && list.indexOf(c) === i);

  const count = (code) => counts[code] ?? 0;
  const label = (code) => `${pieceName(level, code)} 행사 ${count(code)}건`;
  const href = (code) => `#rm-${uid}-${code}`;
  const sides = (from, to) => {
    const list = [];
    for (let o = from; o <= to; o += STEP) list.push(o);
    return list;
  };

  function enter(code) {
    setHover(code);
    if (nation) preloadMap(code);
  }

  function onKeyDown(e) {
    const code = e.target.dataset?.code;
    if (!code) return;
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      onSelect(code);
      return;
    }
    const next = nextPiece(pieces, code, e.key);
    if (next === code) return;
    e.preventDefault();
    setCursor(next);
    refs.current.get(next)?.focus();
  }

  // 조각 라벨 자리를 화면 px로 (떠오른 조각은 그만큼 위로)
  const scale = stageWidth / W;
  const point = (code) => {
    const p = pieces.find((x) => x.code === code);
    const up = lifted.includes(code) ? LIFT : 0;
    return { x: (PAD + p.label[0]) * scale, y: (PAD + LIFT + p.label[1] * SQUASH - up) * scale };
  };
  // 행사 장소 번호 핀 (좌표가 없거나 지도 밖 섬이면 빼고)
  // 자리가 겹치는 핀은 지도 칸 안에서 비켜 세워 각각 누를 수 있게 함 (원래 자리에는 점)
  // 비켜 세우기는 떠오르기 전 좌표로 계산해서, 조각이 떠올라도 핀끼리의 배치는 그대로 (떠오름은 결과에만 더함)
  const spots = spreadMarkers(
    markers
      .filter((m) => m.at && data.proj && has(m.code))
      .map((m) => {
        const [mx, my] = toMapPoint(data.proj, m.at);
        return { ...m, x: (PAD + mx) * scale, y: (PAD + LIFT + my * SQUASH) * scale };
      }),
    26,
    stageWidth ? { width: stageWidth, height: H * scale } : null,
    LIFT * scale,
  ).map((m) => {
    const up = lifted.includes(m.code) ? LIFT * scale : 0;
    return { ...m, y: m.y - up, oy: m.oy - up, up };
  });
  const hotSpot = spots.find((m) => m.id === hotMarker);
  // 행사 수 핀은 번호 핀이 없을 때만. 이름만 있는 라벨은 자리가 넉넉하고 다른 핀과 겹치지 않을 때만
  const showPin = (p) => nation || (count(p.code) > 0 && !markers.length);
  const order = (p) => (p.code === active ? 3 : p.code === selected ? 2 : 0) * 1000 + count(p.code);
  const placed = stageWidth
    ? placePins([
        ...spots.map((m) => ({ code: `marker-${m.id}`, x: m.x, y: m.y, w: 26, h: 32, kind: 'fixed' })),
        ...pieces
          .filter(showPin)
          .sort((a, b) => order(b) - order(a))
          .map((p) => ({ code: p.code, ...point(p.code), ...pinSize(pieceName(level, p.code), count(p.code)), kind: 'pin' })),
        ...pieces
          .filter((p) => !showPin(p) && p.room >= 7)
          .sort((a, b) => b.room - a.room)
          .map((p) => ({ code: p.code, ...point(p.code), ...nameSize(pieceName(level, p.code)), kind: 'name' })),
      ])
    : {};

  return (
    <>
      <div
        ref={stage}
        className="rm-stage"
        style={{
          aspectRatio: `${W} / ${H}`,
          width: `min(100%, ${Math.round(((nation ? MAX_HEIGHT.nation : MAX_HEIGHT.province) * W) / H)}px)`,
        }}
      >
        <svg
          viewBox={`0 0 ${W} ${H}`}
          role="group"
          aria-label={nation ? '전국 시·도 지도' : '시·군·구 지도'}
          onKeyDown={onKeyDown}
          onPointerLeave={() => setHover('')}
        >
          <defs>
            {pieces.map((p) => (
              <path
                key={p.code}
                id={`rm-${uid}-${p.code}`}
                d={p.d}
                fillRule="evenodd"
                vectorEffect="non-scaling-stroke"
              />
            ))}
          </defs>
          <g
            transform={`translate(${PAD} ${PAD + LIFT}) scale(1 ${SQUASH})`}
            className="rm-land"
          >
            {/* 옆면: 모든 조각을 아래로 몇 겹 */}
            {sides(STEP, depth).map((o) => (
              <g
                key={o}
                transform={`translate(0 ${o / SQUASH})`}
                aria-hidden="true"
              >
                {pieces.map((p) => (
                  <use
                    key={p.code}
                    href={href(p.code)}
                    className={`rm-side rm-tone-${p.tone ?? 0} rm-drop`}
                    style={{ '--delay': `${delays[p.code]}ms` }}
                  />
                ))}
              </g>
            ))}
            {/* 윗면: 누를 수 있는 조각 */}
            {pieces.map((p) => (
              <use
                key={p.code}
                ref={(el) => {
                  if (el) refs.current.set(p.code, el);
                  else refs.current.delete(p.code);
                }}
                href={href(p.code)}
                className={`rm-top rm-tone-${p.tone ?? 0} rm-drop`}
                style={{ '--delay': `${delays[p.code]}ms` }}
                data-code={p.code}
                role="button"
                tabIndex={p.code === tabCode ? 0 : -1}
                aria-label={label(p.code)}
                aria-pressed={selected === p.code}
                onPointerEnter={() => enter(p.code)}
                onFocus={() => {
                  setFocus(p.code);
                  setCursor(p.code);
                }}
                onBlur={() => setFocus('')}
                onClick={() => onSelect(p.code)}
              />
            ))}
            {/* 떠오른 조각: 맨 마지막에 다시 그려 앞 조각에 가리지 않게 */}
            {lifted.map((code) => {
              const tone = pieces.find((p) => p.code === code).tone ?? 0;
              const picked = code === selected;
              return (
                // 떠오른 조각도 같은 지역으로 누르고 가리킬 수 있게 (키보드는 아래 원래 조각으로)
                <g
                  key={code}
                  className={`rm-lifted${picked ? ' is-picked' : ''}${code === focus ? ' is-focus' : ''}`}
                  aria-hidden="true"
                  onPointerEnter={() => enter(code)}
                  onClick={() => onSelect(code)}
                >
                  <g transform={`translate(0 ${-LIFT / SQUASH})`}>
                    {sides(STEP, depth + LIFT).map((o) => (
                      <use
                        key={o}
                        href={href(code)}
                        transform={`translate(0 ${o / SQUASH})`}
                        className={`rm-side rm-tone-${tone}`}
                      />
                    ))}
                    <use
                      href={href(code)}
                      className={`rm-top rm-tone-${tone}`}
                    />
                  </g>
                </g>
              );
            })}
          </g>
        </svg>

        <div
          className="rm-pins"
          aria-hidden="true"
        >
          {Object.entries(placed).map(([code, spot]) => {
            if (spot.hidden) return null;
            const { x, y } = point(code);
            const name = pieceName(level, code);
            if (!showPin(pieces.find((p) => p.code === code))) {
              return (
                <span
                  key={code}
                  className="rm-name"
                  style={{ left: x, top: y }}
                >
                  {name}
                </span>
              );
            }
            const on = code === selected || code === active;
            return (
              <span key={code}>
                {spot.moved && (
                  <i
                    className="rm-dot"
                    style={{ left: x, top: y }}
                  />
                )}
                <span
                  className={`rm-pin${on ? ' is-on' : ''}${spot.moved ? ' is-moved' : ''}`}
                  style={{ left: x + spot.dx, top: y + spot.dy }}
                >
                  {name} <b>{count(code)}</b>
                </span>
              </span>
            );
          })}
        </div>

        <div className="rm-markers">
          {spots
            .filter((m) => m.moved)
            .map((m) => (
              <i
                key={`dot-${m.id}`}
                className="rm-dot"
                style={{ left: m.ox, top: m.oy }}
                aria-hidden="true"
              />
            ))}
          {spots.map((m) => (
            <button
              key={m.id}
              type="button"
              tabIndex={-1}
              aria-hidden="true"
              className={`rm-marker${m.id === hotMarker ? ' is-hot' : ''}${m.id === pickedMarker ? ' is-picked' : ''}${m.moved ? ' is-moved' : ''}${m.up ? ' is-up' : ''}`}
              style={{ left: m.x, top: m.y, '--lift': `${m.up}px` }}
              onPointerEnter={() => onMarkerEnter(m.id)}
              onPointerLeave={() => onMarkerEnter('')}
              onClick={() => onMarker(m.id)}
            >
              {m.n}
            </button>
          ))}
        </div>

        {hotSpot && (
          <div
            className={`rm-tip is-marker${hotSpot.x > stageWidth * 0.6 ? ' is-left' : ''}`}
            style={{ left: hotSpot.x, top: hotSpot.y - 10 }}
            aria-hidden="true"
          >
            <strong>{hotSpot.title}</strong>
            <span>{hotSpot.when}</span>
            <span>{hotSpot.venue}</span>
            <small>{hotSpot.id === pickedMarker ? '지금 보고 있는 장소예요' : '누르면 장소를 보여 줘요'}</small>
          </div>
        )}

        {!hotSpot && active && has(active) && (
          <div
            className={`rm-tip${pieces.find((p) => p.code === active).label[0] > data.width * 0.6 ? ' is-left' : ''}`}
            style={{ left: point(active).x, top: point(active).y }}
            aria-hidden="true"
          >
            <strong>{pieceName(level, active)}</strong>
            <span>공개 행사 {count(active)}건</span>
            <small>
              {nation
                ? '누르면 시·군·구로 들어가요'
                : active === selected
                  ? '다시 누르면 고른 지역을 풀어요'
                  : `누르면 목록이 ${pieceName(level, active)} 행사로 바뀌어요`}
            </small>
          </div>
        )}
      </div>

      {away.length > 0 && (
        <div className="rm-away">
          <span>지도에 다 담지 못한 섬 지역</span>
          {away.map((code) => (
            <button
              key={code}
              type="button"
              className={`rm-away-chip${selected === code ? ' is-on' : ''}`}
              aria-pressed={selected === code}
              aria-label={label(code)}
              onClick={() => onSelect(code)}
            >
              {pieceName(level, code)} <b>{count(code)}</b>
            </button>
          ))}
        </div>
      )}
    </>
  );
}
