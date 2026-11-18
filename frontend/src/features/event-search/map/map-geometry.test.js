import test from 'node:test';
import assert from 'node:assert/strict';
import { DISTRICTS, PROVINCES } from '../../../data/regions.js';
import { DEMO_PUBLIC_EVENTS } from '../event-search-data.js';
import { venuePoint } from '../event-search.js';
import {
  dropDelays,
  labelPoint,
  nextPiece,
  pathBounds,
  pieceName,
  placePins,
  spreadMarkers,
  toMapPoint,
  zoomBox,
} from './map-geometry.js';

const levels = ['nation', ...PROVINCES.map((p) => p.code)];
const load = async (level) => (await import(`./map-data/${level}.js`)).default;

test('지도 데이터: 전국은 시·도 16곳, 시·도마다 시·군·구가 빠짐없이 한 번씩 있다', async () => {
  for (const level of levels) {
    const data = await load(level);
    const codes = [...data.pieces.map((p) => p.code), ...data.away];
    const expected =
      level === 'nation'
        ? PROVINCES.map((p) => p.code)
        : DISTRICTS.filter((d) => d.code.startsWith(level)).map((d) => d.code);
    assert.deepEqual([...codes].sort(), [...expected].sort(), level);
  }
});

// SVG 경로 'M x,y x,y ... Z'의 고리 안에 점이 있는지 (구멍은 홀수·짝수로)
const insidePath = (d, [x, y]) => {
  let hits = 0;
  for (const part of d.split('Z').filter(Boolean)) {
    const ring = part.slice(1).trim().split(' ').map((q) => q.split(',').map(Number));
    let c = false;
    for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
      const [xi, yi] = ring[i];
      const [xj, yj] = ring[j];
      if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) c = !c;
    }
    if (c) hits += 1;
  }
  return hits % 2 === 1;
};

test('지도 데이터: 조각마다 경로·라벨·색 번호가 있고 라벨이 칠해진 곳(구멍 밖)에 있다', async () => {
  for (const level of levels) {
    const data = await load(level);
    assert.ok(data.width > 0 && data.width <= 600 && data.height > 0 && data.height <= 460, level);
    for (const p of data.pieces) {
      assert.match(p.d, /^M[\d.]+,[\d.]+ .*Z$/, `${level} ${p.code}`);
      const [x, y] = p.label;
      assert.ok(x >= 0 && x <= data.width && y >= 0 && y <= data.height, `${level} ${p.code}`);
      assert.ok(insidePath(p.d, p.label), `${level} ${p.code} 라벨이 조각 밖이나 구멍 안`);
      assert.ok(Number.isInteger(p.tone) && p.tone >= 0 && p.tone <= 4, `${level} ${p.code}`);
      assert.ok(pieceName(level, p.code), `${level} ${p.code}`);
    }
  }
});

test('지도 밖 섬 지역은 인천 옹진군과 경북 울릉군', async () => {
  assert.deepEqual((await load('28')).away, ['28720']);
  assert.deepEqual((await load('47')).away, ['47940']);
  assert.equal(pieceName('28', '28720'), '옹진군');
});

const sample = [
  { code: 'a', label: [50, 50] },
  { code: 'b', label: [100, 50] },
  { code: 'c', label: [50, 100] },
  { code: 'd', label: [0, 50] },
  { code: 'e', label: [50, 0] },
];

test('나타나는 순서는 가운데부터 바깥으로', () => {
  const delays = dropDelays(sample, 400);
  assert.equal(delays.a, 0);
  assert.equal(Math.max(...Object.values(delays)), 400);
  assert.ok(delays.b > delays.a);
  assert.deepEqual(dropDelays([]), {});
});

test('화살표로 그 방향의 가까운 조각으로 옮겨 가고, 없으면 그대로', () => {
  assert.equal(nextPiece(sample, 'a', 'ArrowRight'), 'b');
  assert.equal(nextPiece(sample, 'a', 'ArrowLeft'), 'd');
  assert.equal(nextPiece(sample, 'a', 'ArrowDown'), 'c');
  assert.equal(nextPiece(sample, 'a', 'ArrowUp'), 'e');
  assert.equal(nextPiece(sample, 'e', 'ArrowUp'), 'e');
  assert.equal(nextPiece(sample, 'a', 'Tab'), 'a');
});

