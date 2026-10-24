import { useState } from 'react';
import Icon from '../../../components/common/icon.jsx';
import { CONDITION_LABELS, REFERENCE_SAMPLE, bpmPosition, compareBeats, compareTempo } from '../song-results.js';

// 추천 이유 슬라이드 4장 (NSU-111): 음악 유사도 → 템포 → 리듬 → 필수 조건 (계약의 reasons[].item 순서)
// ‹ › 버튼이나 ←/→ 키로 넘깁니다. 순위는 음악 유사도로만 정하고, 템포·리듬은 참고 정보입니다.
const SLIDES = ['음악 유사도', '템포', '리듬', '필수 조건'];

// 1장: 음악 유사도
function SimilaritySlide({ item, referenceName }) {
  return (
    <div className="reason-body is-wide">
      <div className="reason-top">
        <div className="reason-similarity">
          <span
            className="stage-ring reason-ring"
            style={{ '--value': item.musicSimilarity }}
            aria-hidden="true"
          />
          <strong>{item.musicSimilarity}</strong>
        </div>
        <p>올린 참고 곡과 이 곡이 얼마나 비슷한지 AI가 비교한 값이에요. 이 값으로 순위를 정해요.</p>
      </div>
      <ol
        className="reason-flow"
        aria-label="비교 순서"
      >
        <li>
          <small>참고 곡</small>
          {referenceName}
        </li>
        <li className="is-ai">
          <small>비교</small>
          AI
        </li>
        <li>
          <small>곡</small>
          {item.music.title}
        </li>
      </ol>
      <p className="reason-hint">오른쪽 CD에서 참고 곡과 이 곡을 바꿔 들어 보세요.</p>
    </div>
  );
}

// 2장: 템포 (참고 곡 BPM과 비교)
function TempoSlide({ item }) {
  const ref = REFERENCE_SAMPLE.bpm;
  const { bpm } = item.music;
  const tempo = compareTempo(ref, bpm);
  return (
    <div className="reason-body is-wide">
      <h4>{tempo.title}</h4>
      <p>
        참고 곡 <b>{ref}</b> → 이 곡 <b>{bpm}</b> · 차이 {tempo.diff}
      </p>
      <div
        className="tempo-scale"
        aria-hidden="true"
      >
        <span
          className="tempo-mark is-reference"
          style={{ '--pos': `${bpmPosition(ref)}%` }}
        >
          참고 곡
        </span>
        <span
          className="tempo-mark is-song"
          style={{ '--pos': `${bpmPosition(bpm)}%` }}
        >
          이 곡
        </span>
      </div>
      <div
        className="tempo-ends"
        aria-hidden="true"
      >
        <span>느림 60</span>
        <span>빠름 160</span>
      </div>
      <p className="reason-hint">BPM은 1분에 박자가 몇 번 나오는지예요. 템포는 순위에 쓰지 않는 참고 정보예요.</p>
    </div>
  );
}

// 3장: 리듬 (한 마디 16칸에서 박이 같은 자리에 오는지)
function RhythmSlide({ item }) {
  const { total, same, cells } = compareBeats(REFERENCE_SAMPLE.beats, item.music.beats);
  const row = (name, who) => (
    <div className="beat-row">
      <span>{name}</span>
      <ol aria-hidden="true">
        {cells.map((cell, i) => (
          <li
            key={i}
            className={`${cell === 'both' || cell === who ? 'is-on' : ''}${cell === 'both' ? ' is-same' : ''}`}
          />
        ))}
      </ol>
    </div>
  );
  return (
    <div className="reason-body is-wide">
      <h4>
        박자가 {total}번 중 {same}번 겹쳐요
      </h4>
      <p>참고 곡에서 박이 나오는 자리에 이 곡도 박이 나오는지 한 마디로 비교했어요.</p>
      <div className="beat-compare">
        {row('참고 곡', 'reference')}
        {row('이 곡', 'song')}
      </div>
      <p className="reason-hint">진하게 표시한 칸이 두 곡에서 함께 박이 나오는 자리예요. 리듬도 참고 정보예요.</p>
    </div>
  );
}

// 4장: 통과한 필수 조건
function ConditionSlide({ item }) {
  return (
    <div className="reason-body is-wide">
      <h4>필수 조건을 모두 통과했어요</h4>
      <ul className="reason-conditions">
        {item.passedConditions.map((c) => (
          <li key={c}>
            <Icon
              name="check"
              size={14}
            />
            {CONDITION_LABELS[c]}
          </li>
        ))}
      </ul>
      <p className="reason-hint">목록의 곡은 모두 일정·지역·예산 같은 필수 조건을 통과한 곡이에요.</p>
    </div>
  );
}

// item: 결과 한 줄, referenceName: 올린 참고 곡 파일 이름
export default function ReasonSlider({ item, referenceName }) {
  const [index, setIndex] = useState(0);
  const last = SLIDES.length - 1;
  const go = (next) => setIndex(Math.min(last, Math.max(0, next)));

  function keyDown(e) {
    if (e.key === 'ArrowRight') go(index + 1);
    else if (e.key === 'ArrowLeft') go(index - 1);
    else return;
    e.preventDefault();
  }

  const reason =
    item.rank === 1
      ? '올린 참고 곡과 음악적으로 가장 가까운 곡이에요.'
      : `올린 참고 곡과 ${item.rank}번째로 가까운 곡이에요.`;

  return (
    <section
      className="reason-slider"
      aria-roledescription="슬라이드"
      aria-label={`추천 이유, ${SLIDES.length}장 중 ${index + 1}장 ${SLIDES[index]}`}
      tabIndex={0}
      onKeyDown={keyDown}
    >
      <button
        type="button"
        className="reason-arrow is-prev"
        onClick={() => go(index - 1)}
        disabled={index === 0}
        aria-label="이전 추천 이유"
      >
        <Icon
          name="chevron-left"
          size={18}
        />
      </button>

      <div
        className="reason-slide"
        aria-live="polite"
      >
        <h3>{SLIDES[index]}</h3>
        <div className="reason-content">
          {index === 0 && (
            <SimilaritySlide
              item={item}
              referenceName={referenceName}
            />
          )}
          {index === 1 && <TempoSlide item={item} />}
          {index === 2 && <RhythmSlide item={item} />}
          {index === 3 && <ConditionSlide item={item} />}
        </div>
        {index === 0 && (
          <p className="reason-why">
            <span>추천 이유</span>
            {reason}
          </p>
        )}
      </div>

      <button
        type="button"
        className="reason-arrow is-next"
        onClick={() => go(index + 1)}
        disabled={index === last}
        aria-label="다음 추천 이유"
      >
        <Icon
          name="chevron-right"
          size={18}
        />
      </button>

      <div className="reason-foot">
        <span
          className="reason-dots"
          aria-hidden="true"
        >
          {SLIDES.map((name, i) => (
            <i
              key={name}
              className={i === index ? 'is-on' : ''}
            />
          ))}
        </span>
        <span>
          {index + 1} / {SLIDES.length}
        </span>
        {index < last && <span className="reason-next">다음: {SLIDES[index + 1]}</span>}
      </div>
    </section>
  );
}
