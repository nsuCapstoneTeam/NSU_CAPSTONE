import { scoreText } from '../../utils/format-score.js';

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
