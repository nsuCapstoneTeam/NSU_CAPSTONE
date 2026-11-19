// 지도 계산 도우미 (NSU-117): 화면 없이 값만 다루는 순수 함수
// 실행 테스트: node --test src/features/event-search/map/map-geometry.test.js
import { DISTRICTS, PROVINCES } from '../../../data/regions.js';

// 위아래를 납작하게 눌러 비스듬히 내려다보는 느낌을 냄 (1이면 평면)
export const SQUASH = 0.68;

// 조각 이름: 전국 지도는 '서울', 시·도 지도는 '마포구'
export function pieceName(level, code) {
  if (level === 'nation') return PROVINCES.find((p) => p.code === code)?.short ?? '';
  return DISTRICTS.find((d) => d.code === code)?.name ?? '';
}

// 나타나는 순서: 가운데 조각부터 바깥으로. 조각마다 지연(ms)을 { code: ms }로
export function dropDelays(pieces, span = 420) {
  if (!pieces.length) return {};
  const xs = pieces.map((p) => p.label[0]);
  const ys = pieces.map((p) => p.label[1]);
  const cx = (Math.min(...xs) + Math.max(...xs)) / 2;
  const cy = (Math.min(...ys) + Math.max(...ys)) / 2;
  const far = Math.max(1, ...pieces.map((p) => Math.hypot(p.label[0] - cx, p.label[1] - cy)));
  return Object.fromEntries(
    pieces.map((p) => [p.code, Math.round((Math.hypot(p.label[0] - cx, p.label[1] - cy) / far) * span)]),
  );
}

const DIRECTIONS = {
  ArrowRight: [1, 0],
  ArrowLeft: [-1, 0],
  ArrowDown: [0, 1],
  ArrowUp: [0, -1],
};

// 키보드 화살표로 옮겨 갈 조각: 그 방향에 있는 조각 중 가깝고 방향이 잘 맞는 것. 없으면 지금 조각
export function nextPiece(pieces, fromCode, key) {
  const dir = DIRECTIONS[key];
  const from = pieces.find((p) => p.code === fromCode);
  if (!dir || !from) return fromCode;
  let best = fromCode;
  let bestScore = Infinity;
  for (const p of pieces) {
    if (p.code === fromCode) continue;
    const dx = p.label[0] - from.label[0];
    const dy = (p.label[1] - from.label[1]) * SQUASH;
    const along = dx * dir[0] + dy * dir[1];
    if (along <= 0) continue;
    const across = Math.abs(dx * dir[1] - dy * dir[0]);
    const score = along + across * 2;
    if (score < bestScore) {
      bestScore = score;
      best = p.code;
    }
  }
  return best;
}

// 핀·이름 크기 어림(px): 12px 글자 기준, 한글 한 자 12px, 숫자 한 자 8px
export const pinSize = (name, count) => ({ w: name.length * 12 + String(count).length * 8 + 26, h: 26 });
export const nameSize = (name) => ({ w: name.length * 12 + 4, h: 16 });

const overlap = (a, b) => a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;

// 핀이 서로 겹치지 않게 자리 잡기 (화면 px 기준)
// items: [{ code, x, y, w, h, kind: 'fixed' | 'pin' | 'name' }], 앞에 있는 것부터 먼저 자리를 차지함
// fixed(행사 장소 핀)는 움직이지 않고 먼저 자리를 차지함
// 핀은 기준점 바로 위가 막히면 아래·왼쪽·오른쪽 순으로 비켜 서고(moved), 이름은 겹치면 숨김(hidden)
// 결과: { code: { dx, dy, moved, hidden } } (fixed는 결과에 넣지 않음)
export function placePins(items) {
  const taken = items
    .filter((i) => i.kind === 'fixed')
    .map((it) => ({ x: it.x - it.w / 2, y: it.y - it.h, w: it.w, h: it.h }));
  const result = {};
  const pins = items.filter((i) => i.kind === 'pin');
  const names = items.filter((i) => i.kind === 'name');
  for (const it of pins) {
    const tries = [
      [0, 0],
      [0, it.h + 14],
      [-(it.w / 2 + 10), it.h / 2 + 4],
      [it.w / 2 + 10, it.h / 2 + 4],
      [0, -(it.h + 4)],
    ];
    const rect = ([dx, dy]) => ({ x: it.x + dx - it.w / 2, y: it.y + dy - 4 - it.h, w: it.w, h: it.h });
    const pick = tries.find((t) => !taken.some((r) => overlap(r, rect(t)))) ?? tries[0];
    taken.push(rect(pick));
    result[it.code] = { dx: pick[0], dy: pick[1], moved: pick[0] !== 0 || pick[1] !== 0, hidden: false };
  }
  for (const it of names) {
    const r = { x: it.x - it.w / 2, y: it.y - it.h / 2, w: it.w, h: it.h };
    const hidden = taken.some((t) => overlap(t, r));
    if (!hidden) taken.push(r);
    result[it.code] = { dx: 0, dy: 0, moved: false, hidden };
  }
  return result;
}

