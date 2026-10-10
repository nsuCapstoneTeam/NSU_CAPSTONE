// 내 행사(NSU-56) → 매칭 조건 변환 (NSU-95, 요구사항 AI-MATCH-050: 매칭은 등록된 행사 기준)
// 화면 없이 값만 바꾸는 순수 함수 (실행 테스트: node --test src/features/matching/event-to-search.test.js)
import { GENRES, REGIONS, TYPES } from './matching.js';

// 행사 하나를 지금 매칭 조건(current)에 덮어써서 돌려줍니다.
// - 행사에 없는 조건(분위기·템포·리듬)은 current 값을 그대로 둡니다.
// - 매칭 선택지에 없는 값은 current 값을 두고 skipped에 이유를 남깁니다.
// 반환: { search: 새 매칭 조건, skipped: [안내 문구] }
export function eventToSearch(event, current) {
  const search = {
    ...current,
    title: event.name,
    description: event.description,
    date: event.date,
    start: event.startTime,
    minutes: event.performanceMinutes,
    // 매칭 필터는 '출연료 ≤ 예산'이라 예산 최대값을 씀 (팀 확정 필요)
    budget: event.budgetMax,
  };
  const skipped = [];

  // 선택지에 있는 값만 가져오는 도우미
  function pick(key, value, options, label) {
    if (options.includes(value)) search[key] = value;
    else skipped.push(`${label} '${value}'은(는) 매칭 선택지에 없어 '${current[key]}'(으)로 두었어요.`);
  }
  pick('region', event.region, REGIONS, '지역');
  pick('type', event.eventType, TYPES, '행사 종류');

  // 매칭은 장르를 하나만 받으므로, 희망 장르 중 선택지에 있는 첫 번째를 사용
  const genre = event.genres.find((g) => GENRES.includes(g));
  if (genre) {
    search.genre = genre;
    if (event.genres.length > 1) {
      skipped.push(`희망 장르가 여러 개라 첫 번째인 '${genre}'(으)로 찾아요.`);
    }
  } else {
    skipped.push(`희망 장르가 매칭 선택지에 없어 '${current.genre}'(으)로 두었어요.`);
  }

  return { search, skipped };
}

// 선택 상자에 보여 줄 행사 이름: '2026 가을 축제 · 10월 30일' (지난 행사 표시)
export function eventOptionLabel(event, today) {
  const [, month, day] = event.date.split('-').map(Number);
  const past = event.date < today ? ' (지난 행사)' : '';
  return `${event.name} · ${month}월 ${day}일${past}`;
}
