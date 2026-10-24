import { useEffect, useRef } from 'react';
import Icon from '../../../components/common/icon.jsx';
import BeatGraph from '../../matching/components/beat-graph.jsx';
import { MATCHING_SAMPLE as SAMPLE, beatCount, tempoPosition } from '../matching-sample.js';

// 상세 화면 제목과 설명 (원래 시안 문구)
const DETAIL_TEXT = {
  similarity: {
    title: '음악이 얼마나 가까울까요?',
    lead: '음악과 글을 함께 이해하는 AI CLAP이 저장된 행사 정보를 해석해, 아티스트의 곡과 얼마나 가까운지 계산해요.',
  },
  tempo: {
    title: '템포는 어떤가요?',
    lead: 'AI가 곡의 BPM을 재고, 행사 정보에서 원하는 템포를 해석해 비교해요. 템포는 순위에 쓰지 않는 참고 정보예요.',
  },
  rhythm: {
    title: '리듬은 어떤가요?',
    lead: '곡의 박이 한 마디(16칸) 안에서 어디에 오는지 보여 줘요. 행사 설명으로 찾을 때는 비교할 곡이 없어 이 곡의 박자만 보여 줘요.',
  },
};

// 음악 유사도: 행사 정보 → CLAP → 곡, 그리고 0~100 막대
function SimilarityBody() {
  return (
    <>
      <div className="stage-flow">
        <dl className="stage-flow-box">
          <dt>행사 정보 (서버에 저장된 값)</dt>
          {SAMPLE.event.map(([name, value]) => (
            <dd key={name}>
              <span>{name}</span> {value}
            </dd>
          ))}
        </dl>
        <span className="stage-flow-arrow">
          CLAP
          <Icon
            name="arrow"
            size={16}
          />
        </span>
        <dl className="stage-flow-box">
          <dt>아티스트의 곡</dt>
          <dd className="is-title">{SAMPLE.song.title}</dd>
          <dd>{SAMPLE.song.artist}</dd>
          <dd>{SAMPLE.song.clip}</dd>
        </dl>
      </div>
      <div className="stage-detail-meter">
        <div className="stage-detail-meter-head">
          <span>음악 유사도</span>
          <strong>{SAMPLE.similarity}</strong>
        </div>
        <div
          className="stage-detail-track"
          role="meter"
          aria-label="음악 유사도"
          aria-valuenow={SAMPLE.similarity}
          aria-valuemin={0}
          aria-valuemax={100}
        >
          <i style={{ '--value': SAMPLE.similarity }} />
        </div>
        <div className="stage-detail-scale">
          <span>0</span>
          <span>50</span>
          <span>100</span>
        </div>
        <p>순위는 이 값으로만 정해요. %를 붙이지 않는 점수예요.</p>
      </div>
    </>
  );
}

// 템포: 원하는 템포 구간 위에 이 곡 BPM 위치 표시 + 숫자 정리
function TempoBody() {
  const { wantedTempo } = SAMPLE;
  return (
    <>
      <div className="stage-detail-tempo">
        <div
          className="stage-tempo is-large"
          style={{
            '--pos': `${tempoPosition(SAMPLE.bpm)}%`,
            '--zone-from': `${tempoPosition(wantedTempo.from)}%`,
            '--zone-to': `${tempoPosition(wantedTempo.to)}%`,
          }}
        >
          <span className="stage-tempo-zone" />
          <span className="stage-tempo-thumb" />
        </div>
        <div className="stage-tempo-scale">
          <span>느림</span>
          <span>보통</span>
          <span>빠름</span>
        </div>
      </div>
      <dl className="stage-detail-facts">
        <div>
          <dt>이 곡 BPM (측정값)</dt>
          <dd>{SAMPLE.bpm}</dd>
        </div>
        <div>
          <dt>원하는 템포 (AI 해석)</dt>
          <dd>{wantedTempo.label}</dd>
        </div>
        <div>
          <dt>점수</dt>
          <dd className="is-muted">없음, 참고 정보</dd>
        </div>
      </dl>
    </>
  );
}

// 리듬: 이 곡의 박자를 심전도 모양 선으로 (추천 이유 슬라이드의 리듬 그래프와 같은 모양, NSU-113)
function RhythmBody() {
  return (
    <>
      <BeatGraph
        traces={[{ label: '이 곡', bpm: SAMPLE.bpm, beats: SAMPLE.beats, kind: 'song' }]}
        bars={1}
      />
      <dl className="stage-detail-facts">
        <div>
          <dt>한 마디의 박</dt>
          <dd>{beatCount(SAMPLE.beats)}번</dd>
        </div>
        <div>
          <dt>점수</dt>
          <dd className="is-muted">없음, 참고 정보</dd>
        </div>
      </dl>
    </>
  );
}

const BODIES = { similarity: SimilarityBody, tempo: TempoBody, rhythm: RhythmBody };

// 매칭 구간에서 카드나 그래프 조각을 눌렀을 때 대시보드 자리에 보이는 상세 화면.
// 열리면 제목으로 포커스를 옮기고, '추천 결과로 돌아가기'로 대시보드에 돌아갑니다.
export default function TrackMatchingDetail({ kind, onBack }) {
  const titleRef = useRef(null);
  const text = DETAIL_TEXT[kind];
  const Body = BODIES[kind];
  useEffect(() => {
    titleRef.current?.focus({ preventScroll: true });
  }, [kind]);
  return (
    <article
      className={`stage-glass stage-detail is-${kind}`}
      aria-labelledby="home-matching-detail-title"
    >
      <div className="stage-detail-head">
        <h3
          id="home-matching-detail-title"
          ref={titleRef}
          tabIndex={-1}
        >
          {text.title}
        </h3>
        <button
          className="stage-back"
          onClick={onBack}
        >
          추천 결과로 돌아가기
        </button>
      </div>
      <p className="stage-detail-lead">{text.lead}</p>
      <Body />
      <p className="stage-reason">
        <span>추천 이유</span>
        {SAMPLE.reasons[kind]}
      </p>
      <p className="stage-glass-note">
        예시 데이터예요.{kind === 'rhythm' && ' 리듬도 점수 없이 참고 정보로 보여 줘요.'}
      </p>
    </article>
  );
}
