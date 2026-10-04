import { scoreText } from '../../utils/format-score.js';

// 항목 이름·점수(0~100)와 점수만큼 채워지는 막대를 표시합니다.
export default function ScoreBar({ label, score }) {
  return (
    <div className="metric">
      <div>
        <span>{label}</span>
        <strong>
          {scoreText(score)}
          <small> / 100</small>
        </strong>
      </div>
      {/* 막대: 화면 낭독기에도 점수가 전달되도록 meter 역할 지정 */}
      <div
        className="meter"
        role="meter"
        aria-label={label}
        aria-valuenow={score}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        <i style={{ width: `${score}%` }} />
      </div>
    </div>
  );
}
