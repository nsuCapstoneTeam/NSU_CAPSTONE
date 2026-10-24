// 홈 '음악 매칭' 구간의 예시 데이터. Figma 시안과 같은 값이며 실제 추천 결과가 아닙니다.
// 순위는 음악 유사도 하나로만 정하고, 템포·리듬은 점수 없이 참고로만 보여 준다는 계약(docs/api/02)을 따릅니다.
export const MATCHING_SAMPLE = {
  similarity: 86,
  // 음악 유사도를 계산할 때 쓰는 행사 정보 (서버에 저장된 값의 예시)
  event: [
    ['설명', '해 질 녘 야외 웨딩 파티'],
    ['종류', '웨딩'],
    ['희망 장르', '어쿠스틱, 인디 팝'],
  ],
  song: { title: '새벽의 정원', artist: '하늘색 밴드', clip: '60~80초 샘플' },
  bpm: 98,
  // AI가 행사 정보에서 해석한 원하는 템포와 그 BPM 구간
  wantedTempo: { label: '빠름', from: 120, to: 160 },
  // 이 곡의 한 마디 16칸 (1 = 박 있음). 행사 설명으로 찾을 때는 비교할 곡이 없어 이 곡의 박자만 보여 줌 (NSU-113)
  beats: [1, 0, 1, 0, 1, 1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 1],
  reasons: {
    similarity: '요청한 분위기와 음악적으로 가장 가까운 곡이에요.',
    tempo: 'BPM 98로 요청한 빠른 템포보다 느려요.',
    rhythm: '이 곡의 박자예요. 리듬은 순위에 쓰지 않는 참고 정보예요.',
  },
  required: ['아티스트 인증', '저작권', '일정', '지역', '예산'],
  songs: [
    { artist: '하늘색 밴드', song: '새벽의 정원', score: 86, isNew: false },
    { artist: '윤슬', song: '여름 끝의 편지', score: 83, isNew: true },
    { artist: '모노 트리오', song: 'Golden Hour', score: 81, isNew: false },
  ],
};

// 한 마디에서 박이 나오는 수
export function beatCount(beats) {
  return beats.filter(Boolean).length;
}

// BPM을 느림(0)~빠름(100) 막대 위 위치(%)로 바꿈. 60~160 BPM 범위로 봅니다.
export function tempoPosition(bpm) {
  return Math.min(100, Math.max(0, ((bpm - 60) / 100) * 100));
}
