export const scoreText = (score) =>
  Number.isInteger(score) ? score : score.toFixed(1);
