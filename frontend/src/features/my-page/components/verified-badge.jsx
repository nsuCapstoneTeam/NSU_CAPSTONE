import './verified-badge.css';

// 아티스트 이름 옆 확인 배지 (NSU-110)
// - 확인됨(파랑): 검증이 끝난 항목이 하나 이상 있음. 마우스를 올리거나 포커스하면 항목 목록을 보여 줌
// - 미확인(회색): 아직 확인된 항목이 없음
// 표시 전용입니다. 추천 순위에는 쓰지 않습니다(TRUST-152). 파랗게 바꾸는 기준은 팀 확정 필요.

// 가장자리가 물결 모양인 배지 테두리 (24×24 기준)
const SEAL =
  'M23.50 12.00 L23.07 13.09 L22.00 13.99 L20.88 14.69 L20.22 15.41 L20.18 16.37 L20.48 17.67 L20.60 19.05 L20.13 20.13 L19.05 20.60 L17.67 20.48 L16.37 20.18 L15.41 20.22 L14.69 20.88 L13.99 22.00 L13.09 23.07 L12.00 23.50 L10.91 23.07 L10.01 22.00 L9.31 20.88 L8.59 20.22 L7.63 20.18 L6.33 20.48 L4.95 20.60 L3.87 20.13 L3.40 19.05 L3.52 17.67 L3.82 16.37 L3.78 15.41 L3.12 14.69 L2.00 13.99 L0.93 13.09 L0.50 12.00 L0.93 10.91 L2.00 10.01 L3.12 9.31 L3.78 8.59 L3.82 7.63 L3.52 6.33 L3.40 4.95 L3.87 3.87 L4.95 3.40 L6.33 3.52 L7.63 3.82 L8.59 3.78 L9.31 3.12 L10.01 2.00 L10.91 0.93 L12.00 0.50 L13.09 0.93 L13.99 2.00 L14.69 3.12 L15.41 3.78 L16.37 3.82 L17.67 3.52 L19.05 3.40 L20.13 3.87 L20.60 4.95 L20.48 6.33 L20.18 7.63 L20.22 8.59 L20.88 9.31 L22.00 10.01 L23.07 10.91Z';

// items: 확인된 항목 이름 목록 (예: ['사업자등록 확인']), size: 배지 크기(px)
export default function VerifiedBadge({ items = [], size = 18 }) {
  const verified = items.length > 0;
  const label = verified ? `확인된 아티스트: ${items.join(', ')}` : '아직 확인된 항목이 없는 아티스트';
  return (
    <span
      className={`verified-badge${verified ? ' is-verified' : ''}`}
      role="img"
      aria-label={label}
      tabIndex={0}
    >
      <svg
        width={size}
        height={size}
        viewBox="0 0 24 24"
        aria-hidden="true"
      >
        <path
          className="verified-badge-seal"
          d={SEAL}
        />
        <path
          className="verified-badge-check"
          d="M7.6 12.3l2.9 2.9 5.9-6"
          fill="none"
          strokeWidth="2.2"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
      {/* 툴팁 (화면 낭독기는 위 aria-label로 같은 내용을 읽음) */}
      <span
        className="verified-badge-tip"
        aria-hidden="true"
      >
        <strong>{verified ? '확인된 아티스트' : '아직 확인된 항목이 없어요'}</strong>
        {verified && <span>{items.join(' · ')}</span>}
      </span>
    </span>
  );
}