// 번호 핀끼리 겹치면 비켜 세움 (원래 자리에는 점을 남김). 화면 px 기준
// spots: [{ x, y, ... }] 앞에 있는 핀(목록 번호가 빠른 핀)이 먼저 자리를 차지함, size: 핀 지름
// 같은 줄의 옆자리 7곳(가운데, 오른쪽, 왼쪽 순)을 먼저 보고, 모두 차면 한 줄 위, 한 줄 아래, 두 줄 위… 로 넓혀 감
// 핀 수만큼만 자리를 차지하므로 언젠가 빈자리가 나와서 모든 핀이 서로 겹치지 않음
// 결과: 같은 순서로 [{ ...spot, x, y: 비켜 선 자리, ox, oy: 원래 자리, moved }]
export function spreadMarkers(spots, size = 26) {
  const placed = [];
  const step = size + 2;
  const cols = [0, 1, -1, 2, -2, 3, -3];
  return spots.map((m) => {
    const free = ([dx, dy]) =>
      !placed.some((p) => Math.abs(p.x - (m.x + dx)) < size && Math.abs(p.y - (m.y + dy)) < size);
    let spot;
    for (let ring = 0; !spot; ring += 1) {
      for (const row of ring ? [-ring, ring] : [0]) {
        spot = cols.map((c) => [c * step, row * step]).find(free);
        if (spot) break;
      }
    }
    const [dx, dy] = spot;
    placed.push({ x: m.x + dx, y: m.y + dy });
    return { ...m, x: m.x + dx, y: m.y + dy, ox: m.x, oy: m.y, moved: dx !== 0 || dy !== 0 };
  });
}

// 경도·위도를 지도 좌표로: proj는 지도 데이터의 [minX, minY, s] (scripts/build-region-map.mjs)
const COS36 = Math.cos((36 * Math.PI) / 180);
export function toMapPoint(proj, [lng, lat]) {
  const [minX, minY, s] = proj;
  return [(lng * COS36 - minX) * s, (-lat - minY) * s];
}

// 경로 'M x,y x,y ... Z'의 범위 { x0, y0, x1, y1 }. squash를 주면 y에 곱함 (납작하게 누른 뒤 좌표)
export function pathBounds(d, squash = 1) {
  const n = (d.match(/-?\d+(?:\.\d+)?/g) ?? []).map(Number);
  const b = { x0: Infinity, y0: Infinity, x1: -Infinity, y1: -Infinity };
  for (let i = 0; i + 1 < n.length; i += 2) {
    b.x0 = Math.min(b.x0, n[i]);
    b.x1 = Math.max(b.x1, n[i]);
    b.y0 = Math.min(b.y0, n[i + 1] * squash);
    b.y1 = Math.max(b.y1, n[i + 1] * squash);
  }
  return b;
}