test('겹치는 핀은 비켜 서고, 핀과 겹치는 이름은 숨김', () => {
  const pin = (code, x, y) => ({ code, x, y, w: 60, h: 24, kind: 'pin' });
  const spots = placePins([
    { code: 'marker', x: 300, y: 400, w: 26, h: 32, kind: 'fixed' },
    pin('under-marker', 300, 400),
    pin('first', 100, 100),
    pin('near', 110, 102),
    pin('far', 400, 400),
    { code: 'name', x: 100, y: 90, w: 40, h: 16, kind: 'name' },
    { code: 'free', x: 300, y: 200, w: 40, h: 16, kind: 'name' },
  ]);
  assert.equal(spots.marker, undefined);
  assert.equal(spots['under-marker'].moved, true);
  assert.equal(spots.first.moved, false);
  assert.equal(spots.near.moved, true);
  assert.equal(spots.far.moved, false);
  assert.equal(spots.name.hidden, true);
  assert.equal(spots.free.hidden, false);
});

test('겹치는 번호 핀은 옆으로 비켜 서고 원래 자리를 남긴다', () => {
  // 서울 예시 행사 두 곳처럼 8px만 떨어진 핀, 떨어져 있는 핀
  const out = spreadMarkers(
    [
      { id: 'a', x: 100, y: 100 },
      { id: 'b', x: 108, y: 102 },
      { id: 'c', x: 300, y: 100 },
    ],
    26,
  );
  assert.equal(out[0].moved, false);
  assert.equal(out[1].moved, true);
  assert.ok(Math.abs(out[1].x - out[0].x) >= 26, '비켜 선 핀이 첫 핀과 겹침');
  assert.deepEqual([out[1].ox, out[1].oy], [108, 102]);
  assert.equal(out[2].moved, false);
});

test('라벨 자리: 격자점이 모두 구멍 안이어도 칠해진 곳을 고른다', () => {
  // 100×100 바깥 고리 안에 96×96 구멍: 24칸 격자점은 모두 구멍 안 (리뷰 예시)
  const outer = [
    [0, 0],
    [100, 0],
    [100, 100],
    [0, 100],
  ];
  const hole = [
    [2, 2],
    [2, 98],
    [98, 98],
    [98, 2],
  ];
  const d = [outer, hole].map((r) => `M${r.map((q) => q.join(',')).join(' ')}Z`).join('');
  const { point } = labelPoint(outer, [outer, hole]);
  assert.ok(insidePath(d, point), `구멍 안이나 밖: ${point}`);
  // 격자 사이로도 빠지는 아주 얇은 띠 (두께 0.2)
  const thinHole = [
    [0.1, 0.1],
    [0.1, 99.9],
    [99.9, 99.9],
    [99.9, 0.1],
  ];
  const thin = labelPoint(outer, [outer, thinHole]);
  const thinD = [outer, thinHole].map((r) => `M${r.map((q) => q.join(',')).join(' ')}Z`).join('');
  assert.ok(insidePath(thinD, thin.point), `얇은 띠에서 구멍 안이나 밖: ${thin.point}`);
  // 구멍이 없으면 가운데 근처
  const plain = labelPoint(outer).point;
  assert.ok(Math.abs(plain[0] - 50) < 10 && Math.abs(plain[1] - 50) < 10);
});

test('예시 행사의 핀 자리는 그 행사의 시·군·구 조각 안에 있다', async () => {
  for (const e of DEMO_PUBLIC_EVENTS) {
    const at = venuePoint(e);
    if (!at) continue; // 좌표가 없는 행사(demo-event-6)는 핀을 찍지 않음
    const data = await load(e.regionCode.slice(0, 2));
    const piece = data.pieces.find((p) => p.code === e.regionCode);
    assert.ok(insidePath(piece.d, toMapPoint(data.proj, at)), `${e.id} ${e.regionCode}`);
  }
});

test('경도·위도를 지도 좌표로 바꾼다', () => {
  const [x, y] = toMapPoint([100, -38, 10], [126, 37]);
  assert.ok(Math.abs(x - (126 * Math.cos(Math.PI / 5) - 100) * 10) < 1e-9);
  assert.equal(y, 10);
});

test('작은 지도: 경로 범위를 구하고, 핀까지 들어오는 가로로 긴 상자로 넓힌다', () => {
  const b = pathBounds('M10,20 30,20 30,60Z M40,10 41,11Z', 0.5);
  assert.deepEqual(b, { x0: 10, y0: 5, x1: 41, y1: 30 });
  const box = zoomBox({ x0: 0, y0: 0, x1: 100, y1: 20 }, 2, [150, 10]);
  assert.ok(Math.abs(box.w / box.h - 2) < 0.01);
  assert.ok(box.x <= 0 && box.x + box.w >= 150);
  const tiny = zoomBox({ x0: 5, y0: 5, x1: 6, y1: 6 }, 2);
  assert.equal(tiny.w, 60);
});
