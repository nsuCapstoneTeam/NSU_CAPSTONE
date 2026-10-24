// 참고 곡으로 찾기 (NSU-111): 행사 관계자가 올리는 참고 곡 파일 검사
// 규칙은 서버 협의 013(Proposed)의 AI Sample 업로드 조건과 같게 둔 임시 값입니다.
// 참고 곡 업로드는 아직 SSOT·API 계약에 없어 팀 확정이 필요합니다. 파일은 서버로 보내지 않습니다.

export const UPLOAD_RULES = {
  extensions: ['mp3', 'm4a'],
  maxBytes: 10 * 1024 * 1024, // 10MB
  minSeconds: 60,
  maxSeconds: 80,
  analyzedSeconds: 56, // AI가 앞에서부터 비교에 쓰는 길이 (013)
};

// 파일 선택 창에 넘길 accept 값
export const ACCEPT = '.mp3,.m4a,audio/mpeg,audio/mp4,audio/x-m4a,audio/aac';

// 업로드 칸 아래 경고문 (화면에 그대로 보여 줌)
export const UPLOAD_NOTICE = [
  'MP3, M4A 파일만 올릴 수 있어요. WAV는 MP3나 M4A로 바꿔서 올려 주세요.',
  '파일 크기는 10MB 이하, 길이는 60초에서 80초 사이여야 해요.',
  'AI는 앞부분 56초로 비교해요. 분위기가 잘 드러나는 구간을 잘라서 올려 주세요.',
  '내가 권리를 가졌거나 사용 허락을 받은 곡만 올려 주세요.',
];

// 파일 이름의 확장자 (소문자, 없으면 '')
export function extensionOf(name = '') {
  const dot = name.lastIndexOf('.');
  return dot > 0 ? name.slice(dot + 1).toLowerCase() : '';
}

// 초 → '1:12'
export function formatDuration(seconds) {
  const total = Math.max(0, Math.floor(seconds || 0));
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')}`;
}

// 바이트 → '2.8MB' (1MB 미만은 'KB')
export function formatSize(bytes) {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))}KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1).replace(/\.0$/, '')}MB`;
}

// 길이를 읽기 전에 할 수 있는 검사 (형식·크기). 문제가 없으면 null
// 반환: { kind: 'format' | 'size', title, fix }
export function checkFileBasics({ name, size }) {
  const ext = extensionOf(name);
  if (ext === 'wav') {
    return {
      kind: 'format',
      title: 'WAV 파일은 올릴 수 없어요',
      fix: 'MP3나 M4A로 바꿔서 올려 주세요.',
    };
  }
  if (!UPLOAD_RULES.extensions.includes(ext)) {
    return {
      kind: 'format',
      title: `${name} 파일은 올릴 수 없어요`,
      fix: 'MP3나 M4A 파일로 올려 주세요.',
    };
  }
  if (size > UPLOAD_RULES.maxBytes) {
    return {
      kind: 'size',
      title: `파일이 10MB보다 커요 (지금 ${formatSize(size)})`,
      fix: '음질을 낮춰 MP3로 저장하면 크기가 줄어요.',
    };
  }
  return null;
}

// 길이 검사. 문제가 없으면 null. seconds가 숫자가 아니면 읽을 수 없는 파일로 봄
export function checkDuration(seconds) {
  if (!Number.isFinite(seconds) || seconds <= 0) {
    return {
      kind: 'broken',
      title: '파일을 재생할 수 없어요',
      fix: '파일이 손상되지 않았는지 확인하고 다시 올려 주세요.',
    };
  }
  const { minSeconds, maxSeconds } = UPLOAD_RULES;
  // 재생 시간 표시는 초 단위로 버리므로 검사도 같은 기준(소수점 버림)으로 함
  const whole = Math.floor(seconds);
  if (whole < minSeconds) {
    return {
      kind: 'length',
      title: `곡이 60초보다 짧아요 (지금 ${formatDuration(seconds)})`,
      fix: '60초에서 80초 사이로 잘라서 올려 주세요.',
    };
  }
  if (whole > maxSeconds) {
    return {
      kind: 'length',
      title: `곡이 80초보다 길어요 (지금 ${formatDuration(seconds)})`,
      fix: '분위기가 잘 드러나는 60초에서 80초 구간만 잘라서 올려 주세요.',
    };
  }
  return null;
}
