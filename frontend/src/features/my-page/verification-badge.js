// 확인 배지가 무엇을 보여 줄지 정하는 규칙 (NSU-110, 외부 링크 ADR 5-2번)
// - 배지 색: 배지 항목이 하나라도 있을 때만 파랑
// - 목록: 확인된 항목을 모두 보여 줌 (색을 켜지 않는 항목도 포함)

export const NO_ITEMS_TITLE = '아직 확인된 항목이 없어요';

const EXTERNAL_ACCOUNT_LABELS = { YOUTUBE: 'YouTube 채널 제어권 확인' };

// Trust Profile verifications 한 줄({ type, provider }) → 목록 이름과, 배지 색을 켜는지
// EXTERNAL_ACCOUNT는 가짜 채널로도 만들 수 있어 색을 켜지 않고 목록에만 보여 줌 (ADR 5-2번)
function describeOne({ type, provider }) {
  switch (type) {
    case 'IDENTITY':
      return { label: '실명 본인확인', lightsBadge: true };
    case 'COPYRIGHT':
      return { label: '등록부 저작자·권리자와 실명 일치', lightsBadge: true };
    case 'BUSINESS_REGISTRATION':
      return { label: '사업자등록 확인', lightsBadge: true };
    case 'EXTERNAL_ACCOUNT':
      return { label: EXTERNAL_ACCOUNT_LABELS[provider] ?? '외부 계정 제어권 확인', lightsBadge: false };
    default:
      return { label: '확인된 항목', lightsBadge: false };
  }
}

// Trust Profile verifications → { badgeItems: 색을 켜는 항목, listItems: 목록에 보일 항목 }
export function describeVerifications(verifications = []) {
  const described = verifications.map(describeOne);
  return {
    badgeItems: described.filter((d) => d.lightsBadge).map((d) => d.label),
    listItems: described.map((d) => d.label),
  };
}

// 이름 목록만 넘기는 기존 화면용
// 이름이 있으면 예전부터 파랗게 하던 항목이므로 색과 목록에 같은 이름을 쓴다
export function itemsFromNames(names = []) {
  return { badgeItems: names, listItems: names };
}

// 항목 → 배지가 그릴 값
export function badgeView({ badgeItems = [], listItems = [] } = {}) {
  const hasList = listItems.length > 0;
  const isBlue = badgeItems.length > 0;
  const head = isBlue ? '확인된 아티스트' : '확인된 항목';
  return {
    isBlue,
    listItems,
    title: hasList ? head : NO_ITEMS_TITLE,
    label: hasList ? `${head}: ${listItems.join(', ')}` : '아직 확인된 항목이 없는 아티스트',
  };
}
