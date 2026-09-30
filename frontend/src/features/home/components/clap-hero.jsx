import { useState } from 'react';
import './clap-hero.css';

// 1. 임의 데이터에 항목별 점수와 색상(이미지에 맞춘 컬러)을 추가했습니다.
const examples = [
  {
    name: '에너지 넘치는 축제',
    score: 89,
    artist: '아티스트 A · 힙합 데모',
    reference: '행사 관계자 · 축제 참고곡',
    reason: '두 음악의 분위기가 가까운 경우를 표현한 예시예요.',
    details: [
      { name: '분위기', score: 92, color: '#5A67D8' },    // 파란/보라색
      { name: '템포', score: 85, color: '#48BB78' }, // 초록색
      { name: '장르', score: 90, color: '#ec4b4b' }, // 빨간색
    ]
  },
  {
    name: '차분한 저녁 공연',
    score: 76,
    artist: '아티스트 B · 어쿠스틱 데모',
    reference: '행사 관계자 · 저녁 공연 참고곡',
    reason: '비슷한 분위기의 후보를 비교하는 화면 예시예요.',
    details: [
      { name: '분위기', score: 70, color: '#5A67D8' },
      { name: '템포', score: 82, color: '#48BB78' },
      { name: '장르', score: 78, color: '#F56565' }
    ]
  },
];