// 범위를 가로:세로 aspect인 상자로 넓힘 (가장자리 여유 35%, 너무 작은 곳도 최소 넓이 60)
// extra: 함께 들어와야 하는 점 [x, y] (공연장 핀). 결과 { x, y, w, h }
export function zoomBox(bounds, aspect, extra = null) {
  const b = { ...bounds };
  if (extra) {
    b.x0 = Math.min(b.x0, extra[0]);
    b.x1 = Math.max(b.x1, extra[0]);
    b.y0 = Math.min(b.y0, extra[1]);
    b.y1 = Math.max(b.y1, extra[1]);
  }
  const pad = 1.7;
  const w = Math.max((b.x1 - b.x0) * pad, (b.y1 - b.y0) * pad * aspect, 60);
  const h = w / aspect;
  const round = (v) => Math.round(v * 100) / 100;
  return {
    x: round((b.x0 + b.x1) / 2 - w / 2),
    y: round((b.y0 + b.y1) / 2 - h / 2),
    w: round(w),
    h: round(h),
  };
}

// 라벨·핀 자리 (지도 데이터를 만들 때 scripts/build-region-map.mjs가 씀)
// 가장 큰 고리(ring) 범위 안에서, 칠해진 곳(rings 전체를 짝홀 규칙으로: 구멍은 빠짐)이면서 테두리와 가장 먼 점
// rings에는 구멍도 들어 있어 구멍 안의 점은 고르지 않고, 구멍 테두리까지 거리도 셈
// 격자(24칸)에 칠해진 점이 없으면 더 촘촘한 격자(96칸), 그래도 없으면 변 가운데에서 안쪽으로 조금 들어간 점
// 결과: { point: [x, y], room: 테두리까지 거리 }
export function labelPoint(ring, rings = [ring]) {
  const inside = (x, y) => {
    let c = false;
    for (const r of rings) {
      for (let i = 0, j = r.length - 1; i < r.length; j = i++) {
        const [xi, yi] = r[i];
        const [xj, yj] = r[j];
        if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) c = !c;
      }
    }
    return c;
  };
  const segDist = (px, py, [ax, ay], [bx, by]) => {
    const dx = bx - ax;
    const dy = by - ay;
    const l2 = dx * dx + dy * dy;
    const t = l2 ? Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / l2)) : 0;
    return Math.hypot(px - ax - t * dx, py - ay - t * dy);
  };
  const edgeDistance = (x, y) => {
    let m = Infinity;
    for (const r of rings) for (let q = 0; q < r.length; q++) m = Math.min(m, segDist(x, y, r[q], r[(q + 1) % r.length]));
    return m;
  };
  const xs = ring.map((p) => p[0]);
  const ys = ring.map((p) => p[1]);
  const [x0, x1, y0, y1] = [Math.min(...xs), Math.max(...xs), Math.min(...ys), Math.max(...ys)];

  // 격자 n칸으로 찾기. 가운데에 가까운 쪽을 조금 더 좋게
  const search = (n) => {
    let found = null;
    let bestScore = -Infinity;
    for (let i = 1; i < n; i++) {
      for (let j = 1; j < n; j++) {
        const x = x0 + ((x1 - x0) * i) / n;
        const y = y0 + ((y1 - y0) * j) / n;
        if (!inside(x, y)) continue;
        const m = edgeDistance(x, y);
        const score = m - 0.06 * Math.hypot(x - (x0 + x1) / 2, (y - (y0 + y1) / 2) * 1.4);
        if (score > bestScore) {
          bestScore = score;
          found = { point: [x, y], room: m };
        }
      }
    }
    return found;
  };
  const byGrid = search(24) ?? search(96);
  if (byGrid) return byGrid;

  // 아주 얇은 띠 모양: 변 가운데에서 양쪽으로 조금씩 들어가 보며 칠해진 점을 찾음
  const size = Math.max(x1 - x0, y1 - y0) || 1;
  for (const r of rings) {
    for (let q = 0; q < r.length; q++) {
      const [ax, ay] = r[q];
      const [bx, by] = r[(q + 1) % r.length];
      const len = Math.hypot(bx - ax, by - ay);
      if (!len) continue;
      const [mx, my] = [(ax + bx) / 2, (ay + by) / 2];
      const [nx, ny] = [-(by - ay) / len, (bx - ax) / len];
      for (const k of [1e-3, 1e-4]) {
        for (const side of [1, -1]) {
          const x = mx + nx * size * k * side;
          const y = my + ny * size * k * side;
          if (inside(x, y)) return { point: [x, y], room: edgeDistance(x, y) };
        }
      }
    }
  }
  return { point: ring[0], room: 0 };
}
