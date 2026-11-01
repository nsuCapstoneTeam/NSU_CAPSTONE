import { useState } from 'react';
import Icon from '../../../components/common/icon.jsx';
import BeatGraph from './beat-graph.jsx';
import {
  CONDITION_LABELS,
  bpmPosition,
  compareWantedTempo,
  conditionDetail,
  formatDuration,
} from '../song-results.js';

// 추천 이유 슬라이드 4장 (NSU-111·112): 음악 유사도 → 템포 → 리듬 → 필수 조건 (계약의 reasons[].item 순서)
// Figma '추천 이유 슬라이드' 시안. 장마다 아래에 '추천 이유' 한 줄이 붙습니다.
// ‹ › 버튼이나 ←/→ 키로 넘깁니다. 순위는 음악 유사도로만 정하고, 템포·리듬은 참고 정보입니다.
// basis: 무엇과 비교하는지 { tempo, event }
//   tempo: 행사 조건에서 고른 템포 (템포 장에서 이 곡 BPM과 비교, 리듬 장은 이 곡 박자만)
//   event: 추천을 받을 때의 행사 조건 (음악 유사도 장의 행사 이름, 필수 조건 둘째 줄에 씀)
// player: useSongPlayer 결과 (리듬 그래프의 재생 위치 선과 '박자만 듣기')
const SLIDES = ['음악 유사도', '템포', '리듬', '필수 조건'];

// 리듬 그래프에 보여 줄 마디 수 (재생 위치 선 계산에도 씀)
const BARS = 2;

// 1장: 음악 유사도
function SimilaritySlide({ item, basis }) {
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
        <p>등록한 행사 설명과 이 곡이 얼마나 어울리는지 AI가 비교한 값이에요. 이 값으로 순위를 정해요.</p>
      </div>
      <ol
        className="reason-flow"
        aria-label="비교 순서"
      >
        <li>
          <small>행사 설명</small>
          {basis.event?.title || '행사 설명'}
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
      <p className="reason-hint">오른쪽 CD에서 이 곡을 들어 보세요.</p>
    </div>
  );
}

// 2장: 템포. 이 곡(점)과 고른 템포 구간(점선 띠), 아래에 값 세 칸
function TempoSlide({ item, basis }) {
  const { bpm } = item.music;
  const tempo = compareWantedTempo(basis.tempo, bpm);
  return (
    <div className="reason-body is-wide">
      <h4 className="reason-headline is-big">BPM {bpm}</h4>
      <div
        className="tempo-track"
        aria-hidden="true"
      >
        {tempo && (
          <span
            className="tempo-band"
            style={{ '--from': `${bpmPosition(tempo.wanted.from)}%`, '--to': `${bpmPosition(tempo.wanted.to)}%` }}
          />
        )}
        <span
          className="tempo-dot is-plain"
          style={{ '--pos': `${bpmPosition(bpm)}%` }}
        />
      </div>
      <div
        className="tempo-ticks"
        aria-hidden="true"
      >
        <span>느림</span>
        <span>보통</span>
        <span>빠름</span>
      </div>
      <dl className="reason-facts">
        <div>
          <dt>이 곡 BPM</dt>
          <dd>{bpm}</dd>
        </div>
        <div>
          <dt>원하는 템포</dt>
          <dd>{tempo ? `${tempo.wanted.label} (${tempo.wanted.from}~${tempo.wanted.to})` : '무관'}</dd>
        </div>
        <div>
          <dt>점수</dt>
          <dd>없음, 참고 정보</dd>
        </div>
      </dl>
    </div>
  );
}

