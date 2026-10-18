import { useEffect, useRef } from 'react';
import Icon from '../../../components/common/icon.jsx';
import { MATCHING_SAMPLE as SAMPLE, sameBeatCount, tempoPosition } from '../matching-sample.js';

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
    title: '리듬이 얼마나 비슷할까요?',
    lead: '한 마디(16박)를 나란히 놓고, 강한 박과 약한 박이 같은 자리에 오는지 비교해요.',
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

// 리듬: 행사 리듬과 곡 리듬 16칸을 위아래로 놓고 같은 자리를 표시
function RhythmBody() {
  const { request, song } = SAMPLE.beats;
  const same = sameBeatCount(SAMPLE.beats);
  return (
    <>
      <div className="stage-detail-beats">
        {[
          ['행사에 어울리는 리듬', request],
          ['이 곡의 리듬', song],
        ].map(([label, row]) => (
          <div
            key={label}
            className="stage-detail-beat-row"
          >
            <span>{label}</span>
            <div aria-hidden="true">
              {row.map((on, index) => (
                <i
                  key={index}
                  className={`${on ? 'is-on' : ''}${on === (row === song ? request : song)[index] ? '' : ' is-diff'}`}
                />
              ))}
            </div>
          </div>
        ))}
        <div
          className="stage-detail-bars"
          aria-hidden="true"
        >
          <span>1</span>
          <span>2</span>
          <span>3</span>
          <span>4</span>
        </div>
        <p className="stage-detail-legend">
          <span className="is-same">같은 자리의 박</span>
          <span className="is-diff">다른 자리의 박</span>
        </p>
      </div>
      <dl className="stage-detail-facts">
        <div>
          <dt>리듬 (예시)</dt>
          <dd>{SAMPLE.rhythmName}</dd>
        </div>
        <div>
          <dt>같은 자리의 박</dt>
          <dd>
            {same}/{song.length}
          </dd>
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
