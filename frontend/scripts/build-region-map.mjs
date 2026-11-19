// 행사 찾기 지도 데이터 만들기 (NSU-117)
// 행정동 경계 GeoJSON을 시·도 / 시·군·구 조각으로 합치고, 화면용 SVG 경로로 줄여서
// src/features/event-search/map/map-data/ 에 파일로 씁니다. 라이브러리 없이 Node만 씁니다.
//
// 경계 자료: vuski/admdongkor (통계청 SGIS 행정동 경계, 공공누리 제1유형 → CC BY 4.0)
//   https://raw.githubusercontent.com/vuski/admdongkor/master/ver20260701/HangJeongDong_ver20260701.geojson
//
// 실행 (frontend 폴더에서):
//   node scripts/build-region-map.mjs <HangJeongDong_verYYYYMMDD.geojson 경로>
//   판(verYYYYMMDD)은 파일 이름에서 읽어 생성 파일 머리말에 적습니다. 파일 이름을 바꾸지 마세요.
//
// 행정구역이 바뀌면 data/regions.js를 먼저 새 코드에 맞추고 실행합니다(목록에 없는 코드가 있으면 멈춤).
// 시·도 코드가 바뀌면 map/map-loaders.js의 LOADERS도 함께 고칩니다.
// 파일은 임시 폴더에 모두 만든 뒤 한 번에 바꿔 넣어서, 중간에 실패해도 기존 지도 데이터는 그대로입니다.
import { existsSync, mkdirSync, readFileSync, renameSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { DISTRICTS, PROVINCES } from '../src/data/regions.js';
import { labelPoint } from '../src/features/event-search/map/map-geometry.js';

const SOURCE_URL = 'https://github.com/vuski/admdongkor';
const LICENSE_URL = 'https://creativecommons.org/licenses/by/4.0/';
const OUT_DIR = join(dirname(fileURLToPath(import.meta.url)), '../src/features/event-search/map/map-data');

// 지도에서 뺄 섬 (너무 멀리 있어 지도가 작아짐). 화면에는 칩으로 따로 보여 줍니다.
const LEFT_OUT = {
  nation: (lon) => lon < 125 || lon > 130.5, // 백령도 등 서해 먼 섬, 울릉도·독도
  '28': (lon, lat, code) => code === '28720', // 인천 옹진군
  '47': (lon, lat, code) => code === '47940', // 경북 울릉군
  '12': (lon, lat) => lon < 125.95 || lat < 34.15, // 전남광주 먼 섬
  '50': (lon, lat) => lat > 33.6, // 제주 추자도
};
// 화면 지도 크기(줄이기 전, 위아래 납작하게 하는 것은 화면에서): 최대 600 × 460
const BOX = { width: 600, height: 460 };
// 단순화 허용 오차(px)와 남길 최소 섬 넓이(px²)
const NATION = { tol: 0.9, minArea: 10 };
const PROVINCE = { tol: 1, minArea: 6 };
const ISLAND_HEAVY = new Set(['12', '44', '48']); // 섬이 많은 곳은 작은 섬을 더 뺌

const districtCodes = new Set(DISTRICTS.map((d) => d.code));
// 일반구(수원시 장안구 41111)는 시(41110)로
const toDistrict = (sgg) => (districtCodes.has(sgg) ? sgg : `${sgg.slice(0, 4)}0`);

const keyOf = (p) => `${p[0].toFixed(7)},${p[1].toFixed(7)}`;
const signedArea = (r) => {
  let a = 0;
  for (let i = 0; i < r.length - 1; i++) a += r[i][0] * r[i + 1][1] - r[i + 1][0] * r[i][1];
  return a / 2;
};
const centroid = (r) => [r.reduce((s, p) => s + p[0], 0) / r.length, r.reduce((s, p) => s + p[1], 0) / r.length];

// 붙어 있는 행정동을 하나로 합치기: 두 조각이 함께 쓰는 변은 지우고 남은 변을 이어 고리로 만듦
// (경계 자료의 이웃 행정동은 꼭짓점을 똑같이 공유한다고 가정)
function dissolve(polygons) {
  const edges = new Map();
  for (const poly of polygons) {
    poly.forEach((ring, i) => {
      // 바깥 고리는 반시계, 구멍은 시계 방향으로 맞춤 (경도·위도 기준)
      const r = (i === 0) !== signedArea(ring) > 0 ? [...ring].reverse() : ring;
      for (let j = 0; j < r.length - 1; j++) {
        const a = keyOf(r[j]);
        const b = keyOf(r[j + 1]);
        if (a === b) continue;
        if (edges.has(`${b}>${a}`)) edges.delete(`${b}>${a}`);
        else edges.set(`${a}>${b}`, [r[j], r[j + 1], a, b]);
      }
    });
  }
  const from = new Map();
  for (const e of edges.values()) {
    if (!from.has(e[2])) from.set(e[2], []);
    from.get(e[2]).push(e);
  }
  const rings = [];
  while (from.size) {
    const [startKey, list] = from.entries().next().value;
    let edge = list.pop();
    if (!list.length) from.delete(startKey);
    const ring = [edge[0]];
    for (;;) {
      ring.push(edge[1]);
      if (edge[3] === startKey) break;
      const next = from.get(edge[3]);
      if (!next) break;
      edge = next.pop();
      if (!next.length) from.delete(edge[2]);
    }
    if (ring.length > 3) rings.push(ring);
  }
  return rings;
}

// Douglas-Peucker
function simplify(points, tol) {
  if (points.length < 3) return points;
  const a = points[0];
  const b = points[points.length - 1];
  const dx = b[0] - a[0];
  const dy = b[1] - a[1];
  const len = Math.hypot(dx, dy);
  let index = 0;
  let max = -1;
  for (let i = 1; i < points.length - 1; i++) {
    const p = points[i];
    const d =
      len > 1e-9
        ? Math.abs(dy * p[0] - dx * p[1] + b[0] * a[1] - b[1] * a[0]) / len
        : Math.hypot(p[0] - a[0], p[1] - a[1]);
    if (d > max) {
      max = d;
      index = i;
    }
  }
  if (max <= tol) return [a, b];
  return simplify(points.slice(0, index + 1), tol).slice(0, -1).concat(simplify(points.slice(index), tol));
}

const half = (v) => Math.round(v * 2) / 2;

// 이웃과 함께 쓰는 변은 두 조각이 반대 방향으로 지나가므로, 늘 같은 방향으로 줄여야 같은 점이 남음
// (Douglas-Peucker는 거리가 같은 점이 둘이면 먼저 만난 점을 골라 방향에 따라 결과가 달라질 수 있음)
function simplifyShared(seg, tol) {
  const [a, b] = [seg[0], seg[seg.length - 1]];
  const forward = a[0] < b[0] || (a[0] === b[0] && a[1] <= b[1]);
  return forward ? simplify(seg, tol) : simplify([...seg].reverse(), tol).reverse();
}

// 화면 좌표로 반올림한 뒤에도 넓이가 남는 고리인지
const visible = (r) => r.length >= 3 && Math.abs(signedArea(r.map((q) => [half(q[0]), half(q[1])]).concat([[half(r[0][0]), half(r[0][1])]]))) > 0;

// 한 화면(전국 또는 시·도 하나)에 들어갈 조각들을 화면 좌표로 바꾸고 줄임
// 이웃 조각과 함께 쓰는 변은 같은 점에서 끊어 따로 줄여서, 줄인 뒤에도 틈이나 겹침이 생기지 않게 함
function buildLevel(pieces, { tol, minArea }) {
  const K = Math.cos((36 * Math.PI) / 180);
  const owners = new Map();
  pieces.forEach((p, i) =>
    p.rings.forEach((r) =>
      r.forEach((pt) => {
        const k = keyOf(pt);
        if (!owners.has(k)) owners.set(k, new Set());
        owners.get(k).add(i);
      }),
    ),
  );
  // 색 번호(0~4): 맞닿은 조각끼리 같은 색이 되지 않게 이웃이 많은 조각부터 정함
  const near = pieces.map(() => new Set());
  for (const set of owners.values()) for (const a of set) for (const b of set) if (a !== b) near[a].add(b);
  const tones = new Array(pieces.length).fill(-1);
  [...pieces.keys()]
    .sort((a, b) => near[b].size - near[a].size)
    .forEach((i) => {
      const used = new Set([...near[i]].map((j) => tones[j]));
      tones[i] = [0, 1, 2, 3, 4].find((t) => !used.has(t)) ?? i % 5;
    });
  let [minX, minY, maxX, maxY] = [Infinity, Infinity, -Infinity, -Infinity];
  for (const p of pieces) {
    for (const r of p.rings) {
      for (const [lon, lat] of r) {
        minX = Math.min(minX, lon * K);
        maxX = Math.max(maxX, lon * K);
        minY = Math.min(minY, -lat);
        maxY = Math.max(maxY, -lat);
      }
    }
  }
  const s = Math.min(BOX.width / (maxX - minX), BOX.height / (maxY - minY));
  const project = ([lon, lat]) => [(lon * K - minX) * s, (-lat - minY) * s];
  const same = (a, b) => a.size === b.size && [...a].every((x) => b.has(x));

  const out = pieces.map((p, pieceIndex) => {
    const shapes = p.rings
      .map((ring) => {
        const r = ring.slice(0, -1);
        const n = r.length;
        const sets = r.map((pt) => owners.get(keyOf(pt)));
        const fixed = r
          .map((_, i) =>
            sets[i].size >= 3 || !same(sets[i], sets[(i - 1 + n) % n]) || !same(sets[i], sets[(i + 1) % n])
              ? i
              : -1,
          )
          .filter((i) => i >= 0);
        const P = r.map(project);
        if (!fixed.length) {
          // 혼자 있는 섬: 가장 먼 점에서 둘로 나눠 줄임
          let far = 0;
          let farD = -1;
          P.forEach((q, i) => {
            const d = Math.hypot(q[0] - P[0][0], q[1] - P[0][1]);
            if (d > farD) {
              farD = d;
              far = i;
            }
          });
          const simplified = simplify(P.slice(0, far + 1), tol)
            .slice(0, -1)
            .concat(simplify(P.slice(far).concat([P[0]]), tol).slice(0, -1));
          return { simplified, original: P };
        }
        const result = [];
        for (let j = 0; j < fixed.length; j++) {
          const start = fixed[j];
          const end = fixed[(j + 1) % fixed.length];
          const seg = [];
          let i = start;
          for (;;) {
            seg.push(P[i]);
            if (i === end && seg.length > 1) break;
            i = (i + 1) % n;
            if (fixed.length === 1 && i === start) {
              seg.push(P[start]);
              break;
            }
          }
          result.push(...simplifyShared(seg, tol).slice(0, -1));
        }
        return { simplified: result, original: P };
      });
    let rings = shapes.map((x) => x.simplified).filter(visible);
    // 줄이다가 고리가 모두 사라지면(아주 작은 시·군·구) 줄이지 않은 모양을 씀
    if (!rings.length) rings = shapes.map((x) => x.original).filter(visible);
    if (!rings.length) throw new Error(`지도에 그릴 수 없는 조각: ${p.code}`);
    const areas = rings.map((r) => Math.abs(signedArea(r.concat([r[0]]))));
    const biggest = Math.max(...areas);
    const main = rings[areas.indexOf(biggest)];
    rings = rings.filter((_, i) => areas[i] >= minArea || areas[i] === biggest);
    // 라벨 자리는 구멍(다른 시·군·구를 둘러싼 자리)을 뺀 실제 칠해진 곳에서 찾음
    const { point, room } = labelPoint(main, rings);
    const d = rings.map((r) => `M${r.map((q) => `${half(q[0])},${half(q[1])}`).join(' ')}Z`).join('');
    // room: 라벨 자리에서 테두리까지 거리(px). 작으면 화면에서 이름을 생략
    return {
      code: p.code,
      d,
      label: [half(point[0]), half(point[1])],
      room: Math.round(room),
      tone: tones[pieceIndex],
    };
  });
  // proj: 경도·위도를 이 지도 좌표로 바꾸는 값 [minX, minY, s]
  //   x = (경도 × cos36° − minX) × s, y = (−위도 − minY) × s  (행사 장소 핀 자리 계산에 씀)
  const proj = [Number(minX.toFixed(6)), Number(minY.toFixed(6)), Number(s.toFixed(4))];
  return { width: half((maxX - minX) * s), height: half((maxY - minY) * s), proj, pieces: out };
}

function writeModule(dir, version, name, data, note) {
  const header = [
    `// 자동 생성 파일 (scripts/build-region-map.mjs) — 손으로 고치지 마세요.`,
    `// 경계: 통계청 통계지리정보서비스(SGIS) 행정동 경계(공공누리 제1유형)를 vuski/admdongkor ${version}이 가공한 자료(CC BY 4.0)를 다시 합치고 줄였습니다.`,
    `// 원본: ${SOURCE_URL} (${version}), 라이선스: ${LICENSE_URL}`,
    note ? `// ${note}` : null,
  ]
    .filter(Boolean)
    .join('\n');
  writeFileSync(join(dir, `${name}.js`), `${header}\nexport default ${JSON.stringify(data)};\n`);
}

// 입력 파일 이름에서 판(verYYYYMMDD)을 읽음. 폴더 이름은 보지 않음 (폴더에 다른 판이 있어도 헷갈리지 않게)
// Windows 경로(\\)와 / 경로를 모두 받음. 없으면 null
export function sourceVersion(inputPath) {
  const name = String(inputPath).split(/[\\/]/).pop();
  return name.match(/ver\d{8}/)?.[0] ?? null;
}

// ---------- 실행 ----------
// 테스트에서 sourceVersion만 불러 쓸 수 있게, 이 파일을 직접 실행할 때만 지도 데이터를 만듦
function main() {
  const input = process.argv[2];
  if (!input) {
    console.error('사용법: node scripts/build-region-map.mjs <HangJeongDong_verYYYYMMDD.geojson>');
    process.exit(1);
  }
  const version = sourceVersion(input);
  if (!version) {
    console.error('파일 이름에서 판(verYYYYMMDD)을 찾지 못했어요. 받은 파일 이름 그대로 넣어 주세요.');
    process.exit(1);
  }
  const geo = JSON.parse(readFileSync(input, 'utf8'));
  const byDistrict = new Map();
  const byProvince = new Map();
  for (const f of geo.features) {
    const { sgg, sido } = f.properties;
    const code = toDistrict(sgg);
    if (!districtCodes.has(code)) throw new Error(`지역 목록에 없는 시·군·구 코드: ${sgg} (${f.properties.adm_nm})`);
    const polys = f.geometry.type === 'Polygon' ? [f.geometry.coordinates] : f.geometry.coordinates;
    if (!byDistrict.has(code)) byDistrict.set(code, []);
    byDistrict.get(code).push(...polys);
    if (!byProvince.has(sido)) byProvince.set(sido, []);
    byProvince.get(sido).push(...polys);
  }
  const missing = DISTRICTS.filter((d) => !byDistrict.has(d.code));
  if (missing.length) throw new Error(`경계가 없는 시·군·구: ${missing.map((d) => d.code + d.name).join(', ')}`);

  // 임시 폴더에 모두 만든 뒤 한 번에 바꿔 넣음 (중간에 실패하면 기존 파일은 그대로)
  const TMP_DIR = `${OUT_DIR}.tmp`;
  const OLD_DIR = `${OUT_DIR}.old`;
  rmSync(TMP_DIR, { recursive: true, force: true });
  mkdirSync(TMP_DIR, { recursive: true });
  const keep = (filter, code) => (ring) => {
    if (!filter) return true;
    const [lon, lat] = centroid(ring);
    return !filter(lon, lat, code);
  };

  const nationPieces = PROVINCES.map((p) => ({
    code: p.code,
    rings: dissolve(byProvince.get(p.code)).filter(keep(LEFT_OUT.nation, p.code)),
  }));
  writeModule(TMP_DIR, version, 'nation', { ...buildLevel(nationPieces, NATION), away: [] }, '전국 시·도 (백령도 등 서해 먼 섬, 울릉도·독도는 뺐습니다)');

  for (const p of PROVINCES) {
    const all = DISTRICTS.filter((d) => d.code.startsWith(p.code)).map((d) => ({
      code: d.code,
      rings: dissolve(byDistrict.get(d.code)).filter(keep(LEFT_OUT[p.code], d.code)),
    }));
    const pieces = all.filter((piece) => piece.rings.length);
    const opts = ISLAND_HEAVY.has(p.code) ? { ...PROVINCE, minArea: 14 } : PROVINCE;
    // away: 지도에서 통째로 뺀 시·군·구 (옹진군, 울릉군). 화면에서 지도 옆 칩으로 보여 줌
    const away = all.filter((piece) => !piece.rings.length).map((piece) => piece.code);
    writeModule(TMP_DIR, version, p.code, { ...buildLevel(pieces, opts), away }, `${p.name} 시·군·구`);
  }
  rmSync(OLD_DIR, { recursive: true, force: true });
  if (existsSync(OUT_DIR)) renameSync(OUT_DIR, OLD_DIR);
  try {
    renameSync(TMP_DIR, OUT_DIR);
  } catch (error) {
    if (existsSync(OLD_DIR)) renameSync(OLD_DIR, OUT_DIR);
    throw error;
  }
  rmSync(OLD_DIR, { recursive: true, force: true });
  console.log(`지도 데이터 ${PROVINCES.length + 1}개 파일을 만들었어요 (${version}): ${OUT_DIR}`);
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main();