// 3장: 리듬. 이 곡의 박자를 선 하나로 (리듬은 순위에 쓰지 않는 참고 정보)
function RhythmSlide({ item, player }) {
  const { beats, bpm, seconds } = item.music;
  // '박자만 듣기'에 쓰는 재생 정보 (서버 연결 전이라 박자 소리로 재생)
  const beatTracks = [
    { key: `beats-song-${item.music.id}`, label: '이 곡 박자만 듣기', duration: seconds, bpm, beats },
  ];
  // 재생 위치 선: 이 곡이나 박자만 듣기가 재생 중일 때, 그 BPM으로 두 마디 안에서 움직임
  const playing = [{ key: item.music.id, bpm }, ...beatTracks].find((t) => player?.key === t.key);
  const active = Boolean(playing);
  const loop = (60 / (playing?.bpm || bpm) / 4) * 16 * BARS || 1;
  const playhead = active ? (player.position % loop) / loop : null;

  const beatCount = beats.filter(Boolean).length;

  return (
    <div className="reason-body is-wide">
      <p className="reason-headline is-right">한 마디에 박이 {beatCount}번 나와요</p>
      <BeatGraph
        traces={[{ label: '이 곡', bpm, beats, kind: 'song' }]}
        playhead={playhead}
        bars={BARS}
      />
      <div className="beat-graph-actions">
        {/* 시각 정보라 화면 낭독기에서는 숨김 (슬라이드 영역이 aria-live라 매초 읽히지 않게) */}
        {active && (
          <span
            className="beat-graph-time"
            aria-hidden="true"
          >
            {formatDuration(player.position)}
          </span>
        )}
        {player &&
          beatTracks.map((t) => {
            const on = player.key === t.key && player.playing;
            return (
              <button
                key={t.key}
                type="button"
                className={`beat-listen is-song${on ? ' is-on' : ''}`}
                aria-pressed={on}
                onClick={() => player.toggle(t)}
              >
                <Icon
                  name={on ? 'pause' : 'play'}
                  size={13}
                />
                {t.label}
              </button>
            );
          })}
      </div>
    </div>
  );
}

// 4장: 통과한 필수 조건 (둘째 줄은 행사 조건으로 채움)
function ConditionSlide({ item, basis }) {
  return (
    <div className="reason-body is-wide">
      <p>추천 목록의 곡은 모두 아래 조건을 통과했어요.</p>
      <ul className="reason-conditions">
        {item.passedConditions.map((c) => (
          <li key={c}>
            <Icon
              name="check"
              size={15}
            />
            <span>
              <strong>{CONDITION_LABELS[c]}</strong>
              {conditionDetail(c, basis.event) && <small>{conditionDetail(c, basis.event)}</small>}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

// 장마다 아래에 붙는 '추천 이유' 한 줄
function slideReason(index, item, basis) {
  const { bpm } = item.music;
  if (index === 0) {
    return item.rank === 1
      ? '요청한 분위기와 음악적으로 가장 가까운 곡이에요.'
      : `요청한 분위기와 ${item.rank}번째로 가까운 곡이에요.`;
  }
  if (index === 1) {
    const tempo = compareWantedTempo(basis.tempo, bpm);
    return tempo ? `이 곡은 ${tempo.title}.` : '원하는 템포를 고르지 않아 이 곡의 BPM만 보여 줘요.';
  }
  if (index === 2) return '이 곡의 박자예요. 리듬은 순위에 쓰지 않는 참고 정보예요.';
  return '일정·지역·예산 조건을 모두 통과했어요.';
}

// item: 결과 한 줄, basis: 비교 기준 (위 설명), player: 재생기
export default function ReasonSlider({ item, basis, player }) {
  const [index, setIndex] = useState(0);
  const last = SLIDES.length - 1;
  const go = (next) => setIndex(Math.min(last, Math.max(0, next)));

  function keyDown(e) {
    if (e.target !== e.currentTarget) return;
    if (e.key === 'ArrowRight') go(index + 1);
    else if (e.key === 'ArrowLeft') go(index - 1);
    else return;
    e.preventDefault();
  }

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
              player={player}
            />
          )}
          {index === 3 && (
            <ConditionSlide
              item={item}
              basis={basis}
            />
          )}
        </div>
        <p className="reason-why">
          <span>추천 이유</span>
          {slideReason(index, item, basis)}
        </p>
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