export default function ClapHero({ navigate }) {
  const [index, setIndex] = useState(0);
  const [activeSegment, setActiveSegment] = useState(null);
  const sample = examples[index];
  const activeDetail = activeSegment === null ? null : sample.details[activeSegment];

  // 2. 다중 색상 원 그래프를 그리기 위한 누적 위치 계산 로직
  const ringSegments = [];
  const totalScore = sample.details.reduce((sum, detail) => sum + detail.score, 0);
  let cumulativeOffset = 0;
  sample.details.forEach((detail) => {
    const segmentValue = totalScore > 0 ? (detail.score / totalScore) * 100 : 0; // 각 항목의 비중 계산
    ringSegments.push({
      ...detail,
      segmentValue,
      offset: cumulativeOffset
    });
    cumulativeOffset += segmentValue;
  });

  return (
    <section
      className="clap-showcase"
      aria-labelledby="clap-heading"
    >
      <div className="clap-showcase-heading">
        <p className="eyebrow">FIND YOUR SOUND · CLAP</p>
        <h2 id="clap-heading">
          CLAP을 통해 원하는
          <br />
          <span>분위기와 음원을 찾게 해줄게!</span>
        </h2>
        <p>
          아티스트가 올린 음원과 행사 관계자의 참고 음악을 비교해,
          <br />내 무대와 어울리는 음악을 찾는 기능을 준비하고 있어요.
        </p>
      </div>
      <div className="clap-showcase-grid">
        <div className="clap-stage-panel">
          <div className="clap-stage-label">
            <span>두 음악을 연결하는 CLAP</span>
            <span>LIGHT PREVIEW</span>
          </div>
          <div
            className="clap-stage"
            role="img"
            aria-label="헤드셋과 CLAP 글자로 음악 비교를 표현한 모습"
          >
            <div className="clap-static">
              <span aria-hidden="true">🎧</span>
              <strong>CLAP</strong>
              <small>음악과 음악을 연결해요</small>
            </div>
          </div>
          <div className="clap-tracks">
            <div>
              <span>ARTIST AUDIO</span>
              <strong>{sample.artist}</strong>
              <i aria-hidden="true">▂ ▅ ▃ ▇ ▄ ▆ ▂ ▅ ▇ ▃</i>
            </div>
            <b aria-hidden="true">↔</b>
            <div>
              <span>REFERENCE AUDIO</span>
              <strong>{sample.reference}</strong>
              <i aria-hidden="true">▃ ▆ ▂ ▅ ▇ ▄ ▃ ▆ ▅ ▂</i>
            </div>
          </div>
        </div>
        <div className="clap-result-panel">
          <div className="clap-result-title">
            <h3>음악 비교 결과</h3>
            <span>예시 결과</span>
          </div>
          <div
            className="clap-example-tabs"
            aria-label="예시 음악 선택"
          >
            {examples.map((example, i) => (
              <button
                key={example.name}
                aria-pressed={index === i}
                onClick={() => {
                  setIndex(i);
                  setActiveSegment(null);
                }}
              >
                {example.name}
              </button>
            ))}
          </div>

          {/* 3. 변경된 다중 색상 링 그래프 영역 */}
          <div
            className={`clap-ring ${activeSegment !== null ? 'has-active' : ''}`}
            style={{ '--active-ring-color': activeDetail?.color || 'var(--blue)' }}
          >
            <svg
              viewBox="0 0 200 200"
              aria-label={`항목별 음악 유사도 그래프. 종합 ${sample.score}점`}
              style={{ transform: 'rotate(-90deg)' }} // 12시 방향부터 시작하도록 회전
            >
              {/** 배경 트랙 (회색) */}
              <circle
                className="clap-ring-track"
                cx="100"
                cy="100"
                r="82"
                fill="none"
              />
              
              {/* 세부 항목별 컬러 조각 렌더링 */}
              {ringSegments.map((segment, idx) => (
                <circle
                  key={idx}
                  className={`clap-ring-value ${activeSegment === idx ? 'is-active' : ''}`}
                  cx="100"
                  cy="100"
                  r="82"
                  fill="none"
                  pathLength="100"
                  strokeDasharray={`${segment.segmentValue} 100`}
                  strokeDashoffset={-segment.offset}
                  tabIndex="0"
                  role="button"
                  aria-label={`${segment.name} ${segment.score}점`}
                  onPointerEnter={() => setActiveSegment(idx)}
                  onPointerLeave={() => setActiveSegment(null)}
                  onFocus={() => setActiveSegment(idx)}
                  onBlur={() => setActiveSegment(null)}
                  onClick={() => setActiveSegment(activeSegment === idx ? null : idx)}
                  style={{
                    stroke: segment.color,
                    '--segment-color': segment.color,
                  }}
                />
              ))}
            </svg>
            
            <div className="clap-ring-label" aria-live="polite">
              <span>{activeDetail ? activeDetail.name : '종합 유사도'}</span>
              <strong>
                {activeDetail ? activeDetail.score : sample.score}
                <small>/100</small>
              </strong>
              <em>{activeDetail ? '항목 점수' : '예시 종합 점수'}</em>
            </div>
          </div>

          <p className="clap-ring-hint">그래프 조각에 마우스를 올리거나 키보드로 선택해 보세요.</p>

          {/* 4. 항목별 점수 뱃지 (이미지 디자인 반영) */}
          <div className="clap-score-badges">
            {sample.details.map((detail, idx) => (
              <button
                key={detail.name}
                className={activeSegment === idx ? 'is-active' : ''}
                onPointerEnter={() => setActiveSegment(idx)}
                onPointerLeave={() => setActiveSegment(null)}
                onFocus={() => setActiveSegment(idx)}
                onBlur={() => setActiveSegment(null)}
                onClick={() => setActiveSegment(activeSegment === idx ? null : idx)}
              >
                <span style={{ backgroundColor: detail.color }} aria-hidden="true" />
                {detail.name} <strong>{detail.score}</strong>
              </button>
            ))}
          </div>

          <p
            className="clap-example-reason"
            aria-live="polite"
          >
            {sample.reason}
          </p>
          <ol className="clap-explanation">
            <li>두 음원의 음악 특징을 표현</li>
            <li>특징 사이의 유사도를 비교</li>
            <li>행사 조건과 함께 후보 확인</li>
          </ol>
          <p className="clap-example-note">
            설명을 위한 가상 점수이며 실제 CLAP 분석값이나 매칭 확률이 아닙니다. 점수 변환 기준은 연동 시 확정할 예정.
          </p>
          <button
            className="primary full"
            onClick={() => navigate('match')}
          >
            현재 조건 기반 매칭 체험 →
          </button>
        </div>
      </div>
    </section>
  );
}
