import { useState } from 'react';
import Icon from '../../../components/common/icon.jsx';
import {
  CONDITION_LABELS,
  REFERENCE_SAMPLE,
  RHYTHM_PATTERNS,
  bpmPosition,
  compareBeats,
  compareTempo,
  compareWantedTempo,
} from '../song-results.js';

// 추천 이유 슬라이드 4장 (NSU-111): 음악 유사도 → 템포 → 리듬 → 필수 조건 (계약의 reasons[].item 순서)
// ‹ › 버튼이나 ←/→ 키로 넘깁니다. 순위는 음악 유사도로만 정하고, 템포·리듬은 참고 정보입니다.
// basis: 무엇과 비교하는지
//   { kind: 'song', name }  참고 곡으로 찾기 (올린 참고 곡과 비교)
//   { kind: 'event', tempo, rhythm }  행사 설명으로 찾기 (행사 조건에서 고른 템포·리듬과 비교)
const SLIDES = ['음악 유사도', '템포', '리듬', '필수 조건'];

// 1장: 음악 유사도
function SimilaritySlide({ item, basis }) {
  const bySong = basis.kind === 'song';
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
        <p>
          {bySong
            ? '올린 참고 곡과 이 곡이 얼마나 비슷한지 AI가 비교한 값이에요. 이 값으로 순위를 정해요.'
            : '등록한 행사 설명과 이 곡이 얼마나 어울리는지 AI가 비교한 값이에요. 이 값으로 순위를 정해요.'}
        </p>
      </div>
      <ol
        className="reason-flow"
        aria-label="비교 순서"
      >
        <li>
          <small>{bySong ? '참고 곡' : '행사'}</small>
          {bySong ? basis.name : '행사 설명'}
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
      <p className="reason-hint">
        {bySong ? '오른쪽 CD에서 참고 곡과 이 곡을 바꿔 들어 보세요.' : '오른쪽 CD에서 이 곡을 들어 보세요.'}
      </p>
    </div>
  );
}

// 템포 막대 (marks: [{ label, bpm, kind: 'reference' | 'song' }], band: 원하는 구간)
function TempoScale({ marks, band }) {
  return (
    <>
      <div
        className="tempo-scale"
        aria-hidden="true"
      >
        {band && (
          <span
            className="tempo-band"
            style={{ '--from': `${bpmPosition(band.from)}%`, '--to': `${bpmPosition(band.to)}%` }}
          />
        )}
        {marks.map((m) => (
          <span
            key={m.kind}
            className={`tempo-mark is-${m.kind}`}
            style={{ '--pos': `${bpmPosition(m.bpm)}%` }}
          >
            {m.label}
          </span>
        ))}
      </div>
      <div
        className="tempo-ends"
        aria-hidden="true"
      >
        <span>느림 60</span>
        <span>빠름 160</span>
      </div>
    </>
  );
}

// 2장: 템포 (참고 곡 BPM 또는 원하는 템포 구간과 비교)
function TempoSlide({ item, basis }) {
  const { bpm } = item.music;
  const hint = 'BPM은 1분에 박자가 몇 번 나오는지예요. 템포는 순위에 쓰지 않는 참고 정보예요.';
  if (basis.kind === 'song') {
    const ref = REFERENCE_SAMPLE.bpm;
    const tempo = compareTempo(ref, bpm);
    return (
      <div className="reason-body is-wide">
        <h4>{tempo.title}</h4>
        <p>
          참고 곡 <b>{ref}</b> → 이 곡 <b>{bpm}</b> · 차이 {tempo.diff}
        </p>
        <TempoScale
          marks={[
            { label: '참고 곡', bpm: ref, kind: 'reference' },
            { label: '이 곡', bpm, kind: 'song' },
          ]}
        />
        <p className="reason-hint">{hint}</p>
      </div>
    );
  }
  const tempo = compareWantedTempo(basis.tempo, bpm);
  return (
    <div className="reason-body is-wide">
      <h4>{tempo ? tempo.title : '원하는 템포를 고르지 않았어요'}</h4>
      <p>
        {tempo && (
          <>
            원하는 템포 <b>{tempo.wanted.label}</b> ({tempo.wanted.from}~{tempo.wanted.to}) ·{' '}
          </>
        )}
        이 곡 <b>{bpm}</b>
      </p>
      <TempoScale
        marks={[{ label: '이 곡', bpm, kind: 'song' }]}
        band={tempo?.wanted}
      />
      <p className="reason-hint">{hint}</p>
    </div>
  );
}

// 3장: 리듬 (한 마디 16칸에서 박이 같은 자리에 오는지)
function RhythmSlide({ item, basis }) {
  const bySong = basis.kind === 'song';
  const pattern = bySong ? REFERENCE_SAMPLE.beats : RHYTHM_PATTERNS[basis.rhythm];
  // 원하는 리듬을 고르지 않았으면 이 곡의 박자만 보여 줌
  const { total, same, cells } = compareBeats(pattern || item.music.beats.map(() => 0), item.music.beats);
  const name = bySong ? '참고 곡' : '원하는 리듬';
  const row = (label, who) => (
    <div className="beat-row">
      <span>{label}</span>
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
      <h4>{pattern ? `박자가 ${total}번 중 ${same}번 겹쳐요` : '원하는 리듬을 고르지 않았어요'}</h4>
      <p>
        {pattern
          ? `${bySong ? '참고 곡' : `원하는 리듬(${basis.rhythm})`}에서 박이 나오는 자리에 이 곡도 박이 나오는지 한 마디로 비교했어요.`
          : '이 곡의 한 마디 박자만 보여 줘요.'}
      </p>
      <div className="beat-compare">
        {pattern && row(name, 'reference')}
        {row('이 곡', 'song')}
      </div>
      <p className="reason-hint">
        {pattern ? '진하게 표시한 칸이 함께 박이 나오는 자리예요. ' : ''}리듬도 참고 정보예요.
      </p>
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

// item: 결과 한 줄, basis: 비교 기준 (위 설명)
export default function ReasonSlider({ item, basis }) {
  const [index, setIndex] = useState(0);
  const last = SLIDES.length - 1;
  const go = (next) => setIndex(Math.min(last, Math.max(0, next)));

  function keyDown(e) {
    if (e.key === 'ArrowRight') go(index + 1);
    else if (e.key === 'ArrowLeft') go(index - 1);
    else return;
    e.preventDefault();
  }

  const target = basis.kind === 'song' ? '올린 참고 곡' : '행사 설명';
  const reason =
    item.rank === 1
      ? `${target}과 음악적으로 가장 가까운 곡이에요.`
      : `${target}과 ${item.rank}번째로 가까운 곡이에요.`;

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
              basis={basis}
            />
          )}
          {index === 1 && (
            <TempoSlide
              item={item}
              basis={basis}
            />
          )}
          {index === 2 && (
            <RhythmSlide
              item={item}
              basis={basis}
            />
          )}
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
