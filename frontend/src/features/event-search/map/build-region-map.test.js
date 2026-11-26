// 지도 데이터 만들기 스크립트(scripts/build-region-map.mjs)의 도우미 테스트
// 실행: node --test src/features/event-search/map/build-region-map.test.js
// (스크립트는 직접 실행할 때만 지도 데이터를 만들고, 불러오기만 하면 아무것도 쓰지 않음)
import test from 'node:test';
import assert from 'node:assert/strict';
import { placeLabel, sourceVersion } from '../../../../scripts/build-region-map.mjs';
import { insideRings } from './map-geometry.js';

test('판은 입력 파일 이름에서만 읽는다 (폴더 이름의 다른 판은 무시)', () => {
  assert.equal(sourceVersion('HangJeongDong_ver20260701.geojson'), 'ver20260701');
  assert.equal(sourceVersion('/data/ver20250101/HangJeongDong_ver20260701.geojson'), 'ver20260701');
  assert.equal(sourceVersion('C:\\Users\\me\\ver20250101\\HangJeongDong_ver20260701.geojson'), 'ver20260701');
});

test('파일 이름에 판이 없으면 폴더 이름에 있어도 null', () => {
  assert.equal(sourceVersion('/data/ver20250101/boundaries.geojson'), null);
  assert.equal(sourceVersion('C:\\geo\\ver20260701\\HangJeongDong.geojson'), null);
  assert.equal(sourceVersion('HangJeongDong.geojson'), null);
});

test('placeLabel: 칠해진 곳이 얇은 띠라 줄인 라벨이 경계에 닿으면, 화면에 그리는 고리 안쪽 좌표를 쓴다', () => {
  const outer = [
    [0, 0],
    [100, 0],
    [100, 100],
    [0, 100],
  ];
  // 각 변에서 0.3 떨어진 구멍: 격자 후보는 모두 구멍 안
  const hole = [
    [0.3, 0.3],
    [99.7, 0.3],
    [99.7, 99.7],
    [0.3, 99.7],
  ];
  const { label } = placeLabel(outer, [outer, hole]);
  const half = (v) => Math.round(v * 2) / 2;
  const drawn = [outer, hole].map((r) => r.map(([x, y]) => [half(x), half(y)]));
  assert.ok(insideRings(drawn, label), `라벨 ${label}이 그리는 고리의 칠해진 곳 밖`);
});
