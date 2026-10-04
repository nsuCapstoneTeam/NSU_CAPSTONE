// 점수 표시용 문자열: 정수는 그대로, 소수는 첫째 자리까지 (예: 87, 86.5)
export const scoreText = (score) =>
  Number.isInteger(score) ? score : score.toFixed(1);
