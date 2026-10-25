import test from 'node:test';
import assert from 'node:assert/strict';
import { DISTRICTS, PROVINCES } from '../../data/regions.js';
import {
  DEMO_ADDRESSES,
  checkStagePlan,
  districtsOf,
  formatBytes,
  provinceShort,
  regionCodeFromSigungu,
  regionLabel,
  searchAddresses,
} from './event-place.js';

test('지역 목록은 시·도 16곳, 시·군·구 230곳이고 코드가 겹치지 않는다', () => {
  assert.equal(PROVINCES.length, 16);
  assert.equal(DISTRICTS.length, 230);
  assert.equal(new Set(DISTRICTS.map((d) => d.code)).size, 230);
  // 모든 시·군·구는 목록에 있는 시·도에 속한다
  const provinceCodes = new Set(PROVINCES.map((p) => p.code));
  assert.ok(DISTRICTS.every((d) => provinceCodes.has(d.code.slice(0, 2))));
});

test('시·도에 속한 시·군·구만 고른다', () => {
  const gyeonggi = districtsOf('41');
  assert.equal(gyeonggi.length, 31);
  assert.equal(gyeonggi[0].name, '수원시');
  assert.equal(districtsOf('36').length, 1);
});

test('주소의 시·군·구 코드를 지역 코드로 바꾼다 (일반구는 시로)', () => {
  assert.equal(regionCodeFromSigungu('11440'), '11440'); // 마포구 그대로
  assert.equal(regionCodeFromSigungu('41111'), '41110'); // 수원시 장안구 → 수원시
  assert.equal(regionCodeFromSigungu('43112'), '43110'); // 청주시 서원구 → 청주시
  assert.equal(regionCodeFromSigungu('99999'), null);
  assert.equal(regionCodeFromSigungu(''), null);
  assert.equal(regionCodeFromSigungu(undefined), null);
});

test('예시 주소는 모두 지역 목록의 코드로 바뀐다', () => {
  for (const a of DEMO_ADDRESSES) assert.ok(regionCodeFromSigungu(a.sigunguCode), a.road);
});

test('지역 이름과 시·도 짧은 이름', () => {
  assert.equal(regionLabel('41110'), '경기도 수원시');
  assert.equal(regionLabel('36110'), '세종특별자치시');
  assert.equal(regionLabel('12210'), '전남광주통합특별시 동구');
  assert.equal(regionLabel('nope'), '');
  assert.equal(provinceShort('41110'), '경기');
  assert.equal(provinceShort('11440'), '서울');
  assert.equal(provinceShort(''), '');
});

test('주소 검색은 띄어쓰기를 무시하고 도로명·지번에서 찾는다', () => {
  assert.equal(searchAddresses('한빛로 123').length, 3);
  assert.equal(searchAddresses('한빛로123').length, 3);
  assert.equal(searchAddresses('연남동')[0].road, '서울 마포구 연남로 45');
  assert.deepEqual(searchAddresses('없는길 1'), []);
  assert.deepEqual(searchAddresses('  '), []);
});

test('무대 도면은 JPG·PNG·PDF, 10MB 이하만 받는다', () => {
  assert.equal(checkStagePlan({ name: '도면.PDF', size: 2_000_000 }), '');
  assert.equal(checkStagePlan({ name: 'stage.jpeg', size: 500_000 }), '');
  assert.match(checkStagePlan({ name: 'stage.dwg', size: 1000 }), /PDF나 이미지/);
  assert.match(checkStagePlan({ name: '도면', size: 1000 }), /PDF나 이미지/);
  assert.match(checkStagePlan({ name: 'big.png', size: 11 * 1024 * 1024 }), /10MB 이하/);
  assert.equal(checkStagePlan({ name: 'edge.png', size: 10 * 1024 * 1024 }), '');
});

test('파일 크기 표시', () => {
  assert.equal(formatBytes(2.4 * 1024 * 1024), '2.4MB');
  assert.equal(formatBytes(820 * 1024), '820KB');
  assert.equal(formatBytes(10), '1KB');
});
