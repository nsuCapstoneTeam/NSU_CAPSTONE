// 행사 장소 도우미 (NSU-115): 주소 → 지역(시·군·구) 코드, 지역 이름, 체험용 주소 검색, 무대 도면 파일 검사
// 화면 없이 값만 다루는 순수 함수 (실행 테스트: node --test src/features/my-page/event-place.test.js)
import { DISTRICTS, PROVINCES } from '../../data/regions.js';

const districtByCode = new Map(DISTRICTS.map((d) => [d.code, d]));
const provinceByCode = new Map(PROVINCES.map((p) => [p.code, p]));

// 시·도에 속한 시·군·구 목록 (코드 순)
export const districtsOf = (provinceCode) => DISTRICTS.filter((d) => d.code.startsWith(provinceCode));

// 주소의 시·군·구 코드(5자리)를 지역 목록의 코드로 바꿉니다. 목록에 없으면 null
// 일반구는 목록에 없고 시 하나로 합쳐 있어서, 끝자리를 0으로 바꾼 시 코드를 씁니다.
// 예: 수원시 장안구 41111 → 수원시 41110 (백엔드 regions 표의 규칙과 같음)
export function regionCodeFromSigungu(sigunguCode) {
  const code = String(sigunguCode ?? '');
  if (!/^\d{5}$/.test(code)) return null;
  if (districtByCode.has(code)) return code;
  const city = `${code.slice(0, 4)}0`;
  return districtByCode.has(city) ? city : null;
}

// 지역 이름: '경기도 수원시'. 세종은 시·도와 시·군·구가 같아 '세종특별자치시' 한 번만
export function regionLabel(regionCode) {
  const district = districtByCode.get(regionCode);
  if (!district) return '';
  const province = provinceByCode.get(regionCode.slice(0, 2));
  return district.name === province.name ? province.name : `${province.name} ${district.name}`;
}

// 매칭 조건에 쓰는 시·도 짧은 이름: '41110' → '경기'
export const provinceShort = (regionCode) => provinceByCode.get(String(regionCode ?? '').slice(0, 2))?.short ?? '';

// [체험용] 주소 검색에 쓰는 예시 주소. 실제 주소 검색 서비스는 팀 확정 필요
// sigunguCode는 주소 검색 서비스가 주는 시·군·구 코드(일반구 포함)라고 가정합니다.
export const DEMO_ADDRESSES = [
  { road: '경기 수원시 장안구 한빛로 123', jibun: '경기 수원시 장안구 한빛동 45-1', zip: '16200', sigunguCode: '41111' },
  { road: '경기 수원시 장안구 한빛로123번길 8', jibun: '경기 수원시 장안구 한빛동 51-3', zip: '16201', sigunguCode: '41111' },
  { road: '충북 청주시 서원구 한빛로 123', jibun: '충북 청주시 서원구 산남동 712', zip: '28640', sigunguCode: '43112' },
  { road: '서울 마포구 연남로 45', jibun: '서울 마포구 연남동 239-1', zip: '03991', sigunguCode: '11440' },
  { road: '서울 강남구 라온로 21', jibun: '서울 강남구 역삼동 812-4', zip: '06236', sigunguCode: '11680' },
  { road: '부산 해운대구 바다숲로 77', jibun: '부산 해운대구 우동 1411', zip: '48095', sigunguCode: '26350' },
  { road: '대구 중구 동성음악로 10', jibun: '대구 중구 동성로2가 88', zip: '41937', sigunguCode: '27110' },
  { road: '전남광주 동구 충장음악로 30', jibun: '전남광주 동구 충장로3가 12', zip: '61476', sigunguCode: '12210' },
  { road: '제주 제주시 애월바람길 5', jibun: '제주 제주시 애월읍 고내리 101', zip: '63044', sigunguCode: '50110' },
];

// 검색어에 맞는 예시 주소 (띄어쓰기는 무시). 검색어가 비면 빈 목록
export function searchAddresses(query, list = DEMO_ADDRESSES) {
  const q = String(query ?? '').replace(/\s+/g, '');
  if (!q) return [];
  return list.filter((a) => `${a.road}${a.jibun}`.replace(/\s+/g, '').includes(q));
}

// 무대 도면 파일 규칙 [임시 값, 팀 확정 필요]
export const STAGE_PLAN_RULES = { extensions: ['jpg', 'jpeg', 'png', 'pdf'], maxBytes: 10 * 1024 * 1024 };
export const STAGE_PLAN_ACCEPT = '.jpg,.jpeg,.png,.pdf';

const extensionOf = (name) => (String(name).includes('.') ? String(name).split('.').pop().toLowerCase() : '');

// 파일 크기 표시: 2.4MB, 820KB
export function formatBytes(bytes) {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)}MB`;
  return `${Math.max(1, Math.round(bytes / 1024))}KB`;
}

// 올릴 수 없는 파일이면 고치는 방법을 담은 문구, 올릴 수 있으면 ''
export function checkStagePlan(file) {
  const ext = extensionOf(file.name);
  if (!STAGE_PLAN_RULES.extensions.includes(ext)) {
    return `${file.name}은(는) 올릴 수 없어요. 도면을 PDF나 이미지(JPG·PNG)로 저장해서 올려 주세요.`;
  }
  if (file.size > STAGE_PLAN_RULES.maxBytes) {
    return `${formatBytes(file.size)} 파일이라 올릴 수 없어요. 10MB 이하로 줄여서 올려 주세요.`;
  }
  return '';
}
