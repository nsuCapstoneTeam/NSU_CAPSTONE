import { useEffect, useRef } from 'react';
import Icon from '../../../components/common/icon.jsx';

// 도면 예시 보기 (NSU-115): 어떤 도면을 올리면 좋은지 직접 그린 가상 공연장으로 보여 주는 대화 상자
// open: 열림 여부, onClose: 닫기

const CHECKS = [
  ['무대 가로·세로 길이', '밴드 편성과 장비가 들어갈지 판단해요.'],
  ['대기실 위치', '무대까지 얼마나 가까운지 알 수 있어요.'],
  ['장비 반입구', '악기와 장비를 어디로 옮기는지 알려 줘요.'],
  ['관객석 방향과 음향 콘솔', '소리를 어디서 조정하는지 알 수 있어요.'],
];

// 예시 도면의 번호 표시 위치 (CHECKS 순서와 같음)
const MARKS = [
  [421, 59],
  [79, 59],
  [611, 157],
  [151, 271],
];

// 관객석 줄 (가운데 통로를 비움)
const SEATS = Array.from({ length: 6 }, (_, r) =>
  Array.from({ length: 15 }, (_, c) => c)
    .filter((c) => c !== 7)
    .map((c) => [172 + c * 20, 190 + r * 22]),
).flat();

function ExamplePlan() {
  return (
    <svg
      className="plan-example"
      viewBox="0 0 640 440"
      role="img"
      aria-label="예시 도면: 무대 12m × 6m, 왼쪽에 대기실, 오른쪽에 장비 보관실과 반입구, 무대 앞에 관객석 약 300석과 음향 콘솔, 앞쪽 양옆에 출입구"
    >
      <rect
        className="plan-wall"
        x="60"
        y="40"
        width="520"
        height="360"
      />
      {/* 출입구: 벽을 지우는 틈 */}
      <path
        className="plan-gap"
        d="M100 400h40M520 400h40M580 100v34"
      />
      <rect
        className="plan-stage"
        x="200"
        y="40"
        width="240"
        height="110"
      />
      <text
        className="plan-stage-text"
        x="320"
        y="90"
      >
        무대
      </text>
      <text
        className="plan-stage-size"
        x="320"
        y="112"
      >
        12m × 6m
      </text>
      {/* 치수선 */}
      <path
        className="plan-dim"
        d="M200 20h240M200 14v12M440 14v12M458 40v110M452 40h12M452 150h12"
      />
      <text
        className="plan-dim-text"
        x="320"
        y="14"
      >
        12 m
      </text>
      <text
        className="plan-dim-text"
        x="476"
        y="99"
      >
        6 m
      </text>
      <rect
        className="plan-room"
        x="60"
        y="40"
        width="120"
        height="110"
      />
      <path
        className="plan-gap"
        d="M180 112v26"
      />
      <text
        className="plan-label"
        x="120"
        y="98"
      >
        대기실
      </text>
      <rect
        className="plan-room"
        x="500"
        y="40"
        width="80"
        height="110"
      />
      <text
        className="plan-label"
        x="540"
        y="90"
      >
        장비 보관
      </text>
      <text
        className="plan-note"
        x="612"
        y="96"
      >
        반입구
      </text>
      <path
        className="plan-arrow"
        d="M612 117h-22m5-5-5 5 5 5"
      />
      {SEATS.map(([x, y]) => (
        <rect
          key={`${x}-${y}`}
          className="plan-seat"
          x={x}
          y={y}
          width="14"
          height="10"
          rx="2"
        />
      ))}
      <text
        className="plan-note"
        x="320"
        y="354"
      >
        관객석 약 300석
      </text>
      <rect
        className="plan-console"
        x="290"
        y="368"
        width="60"
        height="22"
        rx="3"
      />
      <text
        className="plan-small"
        x="320"
        y="383"
      >
        음향 콘솔
      </text>
      <text
        className="plan-note"
        x="120"
        y="424"
      >
        출입구
      </text>
      <text
        className="plan-note"
        x="540"
        y="424"
      >
        출입구
      </text>
      {MARKS.map(([x, y], i) => (
        <g key={i}>
          <circle
            className="plan-mark"
            cx={x}
            cy={y}
            r="11"
          />
          <text
            className="plan-mark-text"
            x={x}
            y={y + 4}
          >
            {i + 1}
          </text>
        </g>
      ))}
    </svg>
  );
}

export default function StagePlanExample({ open, onClose }) {
  const ref = useRef(null);

  // <dialog>를 열고 닫음 (열려 있으면 바깥은 누를 수 없고 Esc로 닫힘)
  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      className="plan-dialog"
      aria-labelledby="plan-dialog-title"
      onClose={onClose}
    >
      <div className="plan-dialog-head">
        <h3 id="plan-dialog-title">이런 도면이면 좋아요</h3>
        <button
          type="button"
          className="icon-button"
          onClick={onClose}
          aria-label="닫기"
        >
          <Icon
            name="close"
            size={18}
          />
        </button>
      </div>
      <ExamplePlan />
      <ol className="plan-checks">
        {CHECKS.map(([title, text]) => (
          <li key={title}>
            <strong>{title}</strong>
            <span>{text}</span>
          </li>
        ))}
      </ol>
      <p className="plan-tip">
        정식 도면이 없으면 손으로 그린 그림이나 무대 사진도 괜찮아요. 길이가 그림에 없으면 특이사항에 적어 주세요.
      </p>
      <small>예시 그림은 CLAP SOUND가 직접 그린 가상의 공연장이에요.</small>
      <button
        type="button"
        className="primary"
        onClick={onClose}
      >
        확인
      </button>
    </dialog>
  );
}
