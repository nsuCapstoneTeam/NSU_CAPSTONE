// 지도 데이터 만들기 스크립트(scripts/build-region-map.mjs)의 도우미 테스트
// 실행: node --test src/features/event-search/map/build-region-map.test.js
// (스크립트는 직접 실행할 때만 지도 데이터를 만들고, 불러오기만 하면 아무것도 쓰지 않음)
import test from 'node:test';
import assert from 'node:assert/strict';
import { sourceVersion } from '../../../../scripts/build-region-map.mjs';

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
